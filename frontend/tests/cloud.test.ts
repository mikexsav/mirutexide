import { test } from "node:test";
import assert from "node:assert/strict";
import { newState, templates, restoreLocal } from "../src/workspaces.ts";
import { passportData } from "../src/qualityPassport.ts";
import {
  api,
  ApiError,
  setCsrfToken,
  type Graph,
  type Comparison,
} from "../src/types.ts";

test("new spaces keep independent main source and baseline file maps", () => {
  const a = newState(),
    b = newState();
  a.project.files["Extra.java"] = "class Extra {}";
  assert.deepEqual(b.project.files, {});
  assert.deepEqual(a.baseline.files, {});
  assert.ok(
    templates.some(
      (t) => t.id === "flow" && t.project.source.includes("while"),
    ),
  );
});

test("an empty editor draft survives browser recovery", () => {
  const previous = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
  const state = newState();
  state.project.source = "";
  state.baseline.source = "";
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: { getItem: () => JSON.stringify(state) },
  });
  try {
    assert.equal(restoreLocal("private-workspace").project.source, "");
  } finally {
    if (previous) Object.defineProperty(globalThis, "localStorage", previous);
    else Reflect.deleteProperty(globalThis, "localStorage");
  }
});
test("quality passport never invents compile, analysis or comparison results", () => {
  const state = newState();
  const report = passportData(
    state,
    { graph: null, analyzed: false, issues: [], run: null, comparison: null },
    "testhash",
    "now",
  );
  assert.equal(report.graph.status, "not_current");
  assert.deepEqual(report.analysis, { status: "not_run" });
  assert.deepEqual(report.compilation_or_run, { status: "not_current" });
  assert.deepEqual(report.comparison, { status: "not_run" });
  assert.equal(report.project_sha256, "testhash");
});
test("passport rejects comparison for previous inputs and binds files to project", () => {
  const state = newState();
  state.project.files = { "Component.java": "class Component {}" };
  const graph = {
    source: state.project.source,
    methods: [],
    nodes: [],
    limitations: [],
  } as unknown as Graph;
  const comparison = {
    behavior_preserved_for_tests: true,
    results: [{ stdin: "old", passed: true }],
    note: "test",
  } as Comparison;
  const r = passportData(
    state,
    { graph, analyzed: true, issues: [], run: null, comparison },
    "abc",
    "now",
  );
  assert.deepEqual(r.comparison, { status: "not_run" });
  assert.ok(r.files.includes("Component.java"));
  const matching = {
    ...comparison,
    results: state.inputs
      .split("\n---\n")
      .map((stdin) => ({ stdin, passed: true })),
  } as Comparison;
  assert.equal(
    passportData(
      state,
      { graph, analyzed: false, issues: [], run: null, comparison: matching },
      "abc",
      "now",
    ).comparison,
    matching,
  );
});
test("API sends same-origin cookie and CSRF and preserves conflict HTTP status", async () => {
  const original = globalThis.fetch;
  let seen: RequestInit | undefined;
  globalThis.fetch = async (_url, options) => {
    seen = options;
    return new Response(JSON.stringify({ detail: "newer server version" }), {
      status: 409,
      headers: { "content-type": "application/json" },
    });
  };
  try {
    setCsrfToken("session-csrf");
    await assert.rejects(
      api("/api/workspaces/x", { version: 1 }, "PUT"),
      (e) =>
        e instanceof ApiError &&
        e.status === 409 &&
        e.message === "newer server version",
    );
    assert.equal(seen?.credentials, "same-origin");
    assert.equal(seen?.method, "PUT");
    assert.equal(
      (seen?.headers as Record<string, string>)["X-Mirutex-CSRF"],
      "session-csrf",
    );
  } finally {
    globalThis.fetch = original;
    setCsrfToken(null);
  }
});
