# Compiled Rascal in the browser (experimental PoC)

Rascal modules in `src/` are compiled to Java by the Rascal compiler, then to bytecode, and run in
the browser with [CheerpJ](https://cheerpj.com/) on top of a slimmed-down Rascal runtime.

```sh
mvn package -DskipTests                       # target/rascal-*.jar (the full, 87MB, shaded jar)
web-compiled/compile.sh Demo Features StoreParsers
                                              # .rsc -> gen/java -> bin -> demo.jar, stores parsers, copies rascal.jar here
python3 web-compiled/slim-jar.py web-compiled/rascal.jar web-compiled/rascal-runtime.jar
                                              # ~11MB runtime for any compiled program
npx http-server web-compiled -p 8080 -c-1     # CheerpJ needs a server with Range support (python's http.server has none)
# open http://localhost:8080/
```

On a JVM: `java -cp web-compiled/demo.jar:web-compiled/rascal-runtime.jar org.rascalmpl.shell.WebRunner 'rascal.$Demo'`

## The runtime jar

`slim-jar.py` keeps what `jdeps` finds reachable from the compiled-code runtime, the Java classes
of the standard library (`@javaClass`), and the classes that are instantiated by name
(`*.config` files, `META-INF/services`, Caffeine/capsule/vallang). It drops the type checker's
`.tpl` files (~35MB), Rascal sources, docs, native libraries, most of ICU's locale data, and the
compressed/http/mvn URI resolvers (see `DROPPED_RESOLVERS`).

| jar                                    | size   |
|----------------------------------------|--------|
| `rascal.jar` (shaded, everything)      | 87 MB  |
| `rascal-runtime.jar` (any program)     | 11 MB  |
| `--program demo.jar --program-only`    | 8.5 MB |

`--program-only` only keeps the library code that the given programs use; that is safe because
generated code references its Java library classes directly.

## Parsing

Generating a parser needs the interpreter and `javac`, neither of which is in the slim runtime (nor
in CheerpJ). So parsers are generated at build time and loaded at run time:

- `StoreParsers.rsc` calls `storeParsers(#start[Exp], |cwd:///Exp.parsers|)`; `compile.sh` runs it
  on the full jar, in `bin/`, so the parser ends up in `demo.jar`.
- `Features.rsc` calls `loadParsers(|resource:///Exp.parsers|)`; the `resource` scheme reads from
  the class path.

## Notes

- Imported library modules (`IO`, `List`, ...) are compiled from source along with the program,
  because `rascal.jar` only ships their type summaries (`.tpl`), not compiled Java classes.
- The compiler's `main` cannot be used yet (`compile(list[loc], ...)` is a stub), so `compile.sh`
  calls `compile([...], ...)` from the interpreter.
