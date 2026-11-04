module StoreParsers

import ParseTree;
import Syntax;

// Run at build time (on the full rascal.jar, from the bin folder): generating a parser needs the
// interpreter and javac, which the slim web runtime does not have. At runtime, use loadParsers.
int main() {
    storeParsers(#start[Exp], |cwd:///Exp.parsers|);
    return 0;
}
