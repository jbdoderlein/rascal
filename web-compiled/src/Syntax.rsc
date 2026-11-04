module Syntax

lexical Num = [0-9]+;
layout WS = [\ ]*;
start syntax Exp = Num | left Exp "+" Exp;
