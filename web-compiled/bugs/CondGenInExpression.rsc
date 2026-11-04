module CondGenInExpression

// OPEN: generates invalid Java ("')' expected"): the generator loop is emitted inside an expression
// (salix::Node::attrsOf has `(...) + ((attr("class", _) <- attrs) ? (...) : ())`)
int main() = 1 + ((2 <- [1, 2]) ? 10 : 20);
