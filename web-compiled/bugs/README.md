# Rascal bugs found while compiling Salix and running it in the browser

Found on branch `experimental-web` while compiling Salix (`../salix-core`) to Java, and while running
it in the interpreter under CheerpJ (`../../web-salix`). Each `.rsc` file in this folder is a minimal
reproduction. `check.sh` compiles and runs each one in isolation (with `java -ea`) and prints one
verdict per module:

```sh
web-compiled/bugs/check.sh                 # all repros
web-compiled/bugs/check.sh IfElseBacktrack # one; full output in bugs/out/IfElseBacktrack/log.txt
```

The compiler runs interpreted from `src/org/rascalmpl/compiler`, so edits to the compiler's `.rsc`
files take effect on the next `check.sh` run, without `mvn`. Only `org.rascalmpl.shell.WebRunner`
has to be compiled (`mvn compile`). It runs a compiled module's `main()`.

| Repro | Symptom | Status |
|---|---|---|
| `JsonImport` | compiler crash: `No module name found for .../check/CollectExpression.rsc` | **fixed** on this branch |
| `EmptyAdtReify` | compiler crash: `NoSuchKey(aadt("Msg",[],dataSyntax()))` | **fixed** on this branch |
| `ReifiedTypeParamCapture` | runtime (interpreter and compiled): `#void(&T)` ~800x slow; `AssertionError` with `-ea`; random exceptions under CheerpJ | **fixed** on this branch |
| `IfElseBacktrack` | **wrong result**: `if` with a backtracking condition always runs `else` | open |
| `CondGenInExpression` | invalid Java: `')' expected` | open |
| `ConstructorShadowsFunction` | compiler crash: `mkVar: remove at ...` | open |
| `VoidVarargsOverload` | invalid Java: `does not override abstract method head(IValue)` | open |
| `NestedClosureCapturesParam` | invalid Java: `IInteger cannot be converted to ValueRef<IInteger>` | open |

To verify a fix: the repro moves from crash/invalid Java/wrong output to `RUNS` with the expected
output in the comment of the repro. The fixed repros were checked to fail without their fix.

---

## Fixed

### JsonImport: synthetic locations in the type model

Any module importing `lang::json::IO` crashed the compiler. `lang::json::IO` has keyword parameters
whose defaults are closures without a return type (`(type[value] _, str _) { throw ""; }`). For those,
the checker uses a synthetic tree, `private Type voidReturnType = (Type) \`void\`` in
`check/CollectExpression.rsc`, and records a fact for it. That tree's location is in the checker's
own source. `getFunctionsAndConstructors` in `compile/muRascal2Java/Resolvers.rsc` asks
`getRascalModuleName` for the location of every fact, and that throws for a location outside all
modules.

**Fix** (`Resolvers.rsc`): treat locations that are in no module as "not in this module":

```rascal
bool inThisModule(loc u) {
   try {
       return getRascalModuleName(u, pcfg) == mname;
   } catch str _: {
       return false;
   }
}
... | loc u <- tmodel.facts, inThisModule(u), ...
```

A deeper alternative: give the synthetic `void` tree the closure's own location in the checker.

### EmptyAdtReify: reifying an ADT without constructors

`#Msg` for `data Msg;` (no constructors in scope, which is how Salix declares `Msg`) crashed in
`collectNeededDefs` in `compile/Rascal2muRascal/TypeUtils.rsc`: `adt_constructors1[adt1]` has no entry
for it.

**Fix** (`TypeUtils.rsc`, in `my_definitions`):
`syntaxRole == dataSyntax() ? (adt_constructors1[adt1] ? {}) : ...`

### ReifiedTypeParamCapture: reifying a type that contains `&T` (runtime, not compiler)

```rascal
str main() = "<#void(&T)> <#void(&U)>";   // &T: cyclic; &U: fine
```

Not a compiler bug: it is in `values/RascalValueFactory.java`, shared by the interpreter and the
compiled runtime. `reifiedType(symbol, definitions)` builds the value `type(symbol, definitions)` by
instantiating the constructor type `type[&T]` with `&T -> <the reified type>`, where `&T` is
`RascalValueFactory.TypeParam`, a parameter named `T`. When the reified type contains a user type
parameter that is also named `T` (`void(&T)`; Salix has `render(&T model, void(&T) block)`), that
binding is cyclic: vallang's `ParameterType.instantiate` re-instantiates bindings, so it recurses
(`FunctionType.instantiate` <-> `Type.instantiateTuple`) until it **catches a `StackOverflowError`**
(on purpose: `assert false : "type bindings are not hygenic..."`). Effects:

