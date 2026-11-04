#!/bin/sh
# Builds the TodoMVC-in-the-browser PoC: Salix runs in the Rascal *interpreter*, inside the page.
#   app.jar     Salix's Rascal sources + the bridge modules in rascal/, under rascal-app/
#               (found via |resource:///rascal-app/|; not at the jar root, because rascal.jar has
#               stale standard library copies at its root)
#   rascal.jar  the full Rascal jar (the interpreter needs the standard library's sources)
#   salix.js, style.css  copied from salix-core
# Usage: web-salix/build.sh   (needs `mvn package` of the rascal project, and ../salix-core)
set -e
X=$(dirname "$(realpath "$0")")
R=$(dirname "$X")
SALIX=${SALIX:-$(dirname "$R")/salix-core}/src/main/rascal

rm -rf "$X/stage" && mkdir -p "$X/stage/rascal-app"
cp -r "$SALIX/salix" "$X/stage/rascal-app/"
cp "$X"/rascal/*.rsc "$X/stage/rascal-app/"
jar --create --file "$X/app.jar" -C "$X/stage" .
rm -rf "$X/stage"

cp "$SALIX/salix/salix.js" "$X/salix.js"
cp "$SALIX/salix/demo/todomvc/style.css" "$X/style.css"

RASCAL_JAR=$(find "$R/target" -maxdepth 1 -name 'rascal-*.jar' ! -name '*-sources.jar' -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f2)
cp -f "$RASCAL_JAR" "$X/rascal.jar"
# diag.jar: WebDiagnostics, which reports which suspect operations fail under CheerpJ
rm -rf "$X/diag/bin" && mkdir -p "$X/diag/bin"
javac --release 11 -nowarn -cp "$X/rascal.jar" -d "$X/diag/bin" "$X/diag/src/WebDiagnostics.java"
jar --create --file "$X/diag.jar" -C "$X/diag/bin" .
rm -rf "$X/diag/bin"

echo "Built $X/app.jar and $X/diag.jar; copied salix.js, style.css and $RASCAL_JAR"
