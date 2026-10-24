import { test } from "node:test";
import assert from "node:assert/strict";
import {
  updateFlowNodes,
  saveView,
  getSavedView,
  type FlowNode,
} from "../src/flowLayout.ts";
const nodes: FlowNode[] = [
  { id: "one", position: { x: 100, y: 100 }, data: { block: {} as never } },
  { id: "two", position: { x: 200, y: 300 }, data: { block: {} as never } },
];
test("drag retains dimensions and dragging state instead of resetting nodes", () => {
  let state = updateFlowNodes(nodes, [
    { id: "one", type: "dimensions", dimensions: { width: 240, height: 90 } },
    {
      id: "one",
      type: "position",
      position: { x: 150, y: 130 },
      dragging: true,
    },
  ]);
  assert.equal(state[0].dragging, true);
  assert.equal(state[0].measured?.width, 240);
  assert.deepEqual(state[0].position, { x: 150, y: 130 });
  assert.equal(state[1], nodes[1]);
  state = updateFlowNodes(state, [
    {
      id: "one",
      type: "position",
      position: { x: 180, y: 160 },
      dragging: false,
    },
  ]);
  assert.equal(state[0].dragging, false);
  assert.deepEqual(state[0].position, { x: 180, y: 160 });
  assert.equal(state[0].measured?.width, 240);
});
test("selection changes are retained without replacing unrelated nodes", () => {
  const after = updateFlowNodes(nodes, [
    { id: "one", type: "select", selected: true },
  ]);
  assert.equal(after[0].selected, true);
  assert.equal(after[1], nodes[1]);
});
test("saved graph view retains viewport independently from positions", () => {
  saveView("qa", { positions: { one: { x: 400, y: 200 } } });
  saveView("qa", { viewport: { x: 1, y: 2, zoom: 0.9 } });
  assert.deepEqual(getSavedView("qa"), {
    positions: { one: { x: 400, y: 200 } },
    viewport: { x: 1, y: 2, zoom: 0.9 },
  });
});
