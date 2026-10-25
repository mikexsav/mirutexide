import { useCallback, useEffect, useRef, useState } from "react";
import {
  Archive,
  ArrowLeft,
  ArrowRight,
  Check,
  Cloud,
  Copy,
  FolderOpen,
  LoaderCircle,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  Share2,
} from "lucide-react";
import App from "./App";
import StudioDialog from "./StudioDialog";
import { api, ApiError, setCsrfToken } from "./types";
import {
  errorMessage,
  newState,
  restoreLocal,
  templates,
  type Account,
  type Share,
  type Workspace,
  type WorkspaceState,
  type WorkspaceSummary,
} from "./workspaces";
import "./Cloud.css";

const stamp = (seconds: number) =>
  new Date(seconds * 1000).toLocaleString("ru-RU", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
type Status = "saved" | "dirty" | "saving" | "error" | "conflict";
const statusText: Record<Status, string> = {
  saved: "В облаке",
  dirty: "Есть изменения",
  saving: "Сохраняем…",
  error: "Нет синхронизации",
  conflict: "Конфликт версий",
};

export default function WorkspaceShell({
  account,
  onLogout,
}: {
  account: Account;
  onLogout: () => void;
}) {
  const [spaces, setSpaces] = useState<WorkspaceSummary[]>([]);
  const [active, setActive] = useState<Workspace | null>(null);
  const activeRef = useRef<Workspace | null>(null);
  const pending = useRef<WorkspaceState | null>(null);
  const saved = useRef("");
  const inFlight = useRef<Promise<void> | null>(null);
  const statusRef = useRef<Status>("saved");
  const [status, setStatusValue] = useState<Status>("saved");
  const [tick, setTick] = useState(0);
  const [nonce, setNonce] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [authLost, setAuthLost] = useState(false);
  const [shareNow, setShareNow] = useState(() => Date.now() / 1000);
  const [query, setQuery] = useState("");
  const [archive, setArchive] = useState(false);
  const [dialog, setDialog] = useState<"create" | "edit" | "share" | "">("");
  const [editing, setEditing] = useState<WorkspaceSummary | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [template, setTemplate] = useState("demo");
  const [shares, setShares] = useState<Share[]>([]);
  const [shareUrl, setShareUrl] = useState("");
  const [copied, setCopied] = useState(false);
  const draftKey = useCallback(
    (id: string) => `mirutex.draft.${account.id}.${id}`,
    [account.id],
  );
  const setStatus = useCallback((value: Status) => {
    statusRef.current = value;
    setStatusValue(value);
  }, []);

  const refresh = useCallback(async () => {
    const data = await api<{ workspaces: WorkspaceSummary[] }>(
      "/api/workspaces",
    );
    setSpaces(data.workspaces);
  }, []);
  useEffect(() => {
    void refresh()
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [refresh]);

  const save = useCallback(async () => {
    if (inFlight.current) return inFlight.current;
    if (
      !activeRef.current ||
      !pending.current ||
      JSON.stringify(pending.current) === saved.current
    )
      return;
    if (statusRef.current === "conflict")
      throw new Error("Выберите серверную версию или сохраните свою копией.");
    const job = (async () => {
      try {
        while (
          activeRef.current &&
          pending.current &&
          JSON.stringify(pending.current) !== saved.current
        ) {
          const w = activeRef.current;
          const state = pending.current;
          setStatus("saving");
          const result = await api<Workspace>(
            `/api/workspaces/${w.id}`,
            { version: w.version, state },
            "PUT",
          );
          activeRef.current = result;
          saved.current = JSON.stringify(state);
          setActive((old) =>
            old?.id === result.id ? { ...result, state: old.state } : old,
          );
          if (JSON.stringify(pending.current) === saved.current)
            localStorage.removeItem(draftKey(w.id));
          else
            localStorage.setItem(
              draftKey(w.id),
              JSON.stringify({
                version: result.version,
                state: pending.current,
              }),
            );
        }
        setStatus("saved");
        setError("");
      } catch (e) {
        setStatus(
          e instanceof ApiError && e.status === 409 ? "conflict" : "error",
        );
        if (e instanceof ApiError && [401, 403].includes(e.status))
          setAuthLost(true);
        setError(errorMessage(e));
        throw e;
      }
    })();
    inFlight.current = job;
    try {
      await job;
    } finally {
      inFlight.current = null;
    }
  }, [draftKey, setStatus]);

  const onStateChange = useCallback(
    (state: WorkspaceState) => {
      const w = activeRef.current;
      if (!w) return;
      pending.current = state;
      if (JSON.stringify(state) !== saved.current) {
        try {
          localStorage.setItem(
            draftKey(w.id),
            JSON.stringify({ version: w.version, state }),
          );
        } catch {
          setError(
            "Браузер не сохранил резервную копию. Скачайте проект до закрытия вкладки.",
          );
        }
        if (statusRef.current !== "conflict") setStatus("dirty");
        setTick((n) => n + 1);
      }
    },
    [draftKey, setStatus],
  );
  useEffect(() => {
    if (statusRef.current === "conflict" || statusRef.current === "error")
      return;
    const id = setTimeout(() => {
      void save().catch(() => {});
    }, 1100);
    return () => clearTimeout(id);
  }, [tick, save]);
  useEffect(() => {
    const before = (e: BeforeUnloadEvent) => {
      if (
        pending.current &&
        JSON.stringify(pending.current) !== saved.current
      ) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    const online = () => {
      if (statusRef.current !== "conflict") void save().catch(() => {});
    };
    window.addEventListener("beforeunload", before);
    window.addEventListener("online", online);
    return () => {
      window.removeEventListener("beforeunload", before);
      window.removeEventListener("online", online);
    };
  }, [save]);

  async function operation(fn: () => Promise<void>) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await fn();
    } catch (e) {
      if (e instanceof ApiError && [401, 403].includes(e.status))
        setAuthLost(true);
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  function activate(w: Workspace) {
    saved.current = JSON.stringify(w.state);
    let state = w.state;
    let conflict = false;
    try {
      const draft = JSON.parse(localStorage.getItem(draftKey(w.id)) ?? "null");
      if (
        typeof draft?.state?.project?.source === "string" &&
        JSON.stringify(draft.state) !== saved.current
      ) {
        state = draft.state;
        conflict = draft.version !== w.version;
      }
    } catch {
      /* Ignore corrupt browser copies; the server is authoritative. */
    }
    activeRef.current = w;
    pending.current = state;
    setActive({ ...w, state });
    setNonce((n) => n + 1);
    setStatus(
      conflict
        ? "conflict"
        : JSON.stringify(state) === saved.current
          ? "saved"
          : "dirty",
    );
    if (conflict)
      setError(
        "Нашли локальный черновик и более новую серверную версию. Выберите, какую продолжить.",
      );
    setTick((n) => n + 1);
  }
  async function open(id: string) {
    await operation(async () => {
      await save();
      activate(await api<Workspace>(`/api/workspaces/${id}`));
    });
  }
  async function back() {
    await operation(async () => {
      await save();
      activeRef.current = null;
      pending.current = null;
      setActive(null);
      await refresh();
    });
  }
  async function logout() {
    await operation(async () => {
      await save();
      await api("/api/auth/logout", {});
      setCsrfToken(null);
      onLogout();
    });
  }
  async function changeMetadata(
    w: WorkspaceSummary,
    data: Record<string, unknown>,
  ) {
    await operation(async () => {
      await api(
        `/api/workspaces/${w.id}`,
        { version: w.version, ...data },
        "PUT",
      );
      await refresh();
      setDialog("");
    });
  }
  async function duplicate(w: WorkspaceSummary) {
    await operation(async () => {
      const original = await api<Workspace>(`/api/workspaces/${w.id}`);
      await api("/api/workspaces", {
        name: `${w.name.slice(0, 65)} · копия`,
        description: w.description,
        state: original.state,
      });
      await refresh();
    });
  }
  async function saveCopy() {
    await operation(async () => {
      if (!pending.current || !activeRef.current) return;
      const w = activeRef.current;
      const copy = await api<Workspace>("/api/workspaces", {
        name: `${w.name.slice(0, 60)} · моя версия`,
        description: w.description,
        state: pending.current,
      });
      localStorage.removeItem(draftKey(w.id));
      activate(copy);
      setError("");
    });
  }
  async function serverVersion() {
    await operation(async () => {
      if (!activeRef.current) return;
      const w = await api<Workspace>(`/api/workspaces/${activeRef.current.id}`);
      localStorage.removeItem(draftKey(w.id));
      activate(w);
      setError("");
    });
  }
  async function showShares() {
    await operation(async () => {
      await save();
      if (!activeRef.current) return;
      setShares(
        (
          await api<{ shares: Share[] }>(
            `/api/workspaces/${activeRef.current.id}/shares`,
          )
        ).shares,
      );
      setShareUrl("");
      setCopied(false);
      setShareNow(Date.now() / 1000);
      setDialog("share");
    });
  }

  const feedback = error && (
    <div className="cloud-alert" role="alert">
      <span>{error}</span>
      {authLost ? (
        <button onClick={onLogout}>
          Вернуться к входу · черновик сохранён в браузере
        </button>
      ) : (
        active && (
          <button
            onClick={() => void save().catch(() => {})}
            disabled={busy || status === "conflict"}
          >
            Повторить сохранение
          </button>
        )
      )}
    </div>
  );
  if (active)
    return (
      <>
        <div className="cloud-ribbon" data-status={status}>
          <button onClick={back} disabled={busy}>
            <ArrowLeft size={15} />
            Мои пространства
          </button>
          <span>
            <Cloud size={15} />
            <strong>{active.name}</strong>
            <small>
              v{active.version} · {statusText[status]}
            </small>
          </span>
          <button onClick={showShares} disabled={busy || status === "conflict"}>
            <Share2 size={15} />
            Поделиться
          </button>
        </div>
        {feedback}
        {status === "conflict" && (
          <div className="cloud-conflict">
            <p>
              Автосохранение приостановлено. Серверная версия сохранена, ваш
              черновик остаётся в этой вкладке.
            </p>
            <button
              className="button primary"
              disabled={busy}
              onClick={saveCopy}
            >
              Сохранить мою версию копией
            </button>
            <button className="button" disabled={busy} onClick={serverVersion}>
              Открыть серверную версию
            </button>
          </div>
        )}
        <App
          key={`${active.id}:${nonce}`}
          account={account}
          workspaceName={active.name}
          initialState={active.state}
          storageKey={`mirutex.cloud.${account.id}.${active.id}`}
          onStateChange={onStateChange}
          onOpenSpaces={back}
          onExit={logout}
          onShare={showShares}
          onSaveNow={() => void save().catch(() => {})}
          syncStatus={statusText[status]}
        />
        {dialog === "share" && (
          <StudioDialog title="Поделиться снимком" close={() => setDialog("")}>
            <p className="dialog-description">
              Ссылка открывает текущий код и граф только для просмотра. История,
              исходная версия сравнения и тестовые входы останутся приватными.
              Изменения после публикации не попадут в этот снимок. Срок ссылки —
              7 дней.
            </p>
            {shareUrl ? (
              <div className="share-link">
                <input
                  aria-label="Ссылка на снимок"
                  readOnly
                  value={shareUrl}
                  onFocus={(e) => e.target.select()}
                />
                <button
                  className="button"
                  onClick={() =>
                    void operation(async () => {
                      await navigator.clipboard.writeText(shareUrl);
                      setCopied(true);
                    })
                  }
                >
                  {copied ? <Check size={16} /> : <Copy size={16} />}{" "}
                  {copied ? "Скопировано" : "Копировать"}
                </button>
                <a
                  className="button"
                  href={shareUrl}
                  target="_blank"
                  rel="noreferrer"
                >
                  Открыть
                </a>
              </div>
            ) : (
              <button
                className="button primary"
                disabled={busy}
                onClick={() =>
                  void operation(async () => {
                    await save();
                    const w = activeRef.current!;
                    const r = await api<{ token: string; id: string }>(
                      `/api/workspaces/${w.id}/shares`,
                      { version: w.version },
                    );
                    setShareUrl(`${location.origin}/share/${r.token}`);
                    setShares(
                      (
                        await api<{ shares: Share[] }>(
                          `/api/workspaces/${w.id}/shares`,
                        )
                      ).shares,
                    );
                  })
                }
              >
                <Share2 size={16} />
                Создать ссылку на этот снимок
              </button>
            )}
            <h3>Опубликованные снимки</h3>
            <div className="share-list">
              {shares
                .filter((s) => !s.revoked && s.expires_at > shareNow)
                .map((s) => (
                  <div key={s.id}>
                    <span>
                      Версия {s.version}
                      <small>до {stamp(s.expires_at)}</small>
                    </span>
                    <button
                      className="button"
                      disabled={busy}
                      onClick={() =>
                        void operation(async () => {
                          await api(
                            `/api/workspaces/${active.id}/shares/${s.id}`,
                            undefined,
                            "DELETE",
                          );
                          setShares((old) =>
                            old.map((v) =>
                              v.id === s.id ? { ...v, revoked: 1 } : v,
                            ),
                          );
                          setShareUrl("");
                        })
                      }
                    >
                      Отозвать
                    </button>
                  </div>
                ))}
            </div>
            {!shares.some((s) => !s.revoked && s.expires_at > shareNow) && (
              <p className="dialog-description">Действующих ссылок пока нет.</p>
            )}
            {error && (
              <p className="login-error" role="alert">
                {error}
              </p>
            )}
          </StudioDialog>
        )}
      </>
    );

  const filtered = spaces.filter(
    (w) =>
      w.archived === archive &&
      `${w.name} ${w.description} ${w.filename}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <main className="cloud-home">
      <header className="cloud-top">
        <div className="cloud-brand">
          <img src="/mirutex.png" alt="Mirutex VMO" />
          <span>
            Mirutex <b>IDE</b>
          </span>
        </div>
        <div>
          <span className="cloud-account">
            {account.display_name}
            <small>@{account.username}</small>
          </span>
          <button className="button" disabled={busy} onClick={logout}>
            <LogOut size={15} />
            Выйти
          </button>
        </div>
      </header>
      <section className="cloud-intro">
        <div>
          <span className="cloud-kicker">ВАШЕ РАБОЧЕЕ ПРОСТРАНСТВО</span>
          <h1>Код остаётся с вами.</h1>
          <p>
            Личные Java-проекты, снимки и тестовые входы сохраняются на сервере.
            Продолжайте с другого устройства.
          </p>
        </div>
        <button
          className="button primary"
          onClick={() => {
            setName("");
            setDescription("");
            setTemplate("demo");
            setDialog("create");
          }}
        >
          <Plus size={18} />
          Новое пространство
        </button>
      </section>
      {feedback}
      <div className="cloud-tools">
        <div className="cloud-tabs">
          <button aria-pressed={!archive} onClick={() => setArchive(false)}>
            <FolderOpen size={16} />
            Активные <span>{spaces.filter((w) => !w.archived).length}</span>
          </button>
          <button aria-pressed={archive} onClick={() => setArchive(true)}>
            <Archive size={16} />
            Архив <span>{spaces.filter((w) => w.archived).length}</span>
          </button>
        </div>
        <label className="cloud-search">
          <Search size={17} />
          <input
            aria-label="Найти пространство"
            placeholder="Найти проект…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
        <button
          className="icon-button"
          aria-label="Обновить список"
          disabled={busy}
          onClick={() => void operation(refresh)}
        >
          <RefreshCw size={17} />
        </button>
      </div>
      {loading ? (
        <div className="cloud-empty">
          <LoaderCircle className="spin" />
          Загружаем пространства…
        </div>
      ) : (
        <section className="space-grid" aria-label="Ваши кодовые пространства">
          {filtered.map((w) => (
            <article className="space-card" key={w.id}>
              <div className="space-heading">
                <div className="space-mark">
                  <FolderOpen size={23} />
                </div>
                <span>
                  Java 21 <small>v{w.version}</small>
                </span>
              </div>
              <button
                className="space-title"
                disabled={busy}
                onClick={() => open(w.id)}
              >
                <h2>{w.name}</h2>
                <ArrowRight size={19} />
              </button>
              <p>{w.description || w.filename}</p>
              <div className="space-stats">
                <span>Файлов: {w.files}</span>
                <span>Строк: {w.lines.toLocaleString("ru-RU")}</span>
                <span>Снимков: {w.snapshots}</span>
              </div>
              <small className="space-time">
                Изменено {stamp(w.updated_at)}
              </small>
              <div className="space-actions">
                <button
                  className="button primary"
                  disabled={busy}
                  onClick={() => open(w.id)}
                >
                  Открыть
                </button>
                <button
                  className="icon-button"
                  aria-label={`Настройки ${w.name}`}
                  disabled={busy}
                  onClick={() => {
                    setEditing(w);
                    setName(w.name);
                    setDescription(w.description);
                    setDialog("edit");
                  }}
                >
                  <Settings2 size={17} />
                </button>
                <button
                  className="icon-button"
                  aria-label={`Создать копию ${w.name}`}
                  disabled={busy}
                  onClick={() => duplicate(w)}
                >
                  <Copy size={17} />
                </button>
                <button
                  className="icon-button"
                  aria-label={
                    w.archived ? `Восстановить ${w.name}` : `В архив ${w.name}`
                  }
                  disabled={busy}
                  onClick={() => changeMetadata(w, { archived: !w.archived })}
                >
                  {w.archived ? <RefreshCw size={17} /> : <Archive size={17} />}
                </button>
              </div>
            </article>
          ))}
        </section>
      )}
      {!loading && !filtered.length && (
        <div className="cloud-empty">
          <FolderOpen size={42} />
          <h2>
            {query
              ? "По этому запросу ничего нет"
              : archive
                ? "Архив пуст"
                : "Первое пространство начинается с идеи"}
          </h2>
          <p>
            {query
              ? "Измените запрос или переключите раздел."
              : archive
                ? "Архивные проекты можно восстановить в любой момент."
                : "Выберите пример или начните с чистого Java-файла."}
          </p>
          {!query && !archive && (
            <button
              className="button primary"
              onClick={() => {
                setName("Мой первый проект");
                setTemplate("flow");
                setDescription("");
                setDialog("create");
              }}
            >
              <Plus size={16} />
              Создать пространство
            </button>
          )}
        </div>
      )}
      <footer className="cloud-footer">
        <Cloud size={16} /> Серверное автосохранение{" "}
        <span>Приватно для вашего аккаунта</span>
        <span>История и ссылки на снимки</span>
      </footer>
      {(dialog === "create" || dialog === "edit") && (
        <StudioDialog
          title={
            dialog === "create"
              ? "Новое кодовое пространство"
              : "Настройки пространства"
          }
          close={() => setDialog("")}
        >
          <form
            className="space-form"
            onSubmit={(e) => {
              e.preventDefault();
              if (dialog === "edit" && editing)
                void changeMetadata(editing, { name, description });
              else
                void operation(async () => {
                  const chosen = templates.find((t) => t.id === template)!;
                  const state =
                    template === "local"
                      ? restoreLocal()
                      : newState(chosen.project);
                  const w = await api<Workspace>("/api/workspaces", {
                    name: name.trim(),
                    description,
                    state,
                  });
                  setDialog("");
                  activate(w);
                });
            }}
          >
            <label>
              Название
              <input
                required
                maxLength={80}
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoFocus
                placeholder="Например, Разбор монолита"
              />
            </label>
            <label>
              Описание
              <textarea
                maxLength={500}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Что исследуем или улучшаем?"
              />
            </label>
            {dialog === "create" && (
              <fieldset>
                <legend>С чего начнём</legend>
                {templates.map((t) => (
                  <label className="template-choice" key={t.id}>
                    <input
                      type="radio"
                      name="template"
                      checked={template === t.id}
                      onChange={() => setTemplate(t.id)}
                    />
                    <span>
                      <strong>{t.name}</strong>
                      <small>{t.description}</small>
                    </span>
                  </label>
                ))}
                <label className="template-choice">
                  <input
                    type="radio"
                    name="template"
                    checked={template === "local"}
                    onChange={() => setTemplate("local")}
                  />
                  <span>
                    <strong>Импорт из этого браузера</strong>
                    <small>Перенести локальный демо-проект и его снимки</small>
                  </span>
                </label>
              </fieldset>
            )}
            <button
              className="button primary"
              disabled={busy || !name.trim()}
              type="submit"
            >
              {busy ? (
                <LoaderCircle size={16} className="spin" />
              ) : (
                <Plus size={16} />
              )}{" "}
              {dialog === "create"
                ? "Создать и открыть"
                : "Сохранить настройки"}
            </button>
            {error && (
              <p className="login-error" role="alert">
                {error}
              </p>
            )}
          </form>
        </StudioDialog>
      )}
    </main>
  );
}
