import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  MarkerType,
  type NodeProps,
  type NodeChange,
  type ReactFlowInstance,
} from "@xyflow/react";
import type { Graph, GraphNode } from "./types";
import {
  layoutMethod,
  updateFlowNodes,
  getSavedView,
  saveView,
  type FlowNode,
} from "./flowLayout";
import "@xyflow/react/dist/style.css";
const labels: Record<string, string> = {
  start: "ВХОД",
  end: "ВЫХОД",
  condition: "УСЛОВИЕ",
  loopCondition: "ЦИКЛ",
  operation: "ОПЕРАЦИЯ",
  call: "ВЫЗОВ",
  return: "RETURN",
  throw: "THROW",
  exception: "РЕГИОН",
  break: "BREAK",
  continue: "CONTINUE",
};
const Block = memo(function Block({ data, selected }: NodeProps<FlowNode>) {
  const b = data.block;
  return (
    <div className={`flow-node ${b.kind} ${selected ? "chosen" : ""}`}>
      <Handle type="target" position={Position.Top} />
      <div className="node-caption">
        <span>{labels[b.kind] ?? b.kind}</span>
        <span>L{b.line}</span>
      </div>
      <div className="node-code">
        {b.label.length > 110 ? b.label.slice(0, 110) + "…" : b.label}
      </div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
});
const nodeTypes = { block: Block };
export type GraphFocus = { id: string; version: number } | null;
type Props = {
  graph: Graph | null;
  method: string;
  selected: string;
  onSelect: (n: GraphNode) => void;
  focus: GraphFocus;
  layoutVersion: number;
  dark: boolean;
};
/** New source/method/layout creates a canvas; moving or selecting a node never lays it out again. */
export default memo(function Flowchart(props: Props) {
  if (!props.graph)
    return (
      <div className="canvas-empty">
        После успешного разбора здесь появится граф
      </div>
    );
  const viewKey = `${props.graph.sourceHash}/${props.method}/${props.layoutVersion}`;
  return (
    <FlowCanvas
      key={viewKey}
      {...props}
      graph={props.graph}
      viewKey={viewKey}
    />
  );
});
function FlowCanvas({
  graph,
  method,
  selected,
  onSelect,
  focus,
  dark,
  viewKey,
}: Props & { graph: Graph; viewKey: string }) {
  const host = useRef<HTMLDivElement>(null);
  const initial = useMemo(
    () =>
      layoutMethod(graph, method).map((n) => ({
        ...n,
        position: getSavedView(viewKey)?.positions[n.id] ?? n.position,
      })),
    [graph, method, viewKey],
  );
  const [nodes, setNodes] = useState<FlowNode[]>(initial);
  const [instance, setInstance] = useState<ReactFlowInstance<FlowNode> | null>(
    null,
  );
  const dragging = useRef(false);
  const edges = useMemo(() => {
    const ids = new Set(initial.map((n) => n.id));
    return graph.edges
      .filter((e) => ids.has(e.source) && ids.has(e.target))
      .map((e) => ({
        ...e,
        type: "smoothstep",
        markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
        style: {
          stroke:
            e.label === "true"
              ? "#178b73"
              : e.label === "false"
                ? "#d48c37"
                : "#99a7b9",
          strokeWidth: 1.5,
        },
        labelStyle: { fontSize: 10, fill: dark ? "#c4d1df" : "#5d6b7c" },
        labelBgStyle: { fill: dark ? "#202b3b" : "#f7f9fc" },
      }));
  }, [graph, initial, dark]);
  const changeNodes = useCallback(
    (changes: NodeChange<FlowNode>[]) =>
      setNodes((current) => updateFlowNodes(current, changes)),
    [],
  );
  useEffect(() => {
    setNodes((current) =>
      current.map((n) =>
        n.selected === (n.id === selected)
          ? n
          : { ...n, selected: n.id === selected },
      ),
    );
  }, [selected]);
  useEffect(() => {
    if (!instance) return;
    const saved = getSavedView(viewKey)?.viewport;
    const entry =
      initial.find((n) => n.data.block.kind === "start") ?? initial[0];
    if (saved) void instance.setViewport(saved);
    else if (entry)
      void instance.setViewport({
        x:
          (host.current?.clientWidth ?? 500) / 2 -
          (entry.position.x + 120) * 0.85,
        y: 35 - entry.position.y * 0.85,
        zoom: 0.85,
      });
  }, [instance, initial, viewKey]);
  useEffect(() => {
    if (!instance || !focus || dragging.current) return;
    const n =
      instance.getNode(focus.id) ?? initial.find((n) => n.id === focus.id);
    if (n)
      void instance.setCenter(n.position.x + 120, n.position.y + 45, {
        zoom: Math.max(instance.getZoom(), 0.85),
        duration: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? 0
          : 220,
      });
  }, [focus, instance, initial]);
  return (
    <div ref={host} className="flow-host">
      <ReactFlow
        onInit={setInstance}
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={changeNodes}
        onNodeClick={(_, n) => onSelect(n.data.block)}
        onNodeDragStart={() => {
          dragging.current = true;
          host.current?.classList.add("is-dragging");
        }}
        onNodeDragStop={(_, n) => {
          dragging.current = false;
          host.current?.classList.remove("is-dragging");
          saveView(viewKey, {
            positions: {
              ...getSavedView(viewKey)?.positions,
              [n.id]: { ...n.position },
            },
          });
        }}
        onMoveEnd={(_, viewport) => saveView(viewKey, { viewport })}
        nodeDragThreshold={4}
        nodesConnectable={false}
        deleteKeyCode={null}
        minZoom={0.1}
        maxZoom={2}
        colorMode={dark ? "dark" : "light"}
      >
        <Background color={dark ? "#344256" : "#d7dfe9"} gap={20} size={1} />
        <Controls showInteractive={false} />
        <MiniMap
          pannable
          zoomable
          nodeColor={dark ? "#42506b" : "#c5d3e8"}
          maskColor={dark ? "#16202bb0" : "#f4f7faaf"}
        />
      </ReactFlow>
    </div>
  );
}
