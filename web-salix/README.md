# TodoMVC (Salix) in the browser, interpreted (experimental PoC)

Salix's TodoMVC demo (`../../salix-core`) running entirely inside the page: CheerpJ runs the Rascal
**interpreter**, and the unmodified `salix.js` talks to it directly instead of to an HTTP server.
(The compiled route is blocked by compiler bugs, see `../web-compiled/bugs/README.md`.)

```sh
mvn package -DskipTests                     # the rascal jar, with org.rascalmpl.shell.WebShell
web-salix/build.sh                          # app.jar (Salix sources + bridge), salix.js, style.css, rascal.jar
npx http-server web-salix -p 8080 -c-1      # needs HTTP Range support
# open http://localhost:8080/ ; the panel in the corner shows what each step costs
```

## How it works

Salix already separates the app from HTTP: `SalixApp = <id, SalixResponse(SalixRequest)>`, and
`salix::App::webApp` is only the HTTP adapter. Here, the adapter is replaced by:

- `rascal/SalixWeb.rsc`: `salixInit(app)` (= `GET /` + `GET /<id>/init`) and
  `salixMessage(app, json)` (= `GET /<id>/msg`), returning the same JSON as the HTTP server, plus the
  boot page's `<body>` content.
- `rascal/TodoWeb.rsc`: binds them to `todoMVCApp()`, unchanged from salix-core.
- `index.html`: loads `app.jar` + `rascal.jar` with `cheerpjRunLibrary`, imports `TodoWeb` through
  `WebShell` (search path `|resource:///rascal-app/|`, i.e. that folder on the class path;
  the standard library comes from `|resource:///org/rascalmpl/library/|` when the rascal jar can not
  be located as a file, as under CheerpJ), puts the boot body in the page,
  and overrides `salix.sendMessage` to call `message(json)` instead of `fetch`.

Constraints: Salix diffs only `<body>` and `salix.js` patches `document.body` by child position,
so the page keeps `<body>` for Salix only (no scripts or extra elements in it).

For another Salix app: write a module like `TodoWeb.rsc`, and change the module and the app id
(`new Salix("todoMVC")`) in `index.html`.

## Performance (JVM, same code; compare with the panel in the browser)

| step | JVM |
|---|---|
| `import TodoWeb` (parses all Salix modules) | ~1.1 s (was 2.4–3.5 s, see below) |
| `init` (boot + first render) | ~100 ms |
| one message (typing in the input) | 40 ms first, then 12–40 ms |

Interpreter changes made for this PoC (in the rascal repo):

- `ShellEvaluatorFactory.getBasicEvaluator`: when the rascal jar can't be located as a file (under
  CheerpJ), the standard library is read from the class path (`|resource:///org/rascalmpl/library/|`).
- `Expression.ReifyType`: `#T` no longer starts the parser generator when neither the module nor
  its imports define syntax (the grammar then has no rules a type can refer to). Before, every
  `#T` in `lang::json::IO`'s `defaultJSONNULLValues` loaded and ran the grammar library in a second
  interpreter. That halved the import time, and under CheerpJ that code crashed with an
  `ArrayIndexOutOfBoundsException` in `RascalFunction.call` (not reproducible on the JVM, even with
  `-Xss256k`; cause unknown). Tests: `functionality::Reification` 35/35 (4 ignored),
  `concrete::Parsing` 11/11, `concrete::Syntax1` 37/37.

- `RascalValueFactory.reifiedType`: building the value `type(symbol, definitions)` instantiated
  `type[&T]` with `&T -> <the reified type>`, where `&T` is `RascalValueFactory.TypeParam`, named
  `T`. If the reified type itself contains a user parameter named `T` (e.g. `void(&T)`, as in
  Salix's `render(&T model, void(&T) block)`), that binding is cyclic, and vallang's
  `ParameterType.instantiate` recursed until it caught a `StackOverflowError` (~15000 frames per call;
  it sometimes escaped the catch, even on the JVM). Under CheerpJ this surfaced as random
  `ArrayIndexOutOfBoundsException`s and `ClassCastException`s, without stack traces. Now the
  instantiated constructor type is built directly, without the cyclic binding. Found with
  `diag/src/WebDiagnostics.java` (`?diag` in the page URL). Same tests pass, plus
  `basic::Functions` 64/64 and `functionality::FunctionComposition` 27/27.

Note: `rascal.jar` has stale copies of some standard library modules at its root (`Content.rsc`,
`IO.rsc`, `List.rsc`), from a shaded dependency, so `|resource:///|` can't be used as a search path;
the app modules are under `rascal-app/` in `app.jar`.

Under CheerpJ, expect these to be several times slower (compiled code ran ~5-6x slower than on
the JVM). The import is the main cost: it happens at every page load.
