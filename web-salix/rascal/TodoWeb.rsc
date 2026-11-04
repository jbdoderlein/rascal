module TodoWeb

import SalixWeb;
import salix::App;
import salix::demo::todomvc::TodoMVC;

// TodoMVC exactly as in salix-core; it renders into the page's <body>
SalixApp[Model] app = todoMVCApp();

str init() = salixInit(app);

str message(str payload) = salixMessage(app, payload);
