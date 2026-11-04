module JsonImport

// FIXED: crashed the compiler with "No module name found for .../check/CollectExpression.rsc"
import lang::json::IO;

str main() = asJSON(("a": [1, 2]));
