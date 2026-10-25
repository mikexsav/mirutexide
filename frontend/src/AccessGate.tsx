import { useState } from "react";
import { ArrowRight, ShieldCheck, Sparkles } from "lucide-react";
import App from "./App";
import "./Experience.css";

export default function AccessGate() {
  const [entered, setEntered] = useState(() => location.pathname.startsWith("/admin") || (location.pathname.startsWith("/demo") && sessionStorage.getItem("mirutex.user") === "user"));
  const [login, setLogin] = useState("user");
  const [error, setError] = useState("");
  const demoMode = !location.pathname.startsWith("/admin");
  if (entered) return <App demoMode={demoMode} onExit={() => { sessionStorage.removeItem("mirutex.user"); location.assign("/"); }} />;
  return <main className="welcome">
    <section className="welcome-story">
      <img src="/mirutex.png" alt="Mirutex VMO" />
      <span className="welcome-label">MIRUTEX VMO / JAVA WORKSPACE</span>
      <h1>Код, который<br />можно увидеть.</h1>
      <p>Исследуйте Java как граф. Находите сложные участки и проверяйте изменения.</p>
      <div className="welcome-features"><span><Sparkles size={18} /> Java assist с Tab</span><span>Палитра команд</span><span>Карта методов</span></div>
    </section>
    <section className="welcome-entry">
      <span className="welcome-label">MIRUTEX IDE</span><h2>Начнём с кода</h2>
      <p>Демо доступно без пароля. Все проекты сохраняются в вашем браузере.</p>
      <form onSubmit={e => { e.preventDefault(); if(login.trim().toLowerCase() !== "user") { setError("Для демо используйте логин user."); return; } sessionStorage.setItem("mirutex.user", "user"); history.replaceState(null,"","/demo/"); setEntered(true); }}>
        <label htmlFor="demo-login">Логин</label><input id="demo-login" autoComplete="username" value={login} onChange={e=>{setLogin(e.target.value);setError("");}} aria-describedby="login-hint" />
        <small id="login-hint">user · пароль не нужен</small>
        {error && <p className="login-error" role="alert">{error}</p>}
        <button className="welcome-submit" type="submit">Войти в демо <ArrowRight size={18} /></button>
      </form>
      <div className="welcome-scope"><ShieldCheck size={18}/><p>Редактор, граф, анализ и экспорт доступны всем. Компиляция и выполнение Java требуют защищённого входа.</p></div>
      <a className="admin-link" href="/admin/">Защищённое рабочее пространство <ArrowRight size={14}/></a>
    </section>
  </main>;
}
