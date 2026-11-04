module ConstructorShadowsFunction

// OPEN: crashes the compiler in TypeUtils::mkVar ("mkVar: remove at ...")
// (salix::util::LCS has a constructor `remove` of Diff[&T], next to List::remove)
import List;

data Diff[&T] = remove(&T t, int pos);

list[Diff[int]] main() = [remove(1, 0)];
