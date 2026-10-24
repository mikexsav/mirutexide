import { test } from "node:test";
import assert from "node:assert/strict";
import { getJavaSuggestions, javaContext } from "../src/javaSuggestions.ts";
const suggestions = (source: string) =>
  getJavaSuggestions(source, source.length);
const names = (source: string) => suggestions(source).map((s) => s.label);
test("sout is a real Tab-stop snippet", () => {
  const s = suggestions("class Main {void f(){sou").find(
    (s) => s.label === "sout",
  );
  assert.ok(s);
  assert.match(s.insertText, /System\.out\.println\(\$\{1:/);
  assert.match(s.insertText, /\$0/);
});
test("Java main and for snippets", () => {
  assert.ok(names("class Main {psv").includes("psvm"));
  assert.ok(names("class Main {void f(){fori").includes("fori"));
});
test("System and PrintStream receiver completion", () => {
  assert.deepEqual(names("System.o"), ["out"]);
  assert.ok(names("System.out.pr").includes("println"));
  assert.ok(names("System.err.").includes("printf"));
});
test("collection type determines member suggestions", () => {
  assert.ok(
    names(
      "class X {void f(){ List<Integer> values = new ArrayList<>(); values.",
    ).includes("get"),
  );
  assert.ok(
    names(
      "class X {void f(){ Map<String,Integer> counts = new HashMap<>(); counts.",
    ).includes("put"),
  );
  assert.ok(
    !names(
      "class X {void f(){ Map<String,Integer> counts = new HashMap<>(); counts.",
    ).includes("println"),
  );
});
test("Scanner and String members", () => {
  assert.ok(
    names(
      "class X {void f(){ Scanner input = new Scanner(System.in); input.ne",
    ).includes("nextInt"),
  );
  assert.ok(
    names('class X {void f(){ String message="hello"; message.le').includes(
      "length",
    ),
  );
});
test("array length is a field, not a method", () => {
  const s = suggestions("class X {void f(){ int[] data={1}; data.le").find(
    (s) => s.label === "length",
  );
  assert.equal(s?.insertText, "length");
  assert.equal(s?.kind, "field");
});
test("unknown receiver does not invent members", () =>
  assert.deepEqual(names("unknown."), []));
test("existing parentheses are not duplicated", () => {
  const s = "System.out.pr()";
  assert.equal(
    getJavaSuggestions(s, "System.out.pr".length).find(
      (s) => s.label === "println",
    )?.insertText,
    "println",
  );
});
test("no completion inside comments, strings, char or text blocks", () => {
  for (const s of [
    "// sou",
    "/* sou",
    'System.out.println("sou',
    "char c='s",
    'String text="""\nsou',
  ])
    assert.deepEqual(names(s), [], s);
});
test("comment and string closing restore Java context", () => {
  assert.ok(names("/* note */ sou").includes("sout"));
  assert.ok(names('"text"; sou').includes("sout"));
  assert.ok(names("// note\nsou").includes("sout"));
});
test("mask preserves UTF-16 offsets and newlines", () => {
  const s = 'String s="Я 😀";\n/*abc*/sou';
  assert.equal(javaContext(s, s.length).masked.length, s.length);
  assert.equal(javaContext(s, s.length).masked.split("\n").length, 2);
});
test("names respect closed lexical blocks", () => {
  const s = "class X { void f(){ int visible=1; { int hidden=2; } hi";
  assert.ok(!names(s).includes("hidden"));
  assert.ok(names(s.replace(/hi$/, "vis")).includes("visible"));
});
test("method parameters and unicode variables are suggested", () => {
  assert.ok(names("class X {void f(String message){ mess").includes("message"));
  assert.ok(names("class X {void f(){int сумма=1; сум").includes("сумма"));
});
test("empty line does not open a giant popup", () =>
  assert.deepEqual(names("class X {void f(){\n    "), []));
test("declared methods are suggested even when defined after the cursor", () => {
  const source =
    "class X {void main(){ tot } private static int total(List<Integer> values){return 0;}}";
  const found = getJavaSuggestions(source, source.indexOf("tot") + 3).find(
    (s) => s.label === "total",
  );
  assert.ok(found);
  assert.equal(found.insertText, "total(${1:values})$0");
});
