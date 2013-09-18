module experiments::Compiler::Tests::Patterns

extend experiments::Compiler::Tests::TestUtils;

// Literals

// Boolean

test bool tst() = run("true := true") == true := true;
test bool tst() = run("true := false") == true := false;

// Integer

test bool tst() = run("1 := 1") == 1 := 1;
test bool tst() = run("1 := 2") == 1 := 2;

// Real
test bool tst() = run("2.3 := 2.3") == (2.3 := 2.3);
test bool tst() = run("2.5 := 2.3") == (2.5 := 2.3);


// Rational
test bool tst() = run("2r3 := 2r3") == (2r3 := 2r3);
test bool tst() = run("2r5 := 2r3") == (2r5 := 2r3);

// String

test bool tst() = run("\"a\" := \"a\"") == "a" := "a";
test bool tst() = run("\"a\" := \"b\"") == "a" := "b";

// Datetime
// The following two tests fail, since theinterpreter does not support datetime patterns. We are ahead :-)
/*fails*/ //test bool tst() = run("$2012-01-01T08:15:30.055+0100$ := $2012-01-01T08:15:30.055+0100$") == ($2012-01-01T08:15:30.055+0100$ := $2012-01-01T08:15:30.055+0100$);
/*fails*/ //test bool tst() = run("$2013-01-01T08:15:30.055+0100$ := $2012-01-01T08:15:30.055+0100$") == ($2013-01-01T08:15:30.055+0100$ := $2012-01-01T08:15:30.055+0100$);

// Location

