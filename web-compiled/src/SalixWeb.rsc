module SalixWeb

import salix::App;
import salix::demo::basic::Counter;
import lang::json::IO;

// Salix's HTTP-independent app; "root" is the id of the DOM element it renders into
SalixApp[Model] app = makeApp("root", init, view, update);

// the same JSON as salix::App::webApp sends over HTTP
str reply(SalixResponse r)
  = asJSON(("commands": r.cmds, "subs": r.subs, "patch": r.patch), dateTimeAsInt=true);

// what GET /root/init does (after GET / has booted the app)
str salixInit() {
  app.rr(boot());
  return reply(app.rr(begin()));
}

// what GET /root/msg?payload=... does
str salixMessage(str payload)
  = reply(app.rr(message(parseJSON(#map[str,value], payload))));
