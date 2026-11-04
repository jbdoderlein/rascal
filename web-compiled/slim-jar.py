#!/usr/bin/env python3
"""Shrinks the (87MB) shaded rascal.jar to the runtime that compiled Rascal programs need.

Keeps only the classes reachable (according to jdeps) from:
  - the compiled-code runtime (org.rascalmpl.runtime.**) and org.rascalmpl.shell.WebRunner,
  - every Java class bound by @javaClass in the standard library's .rsc files
    (unless --program-only: generated code references its Java bindings directly),
  - packages that instantiate their own classes by name (Caffeine, capsule, vallang),
  - the classes listed in *.config files (URI resolvers, vallang type kinds) and the META-INF/services providers
    (both are loaded reflectively, so jdeps cannot see them),
  - optionally, the classes of compiled programs (--program), which may call more Java code.
and only the resources the runtime reads (*.config, without the resolvers in DROPPED_RESOLVERS,
and META-INF/services). Type checker
summaries (.tpl), Rascal sources, docs and native libraries are all dropped.

Usage: slim-jar.py rascal.jar rascal-runtime.jar [--program demo.jar ... [--program-only]] [--icu-data]
"""
import argparse
import collections
import re
import subprocess
import zipfile

RUNTIME_ROOTS = ["org.rascalmpl.runtime.", "org.rascalmpl.shell.WebRunner"]
# packages that load their own classes by computed names, so jdeps cannot follow them
REFLECTIVE_PACKAGES = ["com.github.benmanes.caffeine.cache.", "io.usethesource.capsule.", "io.usethesource.vallang."]
RESOURCES = ["META-INF/MANIFEST.MF"]
# resolvers left out of resolvers.config: compressed streams (commons-compress, xz), http(s)
# (needs an epoll selector, which CheerpJ lacks) and mvn:// (maven settings parsing)
DROPPED_RESOLVERS = [
    "org.rascalmpl.uri.CompressedStreamResolver",
    "org.rascalmpl.uri.remote.HttpURIResolver",
    "org.rascalmpl.uri.remote.HttpsURIResolver",
    "org.rascalmpl.uri.file.MavenRepositoryURIResolver",
]
ICU_DATA = "com/ibm/icu/impl/data/"
# the ICU data that date formatting reads (other locales fall back to root), ~0.9MB instead of ~11MB
ICU_CORE_DATA = [ICU_DATA + "icudata/" + f for f in [
    "root.res", "en.res", "en_US.res", "pool.res", "supplementalData.res",
    "numberingSystems.res", "zoneinfo64.res", "uprops.icu"]]


def class_name(entry):
    """jar entry name -> class name, for the base version of multi-release entries"""
    entry = re.sub(r"^META-INF/versions/\d+/", "", entry)
    return entry[: -len(".class")].replace("/", ".")


def dependency_graph(jars):
    out = subprocess.run(
        ["jdeps", "--multi-release", "11", "-verbose:class", "-filter:none", "--ignore-missing-deps", *jars],
        check=True, capture_output=True, text=True).stdout
    graph = collections.defaultdict(set)
    for line in out.splitlines():
        m = re.match(r"\s+(\S+)\s+->\s+(\S+)", line)
        if m:
            graph[m.group(1)].add(m.group(2))
    return graph


def roots(fat, programs, program_only, graph):
    names = fat.namelist()
    result = {c for c in graph if any(c.startswith(r) for r in RUNTIME_ROOTS + REFLECTIVE_PACKAGES)}
    result |= set(RUNTIME_ROOTS[1:])

    for entry in names:
        if entry.endswith(".rsc") and not program_only:
            src = fat.read(entry).decode("utf-8", "replace")
            result |= {c.strip() for c in re.findall(r"@javaClass\{([^}]+)\}", src)}
        elif entry.startswith("META-INF/services/") and not entry.endswith("/"):
            service = entry[len("META-INF/services/"):]
            providers = fat.read(entry).decode("utf-8", "replace").splitlines()
            # only keep providers of services that the closure can actually ask for
            result |= {f"@service:{service}:{p.split('#')[0].strip()}" for p in providers if p.split("#")[0].strip()}

    # resolvers.config and vallang's types.config list classes that are instantiated by name
    for entry in names:
        if entry.endswith(".config"):
            result |= set(config_classes(fat, entry))

    for program in programs:
        with zipfile.ZipFile(program) as p:
            result |= {class_name(e) for e in p.namelist() if e.endswith(".class")}
    return result


def config_classes(fat, entry):
    lines = [l.strip() for l in fat.read(entry).decode().splitlines()]
    return [l for l in lines if l and not l.startswith("#") and l not in DROPPED_RESOLVERS]


def closure(start, graph):
    services = collections.defaultdict(set)
    seen, todo = set(), []
    for r in start:
        if r.startswith("@service:"):
            _, service, provider = r.split(":", 2)
            services[service].add(provider)
        else:
            todo.append(r)
    while todo:
        c = todo.pop()
        if c in seen:
            continue
        seen.add(c)
        todo.extend(graph.get(c, ()))
        # a service interface in the closure brings its providers along
        todo.extend(services.pop(c, ()))
    return seen, set(services)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("fat_jar")
    parser.add_argument("out_jar")
    parser.add_argument("--program", action="append", default=[], help="compiled program jar, adds roots")
    parser.add_argument("--program-only", action="store_true",
                        help="only keep the library code that the --program jars use, instead of all of it")
    parser.add_argument("--icu-data", action="store_true",
                        help="keep all of ICU's locale data (~11MB), instead of root and English only")
    args = parser.parse_args()

    graph = dependency_graph([args.fat_jar, *args.program])
    with zipfile.ZipFile(args.fat_jar) as fat:
        keep, unused_services = closure(roots(fat, args.program, args.program_only, graph), graph)

        kept = 0
        with zipfile.ZipFile(args.out_jar, "w", zipfile.ZIP_DEFLATED) as out:
            for info in fat.infolist():
                name = info.filename
                if name.endswith("/"):
                    continue
                if name.endswith(".config"):
                    out.writestr(info, "\n".join(config_classes(fat, name)) + "\n")
                    kept += 1
                    continue
                if name.endswith(".class"):
                    wanted = class_name(name) in keep
                elif name.startswith("META-INF/services/"):
                    wanted = name[len("META-INF/services/"):] not in unused_services
                else:
                    wanted = (name in RESOURCES or name in ICU_CORE_DATA
                              or (args.icu_data and name.startswith(ICU_DATA)))
                if wanted:
                    out.writestr(info, fat.read(name))
                    kept += 1
    print(f"{args.out_jar}: kept {kept} entries ({len(keep)} reachable classes)")


if __name__ == "__main__":
    main()
