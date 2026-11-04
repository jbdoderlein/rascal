module NestedClosureCapturesParam

// OPEN: generates invalid Java ("cannot find symbol getValue()", "IValue cannot be converted to ValueRef"):
// an inner closure captures a parameter of the closure around it
// (salix::Index::withIndex: `void(&T model) { index(..., () { view(model); }) }`)
int(int) adder() = int(int x) {
  int() inner = int() { return x + 1; };
  return inner();
};

int main() = adder()(41);