test bool tst() = run("|http://www.rascal-mpl.org| := |http://www.rascal-mpl.org|") == (|http://www.rascal-mpl.org| == |http://www.rascal-mpl.org|);
test bool tst() = run("|http://www.rascal-mpl.org| := |std://demo/basic/Hello.rsc|") == (|http://www.rascal-mpl.org| == |std://demo/basic/Hello.rsc|);

// Basic Patterns

test bool tst() = run("x := 2") == x := 2;
test bool tst() = run("x := 2") == x := 2 && x == 2;

test bool tst() = run("int x := 2") == int x := 2;
test bool tst() = run("int x := 2") == int x := 2 && x == 2;

test bool tst() = run("x:1 := 1") == x:1 := 1;
test bool tst() = run("x:1 := 1") == x:1 := 1 && x == 1;

test bool tst() = run("int x:1 := 1") == int x:1 := 1;
test bool tst() = run("int x:1 := 1") == int x:1 := 1 && x == 1;

test bool tst() = run("[int] 1 := 1") == [int] 1 := 1;

test bool tst() = run("! 1 := 2") == ! 1 := 2;
test bool tst() = run("! 1 := 1") == ! 1 := 1;

// List matching
test bool tst() = run("[1] := [1]") == [1] := [1];
test bool tst() = run("[1] := [2]") == [1] := [2];
test bool tst() = run("[1] := [1,2]") == [1] := [1,2];

test bool tst() = run("[1, x*, 5] := [1,2,3,4,5]") == [1, x*, 5] := [1,2,3,4,5];
test bool tst() = run("[1, x*, 5] := [1,2,3,4,5]") == [1, x*, 5] := [1,2,3,4,5] && x == [2,3,4];

test bool tst() = run("[1, *x, 5] := [1,2,3,4,5]") == [1, *x, 5] := [1,2,3,4,5];
test bool tst() = run("[1, *x, 5] := [1,2,3,4,5]") == [1, *x, 5] := [1,2,3,4,5] && x == [2,3,4];

test bool tst() = run("[1, *int x, 5] := [1,2,3,4,5]") == [1, *int x, 5] := [1,2,3,4,5];
test bool tst() = run("[1, *int x, 5] := [1,2,3,4,5]") == [1, *int x, 5] := [1,2,3,4,5] && x == [2,3,4];


test bool tst() = run("[*int x, 3, *x] := [1,2,3,1,2]") == [*int x, 3, x] := [1,2,3,1,2] && x == [1, 2];
test bool tst() = run("[*int x, 3, *x] := [1,2,3,1,2] && x == [1, 2]") == [*int x, 3, x] := [1,2,3,1,2] && x == [1, 2];

test bool tst() = run("[*int x, *x, 3] := [1,2,1,2,3] && x == [1, 2]") == [*int x, *x, 3] := [1,2,1,2, 3] && x == [1, 2];
test bool tst() = run("[*int x, *x, 3] := [1,2,3,1,2]") == [*int x, *x, 3] := [1,2,3,1,2];

// Set matching

test bool tst() = run("{1} := {1}") == {1} := {1};
test bool tst() = run("{1} := {2}") == {1} := {2};
test bool tst() = run("{1, 2} := {2, 1}") == {1, 2} := {2, 1};
test bool tst() = run("{1} := {1,2}") == {1} := {1,2};

test bool tst() = run("{x, 2} := {2, 1}") == {x, 2} := {2, 1};

test bool tst() = run("{1, x*, 5} := {1,2,3,4,5}") == {1, x*, 5} := {1,2,3,4,5};
test bool tst() = run("{1, x*, 5} := {1,2,3,4,5} && x == {2, 3, 4}") == {1, x*, 5} := {1,2,3,4,5} && x == {2,3,4};

test bool tst() = run("{1, *int x, 5} := {1,2,3,4,5}") == {1, *int x, 5} := {1,2,3,4,5};
test bool tst() = run("{1, *int x, 5} := {1,2,3,4,5} && x == {2, 3, 4}") == {1, *int x, 5} := {1,2,3,4,5} && x == {2,3,4};

test bool tst() = run("{ y = {5, 6}; {*int x, 3, *y} := {1,2,3,4,5,6};}") == { y = {5, 6}; {*int x, 3, *y} := {1,2,3,4,5,6};};

// Node/Constructor matching

test bool tst() = run("d1(1,\"a\") := d1(1, \"a\")") == d1(1,"a") := d1(1, "a");
test bool tst() = run("d1(1,\"a\") := d1(2,\"a\")") == d1(1,"a") := d1(2,"a");
test bool tst() = run("d2(\"a\", true) := d2(\"a\", true)") == d2("a", true) := d2("a", true);
test bool tst() = run("d2(\"a\", true) := d2(\"b\", true)") == d2("a", true) := d2("b", true);

test bool tst() = run("d1(x, \"a\") := d1(1, \"a\")") == d1(x, "a") := d1(1, "a") && x == 1;
test bool tst() = run("d1(int x, \"a\") := d1(1, \"a\")") == d1(int x, "a") := d1(1, "a") && x == 1;

test bool tst() = run("str f(int x, str s) := d1(1, \"a\")") == str f(int x, str s) := d1(1, "a") && x == 1 && s == "a" && f == "d1";

// Descendant matching

test bool tst() = run("/1 := [[1, 2], [5, [8], 7], \"a\"]") == /1 := [[1, 2], [5, [8], 7], "a"];
test bool tst() = run("/2 := [[1, 2], [5, [8], 7], \"a\"]") == /2 := [[1, 2], [5, [8], 7], "a"];
test bool tst() = run("/8 := [[1, 2], [5, [8], 7], \"a\"]") == /8 := [[1, 2], [5, [8], 7], "a"];
test bool tst() = run("/10 := [[1, 2], [5, [8], 7], \"a\"]") == /10 := [[1, 2], [5, [8], 7], "a"];

test bool tst() = run("/1 := d1(1, \"a\")") == /1 := d1(1, "a");
test bool tst() = run("/1 := d1(2, \"a\")") == /1 := d1(2, "a");

test bool tst() = run("/1 := [10, d1(1, \"a\"), 11]") == /1 := [10, d1(1, "a"), 11];
test bool tst() = run("/1 := [10, d1(2, \"a\"), 11]") == /1 := [10, d1(2, "a"), 11];

test bool tst() = run("/1 := {10, d1(1, \"a\"), 11}") == /1 := {10, d1(1, "a"), 11};
test bool tst() = run("/1 := {10, d1(2, \"a\"), 11}") == /1 := {10, d1(2, "a"), 11};

test bool tst() = run("/1 := \<10, d1(1, \"a\"), 11\>") == /1 := <10, d1(1, "a"), 11>;
test bool tst() = run("/1 := \<10, d1(2, \"a\"), 11\>") == /1 := <10, d1(2, "a"), 11>;

test bool tst() = run("/3 := (1 :10, 2:20, 3 :30)") == /3 := (1 :10, 2:20, 3 :30);
test bool tst() = run("/4 := (1 :10, 2:20, 3 :30)") == /3 := (1 :10, 2:20, 4 :30);


test bool tst() = run("/300 := (1 :[10, 100], 2:[20,200], 3 :[30,300])") == /300 := (1 :[10, 100], 2:[20,200], 3 :[30,300]);
test bool tst() = run("/400 := (1 :[10, 100], 2:[20,200], 3 :[30,300])") == /400 := (1 :[10, 100], 2:[20,200], 3 :[30,300]);


test bool tst() = run("/int x := d1(1, \"a\") && x == 1") == (/int x := d1(1, "a") && x == 1);

