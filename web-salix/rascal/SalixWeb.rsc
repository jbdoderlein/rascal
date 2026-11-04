module SalixWeb

// Runs a Salix app inside the page instead of behind an HTTP server: the page's JavaScript calls
// these functions directly (through CheerpJ) where salix.js would otherwise call GET /, GET /<id>/init
// and GET /<id>/msg?payload=...

import salix::App;
import salix::Node;
import lang::json::IO;

// the same JSON as salix::App::webApp sends over HTTP
str reply(SalixResponse r, map[str, value] extra = ())
  = asJSON(extra + ("commands": r.cmds, "subs": r.subs, "patch": r.patch), dateTimeAsInt=true);

// GET / (boot) followed by GET /<id>/init. The app must render an <html> page (salix::Index) as
// usual: Salix only diffs the <body>, and salix.js patches document.body. So the reply also
// carries the boot page's body content ("body"), for the page to show before applying the patch.
str salixInit(SalixApp[&T] app) {
  Node page = app.rr(boot()).doc;
  str body = ("" | it + toHtml(kid) | Node kid <- page.kids[1].kids);
  return reply(app.rr(begin()), extra = ("body": body));
}

// GET /<id>/msg?payload=<json>
str salixMessage(SalixApp[&T] app, str payload)
  = reply(app.rr(message(parseJSON(#map[str,value], payload))));
