import { lazy, useEffect, useState, type FormEvent } from "react";
import {
  ArrowRight,
  Cloud,
  Eye,
  LoaderCircle,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
const App = lazy(() => import("./App"));
const WorkspaceShell = lazy(() => import("./WorkspaceShell"));
const SharedWorkspace = lazy(() => import("./SharedWorkspace"));
import { api, setCsrfToken } from "./types";
import { errorMessage, type Session } from "./workspaces";
import "./Experience.css";
import "./Cloud.css";

export default function AccessGate() {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [demo, setDemo] = useState(
    () =>
      location.pathname.startsWith("/demo") &&
      sessionStorage.getItem("mirutex.user") === "user",
  );
  const [mode, setMode] = useState<"login" | "register">("login");
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const shareToken = location.pathname.startsWith("/share/")
    ? location.pathname.split("/")[2]
    : "";
  async function checkSession() {
    setLoading(true);
    setError("");
    try {
      const s = await api<Session>("/api/auth/session");
      setCsrfToken(s.csrf_token);
      setSession(s);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void checkSession();
  }, []);
  function enterDemo() {
    sessionStorage.setItem("mirutex.user", "user");
    history.replaceState(null, "", "/demo/");
    setDemo(true);
  }
  if (shareToken) return <SharedWorkspace token={shareToken} />;
  if (loading)
    return (
      <main className="welcome-loading">
        <LoaderCircle className="spin" size={28} />
        <p>Mirutex IDE</p>
      </main>
    );
  if (demo)
    return (
      <App
        demoMode
        onExit={() => {
          sessionStorage.removeItem("mirutex.user");
          history.replaceState(null, "", "/");
          setDemo(false);
        }}
      />
    );
  if (session?.user)
    return (
      <WorkspaceShell
        account={session.user}
        onLogout={() => {
          setSession({ ...session, user: null, csrf_token: null });
          history.replaceState(null, "", "/");
        }}
      />
    );
  if (
    location.pathname.startsWith("/admin") &&
    session &&
    !session.execution_requires_account
  )
    return (
      <App
        onExit={() => {
          history.replaceState(null, "", "/");
          setSession(null);
        }}
      />
    );
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (
      mode === "login" &&
      login.trim().toLowerCase() === "user" &&
      !password
    ) {
      enterDemo();
      return;
    }
    setBusy(true);
    setError("");
    try {
      const s = await api<Session>(`/api/auth/${mode}`, {
        username: login.trim(),
        password,
        ...(mode === "register" ? { display_name: displayName.trim() } : {}),
      });
      setCsrfToken(s.csrf_token);
      setPassword("");
      setSession(s);
      history.replaceState(null, "", "/");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="welcome">
      <section className="welcome-story">
        <img src="/mirutex.png" alt="Mirutex VMO" />
        <span className="welcome-label">MIRUTEX VMO / JAVA WORKSPACE</span>
        <h1>
          Ваш код.
          <br />
          Ваше пространство.
        </h1>
        <p>
          Исследуйте Java как граф, проверяйте изменения и продолжайте работу с
          любого устройства.
        </p>
        <div className="welcome-features">
          <span>
            <Cloud size={18} />
            Пространства в облаке
          </span>
          <span>
            <Sparkles size={18} />
            Java assist с Tab
          </span>
          <span>
            <Eye size={18} />
            Снимки по ссылке
          </span>
        </div>
      </section>
      <section className="welcome-entry">
        <span className="welcome-label">MIRUTEX IDE</span>
        <div className="auth-tabs">
          <button
            aria-pressed={mode === "login"}
            onClick={() => {
              setMode("login");
              setError("");
            }}
          >
            Вход
          </button>
          <button
            aria-pressed={mode === "register"}
            onClick={() => {
              setMode("register");
              setError("");
            }}
          >
            Регистрация
          </button>
        </div>
        <h2>{mode === "login" ? "Вернёмся к коду" : "Начнём с идеи"}</h2>
        <p>
          {mode === "login"
            ? "Войдите, чтобы открыть личные проекты, историю и результаты работы."
            : "Создайте аккаунт и первое пространство. Почта не требуется."}
        </p>
        <form onSubmit={submit}>
          {mode === "register" && (
            <label>
              Ваше имя
              <input
                autoComplete="nickname"
                required
                maxLength={60}
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
              />
            </label>
          )}
          <label htmlFor="account-login">
            Логин
            <input
              id="account-login"
              autoComplete="username"
              required
              minLength={3}
              maxLength={32}
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              placeholder="Например, mirutex_dev"
            />
          </label>
          <label htmlFor="account-password">
            Пароль
            <input
              id="account-password"
              type="password"
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              required={mode === "register" || login.toLowerCase() !== "user"}
              minLength={mode === "register" ? 10 : undefined}
              maxLength={128}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <small>
            {mode === "register"
              ? "Не менее 10 символов. Сохраните пароль: восстановление по почте пока не подключено."
              : "Для демо user пароль не нужен."}
          </small>
          {error && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}
          <button className="welcome-submit" disabled={busy} type="submit">
            {busy
              ? "Подождите…"
              : mode === "login"
                ? "Войти в аккаунт"
                : "Создать аккаунт"}
            {busy ? (
              <LoaderCircle className="spin" size={18} />
            ) : (
              <ArrowRight size={18} />
            )}
          </button>
        </form>
        <button className="demo-entry" onClick={enterDemo} disabled={busy}>
          <Eye size={17} />
          Открыть демо user без пароля
          <ArrowRight size={15} />
        </button>
        <div className="welcome-scope">
          <ShieldCheck size={18} />
          <p>
            Пространства приватны. Аккаунт открывает сохранение на сервере и
            ограниченные запуски Java. В демо доступны редактор, граф, анализ и
            экспорт.
          </p>
        </div>
        {!session && (
          <button className="admin-link" onClick={() => void checkSession()}>
            Проверить соединение
          </button>
        )}
      </section>
    </main>
  );
}
