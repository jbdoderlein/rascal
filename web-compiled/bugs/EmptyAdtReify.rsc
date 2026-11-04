module EmptyAdtReify

// FIXED: crashed the compiler with NoSuchKey(aadt("Msg",[],dataSyntax()))
// (salix::Core declares `data Msg;` and reifies #Msg; the constructors come from user modules)
data Msg;

str main() = "<#Msg>";
