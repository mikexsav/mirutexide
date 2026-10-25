import type { Graph } from './types';
import type { FlowNode } from './flowLayout';
/** Route visual connections without changing canonical CFG labels or targets. */
export function routeConnections(graph:Graph,nodes:FlowNode[]) {
  const byId=new Map(nodes.map(n=>[n.id,n]));
  const minX=Math.min(...nodes.map(n=>n.position.x),0);
  const lanes:Array<Array<[number,number]>>=[];
  return graph.edges.filter(e=>byId.has(e.source)&&byId.has(e.target)).map(e=>{
    const from=byId.get(e.source)!,to=byId.get(e.target)!;
    const backward=e.label==='repeat'||e.label==='continue'||from.position.y>to.position.y+20;
    let laneX:number|undefined;
    if(backward){
      const low=Math.min(from.position.y,to.position.y)-30;
      const high=Math.max(from.position.y+(from.height??96),to.position.y+(to.height??96))+30;
      let lane=lanes.findIndex(intervals=>intervals.every(([a,b])=>high<a||low>b));
      if(lane<0){lane=lanes.length;lanes.push([]);}
      lanes[lane].push([low,high]);laneX=minX-50-lane*28;
    }
    const branch=['false','exhausted','default'].includes(e.label)||(from.data.block.kind==='condition'&&!['true','false'].includes(e.label));
    const leftBranch=branch&&from.data.block.kind!=='loopCondition'&&to.position.x+(to.width??260)/2<from.position.x+(from.width??260)/2;
    return {...e,backward,laneX,sourceHandle:backward?'out-left':branch?(leftBranch?'out-left':'out-right'):'out-bottom',targetHandle:backward?'in-left':'in-top'};
  });
}
