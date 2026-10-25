import dagre from "@dagrejs/dagre";
import {
  applyNodeChanges,
  type Node,
  type NodeChange,
  type Viewport,
} from "@xyflow/react";
import type { Graph, GraphNode } from "./types";
import { symbolFor, symbolContext, type SymbolSpec } from "./flowSymbols.ts";
export type FlowData = Record<string, unknown> & { block: GraphNode; symbol?: SymbolSpec };
export type FlowNode = Node<FlowData>;
export function layoutMethod(graph: Graph, method: string): FlowNode[] {
  const context = symbolContext(graph.source);
  const visible = graph.nodes.filter((n) => !method || n.method === method);
  const ids = new Set(visible.map((n) => n.id));
  const d = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
  d.setGraph({
    rankdir: "TB",
    nodesep: 90,
    ranksep: 70,
    marginx: 30,
    marginy: 30,
  });
  const symbols = new Map(visible.map(n=>[n.id, symbolFor(n,context)]));
  visible.forEach((n) => d.setNode(n.id, { width: symbols.get(n.id)!.width, height: symbols.get(n.id)!.height }));
  graph.edges
    .filter((e) => ids.has(e.source) && ids.has(e.target))
    .forEach((e) => d.setEdge(e.source, e.target));
  dagre.layout(d);
  // Keep loop bodies predominantly to the left of their test. A right-side exit
  // can then reach the continuation without cutting through the body branch.
  const loopIds = new Set(visible.filter(n=>n.kind === "loopCondition").map(n=>n.id));
  const bodyDirection = graph.edges.filter(e=>loopIds.has(e.source)&&ids.has(e.target)&&["true","next"].includes(e.label))
    .reduce((score,e)=>score+Math.sign(d.node(e.target).x-d.node(e.source).x),0);
  if(bodyDirection>0){
    const left=Math.min(...visible.map(n=>d.node(n.id).x-symbols.get(n.id)!.width/2));
    const right=Math.max(...visible.map(n=>d.node(n.id).x+symbols.get(n.id)!.width/2));
    visible.forEach(n=>{d.node(n.id).x=left+right-d.node(n.id).x;});
  }
  return visible.map((n) => ({
    id: n.id,
    type: "block",
    ariaLabel: `${symbols.get(n.id)!.title}, строка ${n.line}: ${n.label}`,
    position: { x: d.node(n.id).x - symbols.get(n.id)!.width/2, y: d.node(n.id).y - symbols.get(n.id)!.height/2 },
    width: symbols.get(n.id)!.width,
    height: symbols.get(n.id)!.height,
    data: { block: n, symbol: symbols.get(n.id)! },
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
