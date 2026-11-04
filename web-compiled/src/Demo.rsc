module Demo

import IO;
import List;

int fib(int n) = n < 2 ? n : fib(n - 1) + fib(n - 2);

// all solutions of the n-queens problem, as lists of column indices
list[list[int]] queens(int n) {
    list[list[int]] place(int row) {
        if (row == 0) return [[]];
        return [ qs + [c] | qs <- place(row - 1), c <- [0..n], safe(qs, c) ];
    }
    return place(n);
}

bool safe(list[int] qs, int c) {
    r = size(qs);
    return !any(i <- index(qs), qs[i] == c || abs(qs[i] - c) == r - i);
}

int abs(int x) = x < 0 ? -x : x;

data Expr = lit(int v) | add(Expr l, Expr r) | mul(Expr l, Expr r);

int eval(lit(v)) = v;
int eval(add(l, r)) = eval(l) + eval(r);
int eval(mul(l, r)) = eval(l) * eval(r);

Expr simplify(Expr e) = innermost visit(e) {
    case add(lit(0), x) => x
    case add(x, lit(0)) => x
    case mul(lit(1), x) => x
    case mul(lit(0), _) => lit(0)
};

int main() {
    println("fib(25) = <fib(25)>");
    sols = queens(8);
    println("8-queens: <size(sols)> solutions, first one <sols[0]>");
    e = add(lit(0), mul(lit(1), add(lit(3), mul(lit(0), lit(7)))));
    println("simplify(<e>) = <simplify(e)>, value <eval(e)>");
    return 0;
}
