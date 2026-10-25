import { useEffect, useRef, useState } from "react";
import { ArrowUpRight, Search, X } from "lucide-react";
import { filterCommands, type SearchableCommand } from "./commandSearch";
export type CommandItem = SearchableCommand & { run: () => void };
export default function CommandPalette({ items, close }: { items: CommandItem[]; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const results = filterCommands(items, query);
  const selected = Math.min(active, Math.max(0, results.length - 1));
  useEffect(() => { dialog.current?.showModal(); }, []);
  useEffect(() => { dialog.current?.querySelector(`[data-active="true"]`)?.scrollIntoView({ block: "nearest" }); }, [selected]);
  function choose(item: CommandItem) { close(); item.run(); }
  return <dialog ref={dialog} className="command-dialog" onCancel={close} aria-label="Палитра команд" onClick={e => { if(e.target === e.currentTarget) close(); }}>
    <div className="command-input"><Search size={20}/><input autoFocus aria-label="Поиск команд и методов" placeholder="Действие, метод или фрагмент кода…" value={query} onChange={e=>{setQuery(e.target.value);setActive(0);}} onKeyDown={e=>{
      if(e.key === "ArrowDown") {e.preventDefault();setActive((selected+1)%Math.max(1,results.length));}
      if(e.key === "ArrowUp") {e.preventDefault();setActive((selected+Math.max(1,results.length)-1)%Math.max(1,results.length));}
      if(e.key === "Enter") {e.preventDefault();if(results[selected])choose(results[selected]);}
    }}/><button title="Закрыть палитру" onClick={close}><X size={18}/></button></div>
    <div className="command-results" role="group" aria-label="Результаты поиска">{results.length ? results.map((item,i)=><button data-active={i===selected} key={item.id} onMouseEnter={()=>setActive(i)} onClick={()=>choose(item)}><span><strong>{item.title}</strong><small>{item.detail}</small></span><ArrowUpRight size={16}/></button>):<p className="command-empty">Ничего не найдено. Попробуйте имя метода, строку или действие.</p>}</div>
    <footer><span>↑ ↓ выбрать · Enter открыть</span><span>Esc закрыть</span></footer>
  </dialog>;
}
