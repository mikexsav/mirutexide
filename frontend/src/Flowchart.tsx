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
import FlowSymbol from "./FlowSymbol";
import LoopbackEdge from "./LoopbackEdge";
import { symbolFor, readableEdge } from "./flowSymbols";
import "./FlowSymbols.css";
import { routeConnections } from "./flowRouting";
const Block = memo(function Block({ data, selected }: NodeProps<FlowNode>) {
  const b = data.block;
  const symbol = data.symbol ?? symbolFor(b);
  const description = symbol.shape === "terminal" ? b.label : b.code;
  return <div className={`flow-node flow-symbol symbol-${symbol.shape} ${b.kind} ${selected ? "chosen" : ""}`} style={{width:symbol.width,height:symbol.height}} title={`${symbol.title} · L${b.line}\n${description.length>600 ? description.slice(0,600)+"… (полный код в редакторе)" : description}`}>
    <FlowSymbol shape={symbol.shape} width={symbol.width} height={symbol.height}/>
    <Handle type="target" id="in-top" position={Position.Top}/>
    <Handle type="target" id="in-left" position={Position.Left}/>
    <div className="symbol-content"><div className="node-caption"><span>{symbol.title}</span><span>L{b.line}</span></div><div className="node-code">{b.label.length>105?b.label.slice(0,105)+"…":b.label}</div></div>
    <Handle type="source" id="out-bottom" position={Position.Bottom}/>
    <Handle type="source" id="out-left" position={Position.Left}/>
    <Handle type="source" id="out-right" position={Position.Right}/>
  </div>;
});
const edgeTypes = { loopback: LoopbackEdge };
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
  const viewKey = `symbols-v3/${props.graph.sourceHash}/${props.method}/${props.layoutVersion}`;
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
    return routeConnections(graph,initial).map(e=>{
      const back=e.backward;
      const sourceHandle=e.sourceHandle;
      const stroke=e.label==="true"||e.label==="next"?"#259d83":e.label==="false"||e.label==="exhausted"?"#d89a42":back?"#8d84d8":e.label==="throw"||e.label==="possible abrupt completion"?"#c47e86":dark?"#8499b6":"#8494ad";
      return {...e,label:readableEdge(e.label),sourceHandle,targetHandle:back?"in-left":"in-top",type:back?"loopback":"smoothstep",data:back?{laneX:e.laneX}:undefined,
        markerEnd:{type:MarkerType.ArrowClosed,width:18,height:18},
        style:{stroke,strokeWidth:1.8,...(back?{strokeDasharray:"6 3"}:{})},
        labelStyle:{fontSize:11,fontWeight:600,fill:dark?"#d7e2f2":"#45566e"},labelBgPadding:[6,3] as [number,number],labelBgBorderRadius:4,
        labelBgStyle:{fill:dark?"#1c2a3d":"#f8faff"},
      };
    });
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
          (entry.position.x + (entry.width ?? 240)/2) * 0.85,
        y: 35 - entry.position.y * 0.85,
        zoom: 0.85,
      });
  }, [instance, initial, viewKey]);
  useEffect(() => {
    const element = host.current;
    if (!instance || !element) return;
    let previous = { width: element.clientWidth, height: element.clientHeight };
    const observer = new ResizeObserver(() => {
      const next = { width: element.clientWidth, height: element.clientHeight };
      if (previous.width && previous.height && !dragging.current) {
        const v = instance.getViewport();
        void instance.setViewport({ ...v, x: v.x + (next.width-previous.width)/2, y: v.y + (next.height-previous.height)/2 });
      }
      previous = next;
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [instance]);
  useEffect(() => {
    if (!instance || !focus || dragging.current) return;
    const n =
      instance.getNode(focus.id) ?? initial.find((n) => n.id === focus.id);
    if (n)
      void instance.setCenter(n.position.x + (n.width ?? 260)/2, n.position.y + (n.height ?? 96)/2, {
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
        edgeTypes={edgeTypes}
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
        ariaLabelConfig={{"controls.zoomIn.ariaLabel":"Приблизить", "controls.zoomOut.ariaLabel":"Отдалить", "controls.fitView.ariaLabel":"Весь метод", "minimap.ariaLabel":"Мини-карта метода"}}
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
