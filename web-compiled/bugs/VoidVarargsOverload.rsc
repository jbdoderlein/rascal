module VoidVarargsOverload

// OPEN: generates invalid Java ("does not override abstract method head(IValue)",
// "void cannot be converted to IValue"): a local void varargs function overloads List::head
// (salix::HTML defines `void head(value vals...)` and imports List)
import List;

void head(value vals...) { }

int main() { head(1, 2); return 0; }