- silently ~800x slower: 200x `#void(&T)` takes 11.8 s, 200x `#void(&U)` 14 ms (interpreter, JVM);
- with assertions on (`java -ea`, as surefire runs tests): `AssertionError` every time;
- now and then the overflow escapes the catch, even on the JVM (`StackOverflowError`);
- under CheerpJ the overflow surfaces as random `ArrayIndexOutOfBoundsException`s and
  `ClassCastException`s without stack trace, which made Salix fail in the browser.

In the interpreter: `printf '#void(&T);\n' | java -ea -cp rascal.jar org.rascalmpl.shell.WebShell`.

**Fix** (`RascalValueFactory.reifiedType`): build the instantiated constructor type directly, like
`ConstructorType.instantiate` does, but without the cyclic binding:

```java
Type reified = tr.symbolToType(symbol, definitions);
TypeStore store = new TypeStore();
store.declareAbstractDataType(RascalValueFactory.Type);
store.declareConstructor(RascalValueFactory.Type_Reified);
Type constructorType = tf.constructorFromTuple(store, RascalTypeFactory.getInstance().reifiedType(reified),
    RascalValueFactory.Type_Reified.getName(), RascalValueFactory.Type_Reified.getFieldTypes());
return super.constructor(constructorType, symbol, definitions);
```

Tests after the fix (interpreter): `functionality::Reification` 35/35 (4 ignored),
`functionality::FunctionComposition` 27/27, `basic::Functions` 64/64, `concrete::Parsing` 11/11,
`concrete::Syntax1` 37/37. Deeper alternatives: make `ParameterType.instantiate` hygienic in vallang
(it should not need a `StackOverflowError` catch), or give `TypeParam` a name that can't be written
in Rascal (but `type[&T]` is also declared from Rascal source, so the names must agree).

How it was found: `../../web-salix/diag/src/WebDiagnostics.java` (open the Salix page with `?diag`)
runs suspect operations one by one; the failures moved around between runs, and the same diagnostics
on the JVM showed the `StackOverflowError` with `-XX:MaxJavaStackTraceDepth=200000`.

---

## Open

### IfElseBacktrack: `if`/`else` with a backtracking condition always runs `else` (wrong result)

```rascal
if (int x <- xs, x > 2) r = x; else r = 100;   // h([1,5]) == 100, expected 5
```

`translate(... if (...) ... else ...)` in `compile/Rascal2muRascal/RascalStatement.rsc` (around line
345) handles backtracking conditions by emitting
`muBlock([muExists(ifName, translateAndConds(..., thenCode + muFail(ifName), ...)), elseCode])`.
`elseCode` comes after the `muExists` block **unconditionally**. The generated Java leaves the
then-branch with `continue IF0`, which exits the `do { } while(false)` of `muExists` and falls
straight into the else code:

```java
IF0: do { for (...) { if (x > 2) { r = x; continue IF0; } else continue IF0_GEN; } } while(false);
r = 100;   // always executed
```

**Fix direction**: the then-branch must jump past `elseCode`. For example, use a labelled block around
both, with the then-branch leaving that outer label, or use `muIfElse` on the boolean outcome of
`muExists`. There is a commented-out alternative right below this code. The backtrack-free path just
above it is correct: it passes `elseCode` as the false continuation. Look for other users of the
same `muExists` + sequential-else pattern; they may have the same bug.

### CondGenInExpression: generator as `?:` condition inside an expression

```rascal
int main() = 1 + ((2 <- [1, 2]) ? 10 : 20);
```

`translate` for conditional expressions (`RascalExpression.rsc`, around line 2295) returns
`translateBool(condition, btscopes, translate(thenExp), translate(elseExp))`. With a backtracking
condition, that is control flow (a `for` loop with the branches as value continuations). That is fine
directly under a `return`, but here it gets inlined into the `+` expression as Java statements.

