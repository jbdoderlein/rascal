#!/bin/sh
# Compiles and runs each compiler bug repro in this folder (or the ones given), one by one,
# and prints a one-line verdict per module. Details are in out/<Module>/log.txt.
# Usage: web-compiled/bugs/check.sh [Module ...]
# Needs target/classes and a packaged jar in target/ (mvn package) of the rascal project.
B=$(dirname "$(realpath "$0")")
W=$(dirname "$B")
R=$(dirname "$W")
CP_FILE="$W/.classpath.txt"
[ -f "$CP_FILE" ] || mvn -o -q -f "$R/pom.xml" dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
CP="$R/target/classes:$(cat "$CP_FILE")"

if [ $# -eq 0 ]; then
  set -- $(find "$B" -maxdepth 1 -name '*.rsc' -printf '%f\n' | sed 's/\.rsc$//' | sort)
fi

for M in "$@"; do
  O="$B/out/$M"
  rm -rf "$O"; mkdir -p "$O"
  LOG="$O/log.txt"

  (cd "$R" && java -Xss32m -cp "$CP" org.rascalmpl.shell.RascalShell > "$LOG" 2>&1 <<RSC
import util::Reflective;
import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::compile::Compile;
pcfg = pathConfig(projectRoot=|file://$O|, srcs=[|file://$B|, |file://$R/src/org/rascalmpl/library|], bin=|file://$O/bin|, generatedSources=|file://$O/gen|, generatedResources=|file://$O/bin|, libs=[]);
msgs = compile("$M", rascalCompilerConfig(pcfg));
import IO;
for (m <- msgs) println(m);
RSC
  )
  if grep -q "^	at \|): \"" "$LOG"; then
    echo "$M: COMPILER CRASH: $(grep -m1 '): "' "$LOG" | sed 's/.*): //' | cut -c1-150)"
    continue
  fi

  find "$O/gen" -name '*.java' > "$O/sources.txt"
  if ! javac --release 11 -nowarn -encoding UTF-8 -cp "$CP" -d "$O/bin" @"$O/sources.txt" >> "$LOG" 2>&1; then
    echo "$M: INVALID JAVA: $(grep -m1 'error:' "$LOG" | sed 's/.*error: //')"
    continue
  fi

  # -ea: some bugs only fail deterministically with assertions on (see ReifiedTypeParamCapture)
  if OUT=$(java -ea -cp "$O/bin:$CP" org.rascalmpl.shell.WebRunner "rascal.\$$M" 2>>"$LOG" | grep -v '^\[init'); then
    echo "$M: RUNS: $(echo "$OUT" | tr '\n' ' ' | cut -c1-150)"
  else
    echo "$M: RUNTIME FAILURE (see $LOG)"
  fi
done
