import dagre from "@dagrejs/dagre";
import {
  applyNodeChanges,
  type Node,
  type NodeChange,
  type Viewport,
} from "@xyflow/react";
import type { Graph, GraphNode } from "./types";
export type FlowData = Record<string, unknown> & { block: GraphNode };
export type FlowNode = Node<FlowData>;
export function layoutMethod(graph: Graph, method: string): FlowNode[] {
  const visible = graph.nodes.filter((n) => !method || n.method === method);
  const ids = new Set(visible.map((n) => n.id));
  const d = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  d.setGraph({
    rankdir: "TB",
    nodesep: 65,
    ranksep: 55,
    marginx: 30,
    marginy: 30,
  });
  visible.forEach((n) => d.setNode(n.id, { width: 240, height: 90 }));
  graph.edges
    .filter((e) => ids.has(e.source) && ids.has(e.target))
    .forEach((e) => d.setEdge(e.source, e.target));
  dagre.layout(d);
  return visible.map((n) => ({
    id: n.id,
    type: "block",
    position: { x: d.node(n.id).x - 120, y: d.node(n.id).y - 45 },
    data: { block: n },
  }));
}
/** Apply the whole React Flow change batch, including dimensions and dragging state. */
export function updateFlowNodes(
  nodes: FlowNode[],
  changes: NodeChange<FlowNode>[],
): FlowNode[] {
  return applyNodeChanges(changes, nodes);
}
export type SavedView = {
  positions: Record<string, { x: number; y: number }>;
  viewport?: Viewport;
};
const views = new Map<string, SavedView>();
export function getSavedView(key: string): SavedView | undefined {
  return views.get(key);
}
export function saveView(key: string, change: Partial<SavedView>) {
  const current = views.get(key) ?? { positions: {} };
  views.set(key, { ...current, ...change });
  if (views.size > 16) views.delete(views.keys().next().value!);
}
