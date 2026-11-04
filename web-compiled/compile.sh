#!/bin/sh
# Compiles modules from web-compiled/src (default: Demo) and the library modules they import to Java,
# then javac's the result into web-compiled/bin and packs it as web-compiled/demo.jar,
# next to a copy of rascal.jar, for index.html.
# Usage: [RASCAL_SRCS=/abs/src1:/abs/src2] web-compiled/compile.sh [Module ...]
#   (needs target/classes and the maven classpath of the rascal project;
#    RASCAL_SRCS adds source folders of other Rascal libraries, which are compiled along)
set -e
W=$(dirname "$(realpath "$0")")
R=$(dirname "$W")
CP_FILE="$W/.classpath.txt"
[ -f "$CP_FILE" ] || mvn -o -q -f "$R/pom.xml" dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
CP="$R/target/classes:$(cat "$CP_FILE")"
MODULES=$(printf '"%s",' "${@:-Demo}" | sed 's/,$//')
EXTRA_SRCS=$(echo "${RASCAL_SRCS:-}" | tr ':' '\n' | sed '/^$/d; s/.*/, |file:\/\/&|/' | tr -d '\n')

rm -rf "$W/gen" "$W/bin"

# 1. Rascal -> Java (the shell must run from the rascal repo root, so the compiler sources are on its search path)
(cd "$R" && java -Xss32m -cp "$CP" org.rascalmpl.shell.RascalShell <<RSC
import util::Reflective;
import lang::rascalcore::check::RascalConfig;
import lang::rascalcore::compile::Compile;
pcfg = pathConfig(projectRoot=|file://$W|, srcs=[|file://$W/src|, |file://$R/src/org/rascalmpl/library|$EXTRA_SRCS], bin=|file://$W/bin|, generatedSources=|file://$W/gen/java|, generatedResources=|file://$W/bin|, libs=[]);
msgs = compile([$MODULES], rascalCompilerConfig(pcfg)[verbose=true][logWrittenFiles=true]);
import IO;
for (m <- msgs) println(m);
RSC
)

# 2. Java -> bytecode, for Java 11 like rascal.jar itself
find "$W/gen/java" -name '*.java' > "$W/.sources.txt"
javac --release 11 -nowarn -encoding UTF-8 -cp "$CP" -d "$W/bin" @"$W/.sources.txt"

# 2b. Generate parsers ahead of time (needs the full rascal runtime), so the program can loadParsers them
if [ -f "$W/bin/rascal/\$StoreParsers.class" ]; then
  (cd "$W/bin" && java -cp "$W/bin:$CP" 'rascal.$StoreParsers')
fi

# 3. One small jar with the compiled program; rascal.jar provides the runtime
jar --create --file "$W/demo.jar" -C "$W/bin" .
echo "Built $W/demo.jar"

# 4. The page also needs rascal.jar (from `mvn package`) as the runtime
RASCAL_JAR=$(find "$R/target" -maxdepth 1 -name 'rascal-*.jar' ! -name '*-sources.jar' -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f2)
cp -f "$RASCAL_JAR" "$W/rascal.jar"
echo "Copied $RASCAL_JAR to $W/rascal.jar"
