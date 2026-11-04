module Features

import IO;
import String;
import Set;
import Map;
import Relation;
import DateTime;
import Exception;
import ParseTree;
import Syntax;

int evalExp((Exp) `<Num n>`) = toInt("<n>");
int evalExp((Exp) `<Exp l> + <Exp r>`) = evalExp(l) + evalExp(r);

str greet(str name, str greeting = "Hello") = "<greeting>, <name>!";

int main() {
    println(greet("web", greeting = "Hi"));
    println("words: <sort([w | /<w:[a-z]+>/ := "the quick brown fox"])>");
    s = {1, 2, 3} + {3, 4};
    println("set: <s>, power set size <size(power(s))>");
    m = ("a": 1, "b": 2);
    println("map inverted: <invertUnique(m)>");
    rel[str, str] parent = {<"a", "b">, <"b", "c">, <"c", "d">};
    println("ancestors of a: <(parent+)["a"]>");
    println("date: <printDate($2026-11-02$, "yyyy/MM/dd")>");
    try {
        [1, 2][5];
    }
    catch IndexOutOfBounds(i): println("caught out of bounds index <i>");
    // the parser was generated at build time by StoreParsers, and is a resource in the program's jar
    parser = loadParsers(|resource:///Exp.parsers|);
    t = parser(#start[Exp], "1 + 2 + 39", |unknown:///|);
    println("parsed and evaluated: <evalExp(t.top)>");
    return 0;
}
