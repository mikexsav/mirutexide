import type { GraphNode } from './types';
import { javaContext } from './javaSuggestions.ts';
export type SymbolKind = 'terminal' | 'decision' | 'io' | 'subprocess' | 'process' | 'region';
export type SymbolSpec = { shape:SymbolKind; title:string; width:number; height:number };
export type SymbolContext = { scanners:Set<string> };
export function symbolContext(source:string):SymbolContext {
  const {masked}=javaContext(source,source.length);
  return {scanners:new Set(Array.from(masked.matchAll(/\bScanner\s+([$\p{L}_][$\p{L}\p{N}_]*)/gu),m=>m[1]))};
}
export function symbolFor(node:GraphNode, context?:SymbolContext):SymbolSpec {
  if(node.kind==='start'||node.kind==='end')return {shape:'terminal',title:node.kind==='start'?'НАЧАЛО':'КОНЕЦ',width:240,height:64};
  if(node.kind==='condition'||node.kind==='loopCondition')return {shape:'decision',title:node.kind==='loopCondition'?'ПРОВЕРКА ЦИКЛА':node.label.startsWith('switch ')?'ВЫБОР SWITCH':'УСЛОВИЕ',width:300,height:176};
  if(node.kind==='exception')return {shape:'region',title:'АТОМАРНЫЙ РЕГИОН',width:280,height:108};
  const code=javaContext(node.code,node.code.length).masked;
  if(/\bSystem\s*\.\s*(out|err)\s*\.\s*(print|println|printf|format|write)\s*\(/.test(code))return {shape:'io',title:'ВЫВОД',width:280,height:100};
  const scannerRead=Array.from(code.matchAll(/([$\p{L}_][$\p{L}\p{N}_]*)\s*\.\s*(next(?:Int|Long|Double|Float|Boolean|Byte|Short|Line)?|hasNext(?:Int|Long|Double|Float|Boolean|Byte|Short|Line)?)\s*\(/gu)).some(m=>context?.scanners.has(m[1]));
  if(/\bSystem\s*\.\s*in\b/.test(code)||scannerRead)return {shape:'io',title:'ВВОД',width:280,height:100};
  if(node.kind==='call')return {shape:'subprocess',title:'ВЫЗОВ МЕТОДА',width:260,height:100};
  return {shape:'process',title:({return:'RETURN',throw:'THROW',break:'BREAK',continue:'CONTINUE'} as Record<string,string>)[node.kind]??'ОПЕРАЦИЯ',width:260,height:96};
}
export function readableEdge(label:string):string {
  return ({true:'Да',false:'Нет',next:'Есть элемент',exhausted:'Завершён',repeat:'Следующий шаг',default:'Иначе','normal completion':'Обычный выход','possible abrupt completion':'Возможный выход'} as Record<string,string>)[label]??label.replace(/^\[(.*)\]$/,'case $1');
}
export const symbolGuide=[
  {shape:'terminal' as const,title:'Начало и конец',description:'Скруглённый блок обозначает границу метода.'},
  {shape:'decision' as const,title:'Условие и проверка цикла',description:'Ромб разветвляет поток. «Да» продолжает ветку или тело цикла, «Нет» ведёт к альтернативе или выходу.'},
  {shape:'io' as const,title:'Ввод и вывод',description:'Параллелограмм показывает System.in, консольный вывод и обращения к объявленному Scanner.'},
  {shape:'process' as const,title:'Операция',description:'Прямоугольник содержит вычисление, присваивание или управляющий оператор.'},
  {shape:'subprocess' as const,title:'Вызов метода',description:'Двойные боковые линии обозначают вызов. Внутреннюю логику другого метода смотрите в карте методов.'},
  {shape:'region' as const,title:'Атомарный регион',description:'Блок с пометкой обозначает сохранённый try/catch/finally или другую конструкцию без подробного внутреннего графа.'},
];
