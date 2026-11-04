module ReifiedTypeParamCapture

// FIXED (RascalValueFactory.reifiedType; interpreter and compiled code): reifying a type that
// contains a type parameter named `T` binds Rascal's own `&T` (the parameter of `type[&T]`) to a
// type containing `&T` again, a cyclic binding. vallang then recurses until it catches a
// StackOverflowError: ~800x slower, sometimes escaping; with `java -ea` an AssertionError
// "type bindings are not hygenic"; under CheerpJ random ClassCast/ArrayIndexOutOfBounds exceptions.
// `&U` instead of `&T` is fine.
str main() = "<#void(&T)> <#void(&U)>";
