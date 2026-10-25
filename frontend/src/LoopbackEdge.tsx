import { BaseEdge, EdgeLabelRenderer, type EdgeProps } from '@xyflow/react';
/** Side lane for upward/repeating transfers. The path follows live handle positions during drag. */
export default function LoopbackEdge({id,sourceX,sourceY,targetX,targetY,markerEnd,style,label,data}:EdgeProps) {
  const lane=Math.min(Number(data?.laneX??0),sourceX-45,targetX-45);
  const path=`M ${sourceX} ${sourceY} H ${lane} V ${targetY} H ${targetX}`;
  return <><BaseEdge id={id} path={path} markerEnd={markerEnd} style={{...style,strokeLinejoin:'round'}}/>{label&&<EdgeLabelRenderer><span className="loop-edge-label" style={{transform:`translate(-50%, -50%) translate(${lane}px, ${(sourceY+targetY)/2}px)`}}>{label}</span></EdgeLabelRenderer>}</>;
}
