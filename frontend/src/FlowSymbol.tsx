import type { SymbolKind } from './flowSymbols';
export default function FlowSymbol({shape,width=260,height=100}:{shape:SymbolKind;width?:number;height?:number}) {
  const w=width,h=height,p=2;
  return <svg className="symbol-art" aria-hidden="true" viewBox={`0 0 ${w} ${h}`} width={w} height={h}>
    {shape==='decision'?<polygon points={`${w/2},${p} ${w-p},${h/2} ${w/2},${h-p} ${p},${h/2}`}/>:
      shape==='io'?<polygon points={`26,${p} ${w-p},${p} ${w-26},${h-p} ${p},${h-p}`}/>:
      shape==='terminal'?<rect x={p} y={p} width={w-p*2} height={h-p*2} rx={(h-p*2)/2}/>:
      <rect x={p} y={p} width={w-p*2} height={h-p*2} rx={shape==='region'?10:2}/>}
    {shape==='subprocess'&&<path className="symbol-detail" d={`M 14 ${p} V ${h-p} M ${w-14} ${p} V ${h-p}`}/>}
    {shape==='region'&&<path className="symbol-detail" strokeDasharray="5 4" d={`M 12 ${h-13} H ${w-12}`}/>}
  </svg>;
}
