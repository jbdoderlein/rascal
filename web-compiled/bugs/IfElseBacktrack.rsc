module IfElseBacktrack

// OPEN, WRONG RESULT: an if-else whose condition can backtrack always runs the else branch
int h(list[int] xs) {
  int r = 0;
  if (int x <- xs, x > 2) r = x; else r = 100;
  return r;
}

str main() = "h([1,5]) = <h([1, 5])> (expected 5), h([1]) = <h([1])> (expected 100)";