**Fix direction**: when `!backtrackFree(condition)`, produce a `muValueBlock` that assigns the chosen
branch to a temporary and yields it, like `translateReducer` does. Note: this depends on the same
"exit the success branch past the failure code" mechanism as IfElseBacktrack (a first attempt here
produced exactly that fall-through), so fix IfElseBacktrack first.

Salix workaround: rewrite `salix::Node::attrsOf` with a list comprehension and an `if` statement
(backtrack-free condition).

### ConstructorShadowsFunction: constructor named like an imported function

```rascal
import List;
data Diff[&T] = remove(&T t, int pos);
list[Diff[int]] main() = [remove(1, 0)];
```

`mkVar` (`TypeUtils.rsc`, around line 848; throws at about line 872) cannot resolve the name `remove`
when it is overloaded between `List::remove` (functions) and the constructor `remove` of a
parameterized ADT. It is reached from `translate((QualifiedName) ...)` in `RascalExpression.rsc`
(around line 1209), the function position of the call. **Fix direction**: handle the
mixed function/constructor overload in `mkVar`, as is already done for unparameterized ADTs (worth
checking whether the `&T` parameter is what breaks the lookup).

### VoidVarargsOverload: `void` varargs function overloading a value-returning one

```rascal
import List;
void head(value vals...) { }
```

The module interface `$M_$I` declares `void head(IValue)`. The class's generated resolver for
arity 1 merges it with `List::head(list[&T])` and returns `IValue`, so the class doesn't implement
the interface, and the void call site fails. **Fix direction**: interface generation and resolver
generation (`muRascal2Java/Resolvers.rsc`, `CodeGen.rsc`) must agree on one Java return type for an
overload set that mixes `void` and non-`void` (e.g. `IValue`, returning `null` for the void
alternatives).

### NestedClosureCapturesParam: inner closure captures an outer closure's parameter

```rascal
int(int) adder() = int(int x) { int() inner = int() { return x + 1; }; return inner(); };
```

The inner closure's capture of `x` is compiled as a `ValueRef` (`externalRefs`, `newValueRef` in
`muRascal2Java/CodeGen.rsc`). But `x` is a plain parameter (`IInteger x`) of the outer closure, so the
code calls `x.getValue()` on an `IInteger` and passes an `IInteger` where a `ValueRef` is expected.
**Fix direction**: when the captured variable is a formal of the enclosing closure, either wrap it
in a `ValueRef` at the point of capture, or (when it is never assigned) pass the value. This is the
core of Salix's `withIndex`, and closures that capture closure parameters are everywhere in Salix,
so it matters most for Salix.

---

## Other issues (not compiler code generation)

- **The compiler's `main` cannot compile anything.** `compile(list[loc] moduleLocs, ...)` in
  `compile/Compile.rsc` is a stub returning `info("not yet implemented", ...)`. Workaround:
  `compile(list[str] qualifiedModuleNames, ...)` from the interpreter (see `../compile.sh`).
- **Test classes need JUnit.** Compiling a module with `test` functions (e.g. `util::Webserver`,
  imported by Salix) also writes `$<Module>Tests.java` into `generatedSources`, which needs JUnit
  to compile. Either generate them into `generatedTestSources` only, or skip them when not wanted.

## Runtime fixes made on this branch (Java, for running compiled code from a jar / in the browser)

- `runtime/RascalExecutionContext.java`: threw "can not run Rascal JUnit tests from within a jar
  file" for any class loaded from a jar; now a jar just means "no project root".
- `ideservices/BasicIDEServices.java`: accepts a null project root.
- `runtime/$RascalModule.java`: `readBinaryConstantsFile` turned the resource URL into a location,
  which fails for opaque `jar:file:...!/` URLs; now reads with `getResourceAsStream`. Also removed a
  debug loop that printed every constructor constant to stderr.
- `runtime/RascalRuntimeValueFactory.java`: `loadParsers`/`loadParser` **ignored the stored parser**
  and generated a new one at every call (and passed `maxAmbDepth` for the recovery parameters);
  they now load the stored parser with the new `runtime/StoredParserClassLoader.java` (no Java
  compiler needed). The parse functions no longer create the parser generator eagerly. Removed the
  `parse uses grammar:` debug print.
