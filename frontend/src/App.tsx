import { useCallback, useEffect, useRef, useState } from "react";
import Editor, { DiffEditor } from "@monaco-editor/react";
import type { editor } from "monaco-editor";
import {
  Search,
  Sparkles,
  Focus,
  LogOut,
  GitCompareArrows,
  Map,
  HelpCircle,
  Braces,
  GitBranch,
  FlaskConical,
  FileCode2,
  Upload,
  Download,
  Play,
  Sun,
  Moon,
  RotateCcw,
  ArrowRight,
  Check,
  AlertTriangle,
  Maximize2,
  Minimize2,
  ScanLine,
  X,
  Command,
  ChevronRight,
  History,
  LoaderCircle,
  PanelLeftClose,
} from "lucide-react";
import "./monaco";
import Flowchart, { type GraphFocus } from "./Flowchart";
import {
  api,
  type Graph,
  type GraphNode,
  type Issue,
  type Run,
  type Comparison,
  type Preview,
} from "./types";
import {
  fresh,
  restoreLocal,
  type ProjectData,
  type Snapshot,
  type WorkspaceState,
  type Account,
} from "./workspaces";
import { downloadPassport } from "./qualityPassport";
import "./App.css";
import "./Experience.css";
import CommandPalette, { type CommandItem } from "./CommandPalette";
import StudioDialog from "./StudioDialog";
import FlowSymbol from "./FlowSymbol";
import { symbolGuide } from "./flowSymbols";

type AppProps = {
  demoMode?: boolean;
  onExit?: () => void;
  initialState?: WorkspaceState;
  storageKey?: string;
  onStateChange?: (state: WorkspaceState) => void;
  workspaceName?: string;
  account?: Account;
  onOpenSpaces?: () => void;
  onShare?: () => void;
  syncStatus?: string;
  onSaveNow?: () => void;
};
function App({
  demoMode = false,
  onExit,
  initialState,
  storageKey = "mirutex.project",
  onStateChange,
  workspaceName,
  account,
  onOpenSpaces,
  onShare,
  syncStatus,
  onSaveNow,
}: AppProps) {
  const [initial] = useState(() => initialState ?? restoreLocal(storageKey));
  const [palette, setPalette] = useState(false);
  const [dialog, setDialog] = useState("");
  const [component, setComponent] = useState("");
  const [focusMode, setFocusMode] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [methodQuery, setMethodQuery] = useState("");
  const [dismissedParseError, setDismissedParseError] = useState("");
  const [project, setProject] = useState<ProjectData>(initial.project);
  const [baseline, setBaseline] = useState<ProjectData>(initial.baseline);
  const [history, setHistory] = useState<Snapshot[]>(initial.history);
  const [graph, setGraph] = useState<Graph | null>(null);
  const [method, setMethod] = useState("");
  const [selected, setSelected] = useState("");
  const [graphFocus, setGraphFocus] = useState<GraphFocus>(null);
  const focusVersion = useRef(0);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [analyzed, setAnalyzed] = useState(false);
  const [issueLimit, setIssueLimit] = useState(100);
  const [preview, setPreview] = useState<Preview | null>(null);
  const [run, setRun] = useState<Run | null>(null);
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [tab, setTab] = useState("workspace");
  const [bottomTab, setBottomTab] = useState("diagnostics");
  const [busy, setBusy] = useState("");
  const busyRef = useRef(false);
  const [parsing, setParsing] = useState(false);
  const [parseError, setParseError] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dark, setDark] = useState(
    localStorage.getItem("mirutex.theme") === "dark",
  );
  const [fullscreen, setFullscreen] = useState(false);
  const [layout, setLayout] = useState(0);
  const [draft, setDraft] = useState("");
  const [editType, setEditType] = useState("replace");
  const [inputs, setInputs] = useState(initial.inputs);
  useEffect(() => {
    setRun(null);
    setComparison(null);
    setPreview(null);
  }, [project.files, baseline]);
  useEffect(() => {
    try {
      if (!account) localStorage.setItem("mirutex.inputs", inputs);
    } catch {}
    setComparison(null);
  }, [inputs, account]);
  const [width, setWidth] = useState(46);
  const split = useRef<HTMLDivElement>(null);
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const monacoRef = useRef<typeof import("monaco-editor") | null>(null);
  const uploadRef = useRef<HTMLInputElement>(null);
  const graphBox = useRef<HTMLDivElement>(null);
  const revision = useRef(0);
  const graphRef = useRef(graph);
  useEffect(() => {
    graphRef.current = graph;
  }, [graph]);
  const source = project.source;
  const files = project.files;
  const node = graph?.nodes.find((n) => n.id === selected);
  const tests = () => inputs.split(/\r?\n---\r?\n/);
  const current = useRef(project);
  useEffect(() => {
    current.current = project;
  }, [project]);
  useEffect(() => {
    try {
      localStorage.setItem(
        storageKey,
        JSON.stringify({
          project,
          baseline,
          history: history.slice(-20),
          inputs,
        }),
      );
    } catch {
      setNotice("Недостаточно места для автосохранения. Скачайте код.");
    }
    onStateChange?.({ project, baseline, history: history.slice(-20), inputs });
  }, [project, baseline, history, inputs, storageKey, onStateChange]);
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    localStorage.setItem("mirutex.theme", dark ? "dark" : "light");
  }, [dark]);
  useEffect(() => {
    const id = ++revision.current;
    setSelected("");
    setGraphFocus(null);
    setParsing(true);
    setParseError("");
    setDismissedParseError("");
    setIssues([]);
    setAnalyzed(false);
    setPreview(null);
    setComparison(null);
    setRun(null);
    const timer = setTimeout(
      () =>
        api<Graph>("/api/graph/parse", { source })
          .then((g) => {
            if (id !== revision.current) return;
            setGraph(g);
            setMethod((m) =>
              g.methods.some((x) => x.id === m) ? m : (g.methods[0]?.id ?? ""),
            );
            setParsing(false);
          })
          .catch((e) => {
            if (id === revision.current) {
              setParseError(e.message);
              setParsing(false);
            }
          }),
      650,
    );
    return () => {
      clearTimeout(timer);
      revision.current++;
    };
  }, [source]);
  useEffect(() => {
    if (node) {
      setDraft(node.code);
      setEditType("replace");
    }
  }, [node?.id, node?.code]);
  useEffect(() => {
    const model = editorRef.current?.getModel();
    if (model && !model.isDisposed() && monacoRef.current) {
      monacoRef.current.editor.setModelMarkers(
        model,
        "mirutex",
        issues.map((i) => ({
          startLineNumber: i.line,
          endLineNumber: i.line,
          startColumn: 1,
          endColumn: 100,
          message: i.message + "\n" + i.recommendation,
          severity: i.severity === "HIGH" ? 8 : i.severity === "MEDIUM" ? 4 : 2,
        })),
      );
    }
  }, [issues]);
  const remember = (label: string) =>
    setHistory((h) => [
      ...h.slice(-19),
      {
        ...current.current,
        label,
        baseline: {
          source: baseline.source,
          files: baseline.files,
          filename: baseline.filename,
          label: baseline.label,
        },
      },
    ]);
  async function action(label: string, fn: () => Promise<void>) {
    if (
      demoMode &&
      [
        "Компиляция",
        "Проверка поведения",
        "Рефакторинг и тесты",
        "Изменение графа",
        "Выполнение",
      ].includes(label)
    ) {
      setDialog("access");
      return;
    }
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(label);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      busyRef.current = false;
      setBusy("");
    }
  }
  function select(n: GraphNode, focusGraph = true) {
    setGraphFocus(
      focusGraph ? { id: n.id, version: ++focusVersion.current } : null,
    );
    setSelected(n.id);
    setMethod(n.method);
    editorRef.current?.revealLineInCenter(n.line);
    editorRef.current?.setSelection({
      startLineNumber: n.line,
      startColumn: 1,
      endLineNumber: n.endLine,
      endColumn: 1000,
    });
  }
  async function analyze() {
    await action("Анализ", async () => {
      const s = source;
      const result = await api<{ issues: Issue[] }>("/api/analysis", {
        source: s,
      });
      if (current.current.source === s) {
        setIssues(result.issues);
        setIssueLimit(100);
        setAnalyzed(true);
        setBottomTab("diagnostics");
        setNotice(
          `Проверено: ${result.issues.length} диагностических сообщений`,
        );
      }
    });
  }
  async function compile() {
    await action("Компиляция", async () => {
      const result = await api<Run>("/api/compile", { source, files });
      setRun(result);
      setBottomTab("output");
      setTab("workspace");
    });
  }
  async function compare() {
    await action("Проверка поведения", async () => {
      setComparison(
        await api<Comparison>("/api/tests/compare", {
          original: baseline.source,
          original_files: baseline.files,
          modified: source,
          modified_files: files,
          inputs: tests(),
        }),
      );
      setTab("tests");
      setBottomTab("tests");
    });
  }
  async function refactor() {
    await action("Рефакторинг и тесты", async () => {
      const result = await api<Preview>("/api/refactor", {
        source,
        files,
        inputs: tests(),
      });
      setPreview(result);
      setTab("refactor");
    });
  }
  async function edit() {
    if (!graph) return;
    await action("Изменение графа", async () => {
      const result = await api<Graph>("/api/graph/edit", {
        graph,
        files,
        operation: { type: editType, nodeId: selected, code: draft },
      });
      remember("До изменения графа");
      setProject((p) => ({
        ...p,
        source: result.source,
        label: "Изменение графа",
      }));
      setGraph(result);
      setNotice("Граф проверен. Java-код обновлён.");
      setSelected("");
    });
  }
  async function download() {
    await action("Экспорт", async () => {
      const response = await fetch("/api/files/download", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ source, files }),
      });
      if (!response.ok) throw new Error(await response.text());
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = response.headers.get("content-type")?.includes("zip")
        ? "mirutex-project.zip"
        : project.filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }
  async function openFile(file: File) {
    await action("Загрузка", async () => {
      const data = new FormData();
      data.append("file", file);
      const result = await api<{
        source: string;
        filename: string;
        graph: Graph;
      }>("/api/files/upload", data);
      remember("До загрузки файла");
      const next = {
        source: result.source,
        files: {},
        filename: result.filename,
        label: "Загружен " + file.name,
      };
      setProject(next);
      setBaseline(next);
      setGraph(result.graph);
      setTab("workspace");
      setNotice("Файл загружен и разобран");
    });
  }
  const shortcut = useRef({
    analyze,
    compile,
    download,
    save: onSaveNow ?? download,
  });
  useEffect(() => {
    shortcut.current = {
      analyze,
      compile,
      download,
      save: onSaveNow ?? download,
    };
  }, [analyze, compile, download, onSaveNow]);
  useEffect(() => {
    function key(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && ["s", "Enter", "k"].includes(e.key)) {
        e.preventDefault();
        if (e.key === "s") void shortcut.current.save();
        else if (e.key === "Enter") void shortcut.current.compile();
        else void shortcut.current.analyze();
      }
      if (e.key === "Escape") setSelected("");
    }
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, []);
  const resize = useCallback((e: React.PointerEvent) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    const move = (event: PointerEvent) => {
      const box = split.current?.getBoundingClientRect();
      if (box)
        setWidth(
          Math.max(
            25,
            Math.min(70, ((event.clientX - box.left) / box.width) * 100),
          ),
        );
    };
    const stop = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", stop);
  }, []);
  function undo(index = history.length - 1) {
    const snapshot = history[index];
    if (!snapshot) return;
    setProject({
      source: snapshot.source,
      files: snapshot.files,
      filename: snapshot.filename,
      label: snapshot.label,
    });
    if (snapshot.baseline) setBaseline(snapshot.baseline);
    setGraphFocus(null);
    setHistory((h) => h.slice(0, index));
    setSelected("");
    setNotice("Восстановлено: " + snapshot.label);
  }
  const disabled = !!busy;
  const stale = graph?.source !== source;
  const visibleParseError =
    parseError !== dismissedParseError ? parseError : "";
  const option = {
    fontSize: 13,
    editContext: false,
    fixedOverflowWidgets: true,
    suggestFontSize: 12,
    suggestLineHeight: 26,
    quickSuggestions: {
      other: "on" as const,
      comments: "off" as const,
      strings: "off" as const,
    },
    suggestOnTriggerCharacters: true,
    tabCompletion: "on" as const,
    acceptSuggestionOnEnter: "on" as const,
    snippetSuggestions: "top" as const,
    wordBasedSuggestions: "off" as const,
    suggest: { showStatusBar: true, preview: true },
    cursorSmoothCaretAnimation: "on" as const,
    smoothScrolling: true,
    lineHeight: 23,
    minimap: { enabled: false },
    padding: { top: 16 },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 4,
    wordWrap: "off" as const,
  };
  const commands: CommandItem[] = [
    ...(onOpenSpaces
      ? [
          {
            id: "spaces",
            title: "Мои пространства",
            detail: "Сохранить проект и открыть личные пространства",
            run: onOpenSpaces,
          },
        ]
      : []),
    ...(onShare
      ? [
          {
            id: "share",
            title: "Поделиться снимком проекта",
            detail: "Ссылка только для просмотра, без истории",
            run: onShare,
          },
        ]
      : []),
    {
      id: "passport",
      title: "Паспорт проверки",
      detail: "Скачать фактические результаты и SHA-256 исходников",
      run: () => {
        void action("Паспорт проверки", () =>
          downloadPassport(
            { project, baseline, history, inputs },
            { graph, analyzed, issues, run, comparison },
          ),
        );
      },
    },
    ...[
      { id: "workspace", title: "Код и граф" },
      { id: "analysis", title: "Анализ кода" },
      { id: "refactor", title: "Рефакторинг" },
      { id: "tests", title: "Тестирование" },
      { id: "history", title: "История" },
    ].map((v) => ({ ...v, detail: "Открыть раздел", run: () => setTab(v.id) })),
    {
      id: "analyze",
      title: "Анализировать программу",
      detail: "Найти сложность и потенциальные проблемы",
      run: () => {
        setTab("analysis");
        void analyze();
      },
    },
    {
      id: "compile",
      title: "Компилировать Java",
      detail: "Проверить все файлы проекта",
      run: () => {
        void compile();
      },
    },
    {
      id: "export",
      title: "Экспорт проекта",
      detail: "Скачать Java или ZIP",
      run: () => {
        void download();
      },
    },
    {
      id: "snapshot",
      title: "Сохранить снимок",
      detail: "Создать точку восстановления",
      run: () => {
        remember("Ручной снимок");
        setNotice("Снимок сохранён");
      },
    },
    {
      id: "diff",
      title: "Линза изменений",
      detail: "Сравнить код с исходной версией",
      run: () => setDialog("diff"),
    },
    {
      id: "map",
      title: "Карта методов",
      detail: "Найти метод и оценить размер графа",
      run: () => setDialog("methods"),
    },
    {
      id: "focus",
      title: "Режим фокуса",
      detail: "Скрыть лишнее вокруг редактора",
      run: () => {
        setTab("workspace");
        setFocusMode((v) => !v);
      },
    },
    {
      id: "theme",
      title: "Переключить тему",
      detail: dark ? "Включить светлую" : "Включить тёмную",
      run: () => setDark((v) => !v),
    },
    ...(graph?.methods.map((m) => ({
      id: "method-" + m.id,
      title: m.name,
      detail: `Метод · L${m.line} · ${m.signature}`,
      keywords: m.signature,
      run: () => {
        setTab("workspace");
        setMethod(m.id);
        const n = graph.nodes.find((n) => n.method === m.id);
        if (n) select(n);
      },
    })) ?? []),
    ...(graph?.nodes
      .filter((n) => !["start", "end"].includes(n.kind))
      .map((n) => ({
        id: "node-" + n.id,
        title: n.label || n.code,
        detail: `Узел · L${n.line} · ${n.kind}`,
        keywords: n.code,
        run: () => {
          setTab("workspace");
          select(n);
        },
      })) ?? []),
  ];
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (
        (e.ctrlKey || e.metaKey) &&
        e.shiftKey &&
        e.key.toLowerCase() === "p"
      ) {
        e.preventDefault();
        e.stopPropagation();
        setPalette((v) => !v);
      }
      if (e.key === "Escape") {
        setFullscreen(false);
      }
    };
    window.addEventListener("keydown", key, true);
    return () => window.removeEventListener("keydown", key, true);
  }, []);
  return (
    <div
      className={`studio ${collapsed ? "sidebar-collapsed" : ""} ${focusMode ? "focus-mode" : ""}`}
      data-section={tab}
    >
      <aside className="sidebar">
        <button
          className="brand"
          title="Открыть код и граф"
          onClick={() => setTab("workspace")}
        >
          <img className="brand-logo" src="/mirutex.png" alt="Mirutex VMO" />
          <div>
            <strong>
              Mirutex<span className="ide-word">IDE</span>
            </strong>
            <small>THINK IN CODE. SEE THE FLOW.</small>
          </div>
        </button>
        <button
          className="workspace-switch"
          disabled={disabled}
          onClick={() => (onOpenSpaces ? onOpenSpaces() : setDialog("project"))}
          title={onOpenSpaces ? "Мои пространства" : "Обзор проекта"}
        >
          <span className="avatar">M</span>
          <div>
            {workspaceName ?? "Mirutex VMO"}
            <small>
              {account
                ? `@${account.username} · Пространства`
                : "Java workspace"}
            </small>
          </div>
          <ChevronRight size={14} />
        </button>
        <div className="side-label">РАБОЧЕЕ ПРОСТРАНСТВО</div>
        <nav aria-label="Разделы Mirutex IDE">
          {[
            { id: "workspace", label: "Код и граф", icon: GitBranch },
            { id: "analysis", label: "Анализ кода", icon: ScanLine },
            { id: "refactor", label: "Рефакторинг", icon: Braces },
            { id: "tests", label: "Тестирование", icon: FlaskConical },
            { id: "history", label: "История", icon: History },
          ].map((v) => (
            <button
              key={v.id}
              className={tab === v.id ? "nav active" : "nav"}
              aria-current={tab === v.id ? "page" : undefined}
              onClick={() => setTab(v.id)}
            >
              <v.icon size={17} />
              {v.label}
              {v.id === "analysis" && analyzed && (
                <span className="nav-count">{issues.length}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="side-label files-heading">
          ФАЙЛЫ ПРОЕКТА{" "}
          <button
            title="Загрузить Java"
            onClick={() => uploadRef.current?.click()}
          >
            <Upload size={13} />
          </button>
        </div>
        <button className="project-file" onClick={() => setTab("workspace")}>
          <FileCode2 size={16} />
          <span>{project.filename}</span>
        </button>
        {Object.keys(files)
          .filter((f) => f !== project.filename)
          .map((f) => (
            <button
              key={f}
              className="project-file"
              onClick={() => {
                setComponent(f);
                setDialog("component");
              }}
            >
              <FileCode2 size={16} />
              <span>{f}</span>
            </button>
          ))}
        <button
          className="load-demo"
          disabled={disabled}
          onClick={() => {
            remember("До открытия примера");
            setProject(fresh);
            setBaseline(fresh);
            setTab("workspace");
          }}
        >
          Открыть пример <ArrowRight size={14} />
        </button>
        <div className="sidebar-bottom">
          <div className="team-icon">AX</div>
          <div>
            <strong>AXENIX CHALLENGE</strong>
            <small>Визуальная среда Java</small>
          </div>
        </div>
      </aside>
      <main className="ide-main">
        <header className="topbar">
          <div className="breadcrumbs">
            <button
              className="icon-button sidebar-toggle"
              title={collapsed ? "Развернуть меню" : "Свернуть меню"}
              aria-expanded={!collapsed}
              onClick={() => setCollapsed((v) => !v)}
            >
              <PanelLeftClose size={17} />
            </button>
            <span>Workspace</span>
            <ChevronRight size={13} />
            <strong>{project.filename}</strong>
          </div>
          <div className="header-actions">
            <button
              className="command-trigger"
              onClick={() => setPalette(true)}
              title="Палитра команд (Ctrl/⌘ Shift P)"
            >
              <Search size={15} />
              <span>Найти действие</span>
              <kbd>⌘ ⇧ P</kbd>
            </button>
            <button
              className="icon-button"
              title="Режим фокуса"
              aria-pressed={focusMode}
              onClick={() => {
                setTab("workspace");
                setFocusMode((v) => !v);
              }}
            >
              <Focus size={17} />
            </button>
            <button
              className="autosave"
              onClick={
                onSaveNow ??
                (() => setNotice("Текущий проект сохранён в этом браузере."))
              }
              title={
                onSaveNow
                  ? "Сохранить на сервере сейчас"
                  : "Автосохранение в браузере"
              }
            >
              <span className="dot" />
              {syncStatus ?? "Сохранено локально"}
            </button>
            <button
              className="icon-button"
              title="Переключить тему"
              onClick={() => setDark(!dark)}
            >
              {dark ? <Sun size={17} /> : <Moon size={17} />}
            </button>
            <button
              className="avatar small"
              title="Профиль и помощь"
              onClick={() => setDialog("profile")}
            >
              {account
                ? account.display_name.slice(0, 2).toUpperCase()
                : demoMode
                  ? "U"
                  : "MV"}
            </button>
          </div>
        </header>
        <div className="heading">
          <div>
            <div className="eyebrow">MIRUTEX IDE / JAVA WORKSPACE</div>
            <h1>
              {
                (
                  {
                    workspace: "От кода — к ясности.",
                    analysis: "Понять. Найти. Улучшить.",
                    refactor: "Меньше монолита. Больше структуры.",
                    tests: "Проверка реального поведения.",
                    history: "Каждое изменение под контролем.",
                  } as Record<string, string>
                )[tab]
              }
            </h1>
            <p>Исследуйте Java, изменяйте структуру и проверяйте результат.</p>
          </div>
          <div className="heading-actions">
            <input
              ref={uploadRef}
              type="file"
              accept=".java"
              hidden
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) void openFile(f);
                e.target.value = "";
              }}
            />
            <button
              className="button"
              disabled={disabled}
              onClick={() => uploadRef.current?.click()}
            >
              <Upload size={15} />
              Загрузить .java
            </button>
            <button
              className="button dark-button"
              disabled={disabled}
              onClick={download}
            >
              <Download size={15} />
              Экспорт
            </button>
          </div>
        </div>
        <div className="workflow">
          <button
            className="step current"
            onClick={() => {
              setTab("workspace");
              editorRef.current?.focus();
            }}
          >
            <span>01</span>Исходный код
          </button>
          <ChevronRight size={14} />
          <button
            className={"step " + (graph && !stale ? "current" : "")}
            onClick={() => {
              setTab("workspace");
              setDialog("methods");
            }}
          >
            <span>02</span>Визуальная модель
          </button>
          <ChevronRight size={14} />
          <button
            className={"step " + (analyzed ? "current" : "")}
            onClick={() => setTab("analysis")}
          >
            <span>03</span>Анализ и улучшение
          </button>
          <ChevronRight size={14} />
          <button
            onClick={() => setTab("tests")}
            className={
              "step " +
              (comparison?.behavior_preserved_for_tests ? "current" : "")
            }
          >
            <span>04</span>Проверка поведения
          </button>
        </div>
        {demoMode && (
          <div className="demo-banner">
            <Sparkles size={15} />
            <span>
              <strong>Демо user</strong> · редактор, граф, анализ и экспорт без
              пароля
            </span>
            <button onClick={() => setDialog("access")}>
              Создать аккаунт <ArrowRight size={14} />
            </button>
          </div>
        )}
        {(error || notice || visibleParseError) && (
          <div
            className={`message ${error || visibleParseError ? "error" : ""}`}
            role="status"
          >
            {error || visibleParseError ? (
              <AlertTriangle size={16} />
            ) : (
              <Check size={16} />
            )}
            <span>{error || visibleParseError || notice}</span>
            <button
              title="Закрыть уведомление"
              onClick={() => {
                setError("");
                setNotice("");
                setDismissedParseError(parseError);
              }}
            >
              <X size={14} />
            </button>
          </div>
        )}
        {busy && (
          <div className="busy-strip">
            <LoaderCircle size={15} className="spin" />
            {busy}… <span>Выполняется реальная операция</span>
          </div>
        )}
        {(tab === "workspace" || tab === "analysis") && (
          <>
            <div className="workspace-toolbar">
              <div className="view-tabs">
                <button
                  className={tab === "workspace" ? "active" : ""}
                  onClick={() => setTab("workspace")}
                >
                  <GitBranch size={15} />
                  Код и граф
                </button>
                <button
                  className={tab === "analysis" ? "active" : ""}
                  onClick={() => setTab("analysis")}
                >
                  <ScanLine size={15} />
                  Диагностика{analyzed && <b>{issues.length}</b>}
                </button>
              </div>
              <div className="toolbar-actions">
                <button
                  title="Отменить преобразование"
                  disabled={!history.length || disabled}
                  onClick={() => undo()}
                >
                  <RotateCcw size={15} />
                </button>
                <button
                  title="Линза изменений"
                  onClick={() => setDialog("diff")}
                >
                  <GitCompareArrows size={15} />
                  <span className="extra-label">Изменения</span>
                </button>
                <button
                  title="Карта методов"
                  onClick={() => setDialog("methods")}
                >
                  <Map size={15} />
                </button>
                <span className="divider" />
                <button disabled={disabled || parsing} onClick={analyze}>
                  <ScanLine size={15} />
                  Анализировать
                </button>
                <button
                  className="run-button"
                  disabled={disabled}
                  onClick={compile}
                >
                  <Play size={14} />
                  Компиляция <kbd>⌘ ↵</kbd>
                </button>
              </div>
            </div>
            <div ref={split} className="editor-grid">
              <section
                className="panel source-panel"
                style={{ width: `${width}%` }}
              >
                <div className="panel-title">
                  <div>
                    <FileCode2 size={16} />
                    <strong>{project.filename}</strong>
                    <span className="file-indicator" />
                  </div>
                  <span className="tag">JAVA 21</span>
                </div>
                <Editor
                  path={`${storageKey}-source.java`}
                  keepCurrentModel
                  height="100%"
                  language="java"
                  value={source}
                  onChange={(value) => {
                    if (value !== undefined)
                      setProject((p) => ({ ...p, source: value }));
                  }}
                  theme={dark ? "mirutex-dark" : "mirutex-light"}
                  options={{ ...option, readOnly: disabled }}
                  onMount={(ed, m) => {
                    editorRef.current = ed;
                    monacoRef.current = m;
                    ed.onMouseDown((event) => {
                      const line = event.target.position?.lineNumber;
                      if (line && graphRef.current) {
                        const n = graphRef.current.nodes
                          .filter((n) => n.line <= line && n.endLine >= line)
                          .sort(
                            (a, b) => a.endLine - a.line - (b.endLine - b.line),
                          )[0];
                        if (n) {
                          setSelected(n.id);
                          setMethod(n.method);
                          setGraphFocus({
                            id: n.id,
                            version: ++focusVersion.current,
                          });
                        }
                      }
                    });
                  }}
                />
                <div className="panel-foot">
                  <span className="completion-hint">
                    <span className="completion-dot" />
                    Java assist <kbd>Tab</kbd> принять · <kbd>⌃ Space</kbd>{" "}
                    подсказки
                  </span>
                  <span>
                    {source.split("\n").length} строк ·{" "}
                    {source.length.toLocaleString()} символов
                  </span>
                </div>
              </section>
              <div
                className="resize-handle"
                role="separator"
                aria-label="Изменить ширину панелей"
                tabIndex={0}
                aria-orientation="vertical"
                aria-valuemin={25}
                aria-valuemax={70}
                aria-valuenow={Math.round(width)}
                onKeyDown={(e) => {
                  if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
                    e.preventDefault();
                    setWidth((w) =>
                      Math.max(
                        25,
                        Math.min(70, w + (e.key === "ArrowRight" ? 2 : -2)),
                      ),
                    );
                  }
                }}
                onPointerDown={resize}
              />
              <section
                className={
                  "panel graph-panel " + (fullscreen ? "graph-fullscreen" : "")
                }
                ref={graphBox}
              >
                <div className="panel-title">
                  <div>
                    <GitBranch size={16} />
                    <strong>Граф выполнения</strong>
                  </div>
                  <span className={"sync-tag " + (stale ? "stale" : "")}>
                    {parsing ? (
                      <LoaderCircle size={12} className="spin" />
                    ) : (
                      <span className="dot" />
                    )}
                    {parsing
                      ? "Разбор…"
                      : stale
                        ? "Требуется исправление"
                        : "Синхронизирован"}
                  </span>
                </div>
                <div className="graph-toolbar">
                  <select
                    aria-label="Метод графа"
                    value={method}
                    onChange={(e) => {
                      setMethod(e.target.value);
                      setSelected("");
                    }}
                  >
                    {graph?.methods.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.name} · {m.signature}
                      </option>
                    ))}
                  </select>
                  <button
                    title="Автоматическая компоновка"
                    onClick={() => setLayout((l) => l + 1)}
                  >
                    <ScanLine size={15} />
                  </button>
                  <button
                    title="Полноэкранный граф"
                    onClick={() => setFullscreen(!fullscreen)}
                  >
                    {fullscreen ? (
                      <Minimize2 size={15} />
                    ) : (
                      <Maximize2 size={15} />
                    )}
                  </button>
                </div>
                <div className="graph-canvas">
                  <Flowchart
                    graph={graph}
                    method={method}
                    selected={selected}
                    onSelect={(n) => select(n, false)}
                    focus={graphFocus}
                    layoutVersion={layout}
                    dark={dark}
                  />
                  {node && (
                    <div className="node-inspector">
                      <div>
                        <strong>Узел · строка {node.line}</strong>
                        <button
                          title="Закрыть узел"
                          onClick={() => setSelected("")}
                        >
                          <X size={14} />
                        </button>
                      </div>
                      <p>
                        {node.editable
                          ? "Редактирование проверяется сервером перед обновлением кода."
                          : "Атомарный или служебный узел. Измените соответствующий Java-код."}
                      </p>
                      {node.editable && (
                        <>
                          <select
                            aria-label="Операция над узлом"
                            value={editType}
                            onChange={(e) => setEditType(e.target.value)}
                          >
                            <option value="replace">Заменить код узла</option>
                            {["call", "operation"].includes(node.kind) && (
                              <>
                                <option value="insertBefore">
                                  Вставить оператор перед узлом
                                </option>
                                <option value="delete">Удалить оператор</option>
                              </>
                            )}
                          </select>
                          {editType !== "delete" && (
                            <textarea
                              aria-label="Код узла"
                              value={draft}
                              onChange={(e) => setDraft(e.target.value)}
                            />
                          )}
                          <button
                            className="button primary"
                            disabled={disabled || stale}
                            onClick={edit}
                          >
                            Применить к Java <ArrowRight size={14} />
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </div>
                <div className="panel-foot">
                  <span>
                    <button
                      className="symbol-legend-button"
                      onClick={() => setDialog("symbols")}
                      title="Обозначения блок-схемы"
                    >
                      <HelpCircle size={12} />
                      Обозначения фигур
                    </button>
                  </span>
                  <span>
                    {graph?.nodes.filter((n) => n.method === method).length ??
                      0}{" "}
                    узлов
                  </span>
                </div>
              </section>
            </div>
            <section className="diagnostics panel">
              <div className="diagnostics-tabs">
                <button
                  className={bottomTab === "diagnostics" ? "active" : ""}
                  onClick={() => setBottomTab("diagnostics")}
                >
                  <AlertTriangle size={14} />
                  Диагностика <b>{issues.length}</b>
                </button>
                <button
                  className={bottomTab === "output" ? "active" : ""}
                  onClick={() => setBottomTab("output")}
                >
                  Вывод компилятора
                </button>
                <button
                  className={bottomTab === "tests" ? "active" : ""}
                  onClick={() => setBottomTab("tests")}
                >
                  Проверки <b>{comparison?.results.length ?? 0}</b>
                </button>
                <span className="diagnostic-caption">
                  {analyzed ? "Эвристический анализ" : "Java Engine / AST"}
                </span>
              </div>
              <div className="diagnostics-content">
                {bottomTab === "diagnostics" ? (
                  issues.length ? (
                    <>
                      {" "}
                      {issues.slice(0, issueLimit).map((i, index) => (
                        <button
                          className="issue-row"
                          key={index}
                          onClick={() => {
                            const n = graph?.nodes.find(
                              (n) => n.id === i.nodeId,
                            );
                            if (n) select(n);
                          }}
                        >
                          <span className={"severity " + i.severity}>
                            {i.type === "COMPLEXITY_ESTIMATE" ? (
                              "O"
                            ) : (
                              <AlertTriangle size={14} />
                            )}
                          </span>
                          <div>
                            <strong>{i.message}</strong>
                            <p>{i.recommendation}</p>
                          </div>
                          <code>
                            {i.complexity !== "UNKNOWN" ? i.complexity : ""}
                          </code>
                          <span className="line-link">L{i.line}</span>
                          <ChevronRight size={14} />
                        </button>
                      ))}{" "}
                      {issues.length > issueLimit && (
                        <div className="more-issues">
                          <span>
                            Показано {issueLimit} из {issues.length}
                          </span>
                          <button
                            className="button"
                            onClick={() => setIssueLimit((n) => n + 100)}
                          >
                            Показать ещё 100
                          </button>
                        </div>
                      )}{" "}
                    </>
                  ) : (
                    <div className="empty-state">
                      <ScanLine size={25} />
                      <div>
                        <strong>
                          {analyzed
                            ? "Анализ завершён"
                            : "Код готов к исследованию"}
                        </strong>
                        <p>
                          {analyzed
                            ? "Диагностические сообщения не найдены. Это не доказывает отсутствие дефектов."
                            : "Запустите анализ, чтобы увидеть сложность методов и рекомендации."}
                        </p>
                      </div>
                      <button
                        className="button"
                        disabled={disabled}
                        onClick={analyze}
                      >
                        Запустить анализ <ArrowRight size={14} />
                      </button>
                    </div>
                  )
                ) : bottomTab === "output" ? (
                  <RunOutput run={run} />
                ) : (
                  <TestOutput comparison={comparison} />
                )}
              </div>
            </section>
            {graph && graph.limitations.length > 0 && (
              <details className="limitations">
                <summary>
                  Границы визуальной модели · {graph.limitations.length}
                </summary>
                {graph.limitations.map((l, i) => (
                  <p key={i}>{l}</p>
                ))}
              </details>
            )}
          </>
        )}
        {tab === "refactor" && (
          <section className="panel task-panel">
            <div className="task-heading">
              <div>
                <Braces size={24} />
                <h2>Выделение самостоятельных методов</h2>
                <p>
                  Private static методы без зависимостей от полей и методов
                  исходного класса. Перед применением — компиляция и тесты.
                </p>
              </div>
              <button
                className="button primary"
                disabled={disabled}
                onClick={refactor}
              >
                Подготовить и проверить <ArrowRight size={15} />
              </button>
            </div>
            {preview ? (
              <>
                <div className="preview-status">
                  <span
                    className={
                      "badge " + (preview.accepted ? "success" : "warning")
                    }
                  >
                    {preview.accepted
                      ? "Проверки пройдены"
                      : preview.extracted.length
                        ? "Проверки не пройдены"
                        : "Нет безопасных кандидатов"}
                  </span>
                  <span>{preview.note}</span>
                </div>
                {preview.candidates.map((c, i) => (
                  <div className="candidate" key={i}>
                    <code>{c.name}()</code>
                    <span>L{c.line}</span>
                    <span>{c.reason}</span>
                  </div>
                ))}
                <DiffEditor
                  originalModelPath="mirutex-original.java"
                  modifiedModelPath="mirutex-preview.java"
                  keepCurrentOriginalModel
                  keepCurrentModifiedModel
                  height="400px"
                  language="java"
                  original={preview.original}
                  modified={preview.source}
                  theme={dark ? "mirutex-dark" : "mirutex-light"}
                  options={{ ...option, readOnly: true }}
                />
                {Object.entries(preview.files)
                  .filter(([name]) => name !== project.filename)
                  .map(([name, text]) => (
                    <details key={name} className="component-preview" open>
                      <summary>
                        <FileCode2 size={15} />
                        {name}
                      </summary>
                      <pre>{text}</pre>
                    </details>
                  ))}
                <TestOutput comparison={preview.validation} />
                <button
                  className="button primary"
                  disabled={disabled || !preview.accepted}
                  onClick={() => {
                    remember("До рефакторинга");
                    setProject((p) => ({
                      ...p,
                      source: preview.source,
                      files: Object.fromEntries(
                        Object.entries(preview.files).filter(
                          ([name]) => name !== project.filename,
                        ),
                      ),
                      label: "Выделение компонента",
                    }));
                    setTab("workspace");
                    setNotice(
                      "Проверенный рефакторинг применён. Откат доступен в истории.",
                    );
                  }}
                >
                  Применить проверенные изменения <Check size={15} />
                </button>
              </>
            ) : (
              <div className="large-empty">
                <Braces size={44} />
                <h3>Из монолита — в компоненты</h3>
                <p>
                  Получите несколько Java-файлов с сохранением исходных сигнатур
                  через делегирующие методы.
                </p>
              </div>
            )}
          </section>
        )}
        {tab === "tests" && (
          <section className="panel task-panel">
            <div className="task-heading">
              <div>
                <FlaskConical size={24} />
                <h2>Проверка поведения</h2>
                <p>
                  Исходная версия: {baseline.filename}. Сравниваются stdout,
                  stderr и код завершения в изолированных Java 21 контейнерах.
                </p>
              </div>
              <button
                className="button primary"
                disabled={disabled}
                onClick={compare}
              >
                <Play size={15} />
                Сравнить версии
              </button>
            </div>
            <div className="testing-grid">
              <div>
                <label className="input-label">
                  Входные данные · отдельные сценарии через строку ---
                </label>
                <textarea
                  className="test-input"
                  aria-label="Тестовые входы"
                  value={inputs}
                  disabled={disabled}
                  onChange={(e) => setInputs(e.target.value)}
                />
                <p className="fine-print">
                  До 10 сценариев. Пустой ввод — тоже сценарий. Конечный набор
                  тестов не доказывает эквивалентность программ.
                </p>
                <button
                  className="button"
                  disabled={disabled}
                  onClick={() =>
                    action("Выполнение", async () => {
                      setRun(
                        await api<Run>("/api/execute", {
                          source,
                          files,
                          stdin: tests()[0],
                        }),
                      );
                    })
                  }
                >
                  <Play size={14} />
                  Выполнить текущую версию
                </button>
                <RunOutput run={run} />
              </div>
              <div>
                <TestOutput comparison={comparison} />
              </div>
            </div>
          </section>
        )}
        {tab === "history" && (
          <section className="panel task-panel">
            <div className="task-heading">
              <div>
                <History size={24} />
                <h2>История преобразований</h2>
                <p>
                  До 20 снимков кода, исходной версии и файлов проекта{" "}
                  {account
                    ? "сохраняются в вашем пространстве на сервере."
                    : "сохраняются в этом браузере."}
                </p>
              </div>
              <button
                className="button"
                disabled={disabled}
                onClick={() => {
                  remember("Ручной снимок");
                  setNotice("Снимок сохранён");
                }}
              >
                Сохранить снимок
              </button>
            </div>
            {history.length ? (
              history.map((h, i) => (
                <div className="history-row" key={i}>
                  <History size={18} />
                  <div>
                    <strong>{h.label}</strong>
                    <p>
                      {h.filename} · {h.source.split("\n").length} строк
                    </p>
                  </div>
                  <button
                    className="button"
                    disabled={disabled}
                    onClick={() => undo(i)}
                  >
                    Восстановить <RotateCcw size={14} />
                  </button>
                </div>
              ))
            ) : (
              <div className="large-empty">
                <History size={40} />
                <p>
                  Снимки появляются перед изменениями графа, загрузкой и
                  рефакторингом.
                </p>
              </div>
            )}
          </section>
        )}
        {palette && (
          <CommandPalette items={commands} close={() => setPalette(false)} />
        )}
        {dialog && (
          <StudioDialog
            title={
              {
                symbols: "Как читать блок-схему",
                project: "Обзор проекта",
                profile: "Ваше рабочее пространство",
                access: "Полное рабочее пространство",
                methods: "Карта методов",
                diff: "Линза изменений",
                component,
              }[dialog] ?? "Mirutex IDE"
            }
            close={() => setDialog("")}
            wide={["diff", "component"].includes(dialog)}
          >
            {dialog === "symbols" && (
              <>
                <div className="symbol-guide">
                  {symbolGuide.map((item) => (
                    <div key={item.shape}>
                      <div className="symbol-preview">
                        <FlowSymbol
                          shape={item.shape}
                          width={item.shape === "decision" ? 300 : 260}
                          height={item.shape === "decision" ? 176 : 100}
                        />
                      </div>
                      <div>
                        <strong>{item.title}</strong>
                        <p>{item.description}</p>
                      </div>
                    </div>
                  ))}
                </div>
                <p className="symbol-guide-note">
                  Пунктирные боковые линии показывают обратные переходы циклов.
                  Перемещение фигуры меняет только раскладку. Полный код
                  доступен при выборе узла.
                </p>
              </>
            )}
            {dialog === "methods" && (
              <>
                <p className="dialog-description">
                  Перейдите к методу, не теряя контекст. Размер показывает число
                  узлов его графа.
                </p>
                <input
                  className="method-search"
                  aria-label="Найти метод"
                  placeholder="Имя метода или сигнатура…"
                  value={methodQuery}
                  onChange={(e) => setMethodQuery(e.target.value)}
                />
                <div className="method-list">
                  {graph?.methods
                    .filter((m) =>
                      (m.name + " " + m.signature)
                        .toLowerCase()
                        .includes(methodQuery.toLowerCase()),
                    )
                    .map((m) => (
                      <button
                        key={m.id}
                        onClick={() => {
                          setDialog("");
                          setTab("workspace");
                          setMethod(m.id);
                          const n = graph.nodes.find((n) => n.method === m.id);
                          if (n) select(n);
                        }}
                      >
                        <span>
                          <strong>{m.name}</strong>
                          <small>{m.signature}</small>
                        </span>
                        <span>
                          L{m.line} ·{" "}
                          {graph.nodes.filter((n) => n.method === m.id).length}{" "}
                          узлов <ArrowRight size={14} />
                        </span>
                      </button>
                    ))}
                  {(!graph ||
                    !graph.methods.some((m) =>
                      (m.name + " " + m.signature)
                        .toLowerCase()
                        .includes(methodQuery.toLowerCase()),
                    )) && (
                    <p>Методы не найдены. Проверьте поиск и синтаксис Java.</p>
                  )}
                </div>
              </>
            )}
            {dialog === "diff" && (
              <>
                <p className="dialog-description">
                  Слева исходная версия сравнения, справа текущий код. Снимки
                  сохраняют также дополнительные файлы проекта.
                </p>
                <DiffEditor
                  originalModelPath="mirutex-lens-original.java"
                  modifiedModelPath="mirutex-lens-current.java"
                  keepCurrentOriginalModel
                  keepCurrentModifiedModel
                  height="55vh"
                  language="java"
                  original={baseline.source}
                  modified={source}
                  theme={dark ? "mirutex-dark" : "mirutex-light"}
                  options={{ ...option, readOnly: true }}
                />
                <div className="dialog-actions">
                  <button
                    className="button"
                    onClick={() => {
                      remember("Снимок из линзы изменений");
                      setNotice("Снимок сохранён");
                      setDialog("");
                    }}
                  >
                    Сохранить снимок
                  </button>
                  <button
                    className="button primary"
                    onClick={() => {
                      setDialog("");
                      setTab("history");
                    }}
                  >
                    Открыть историю
                  </button>
                </div>
              </>
            )}
            {dialog === "component" && (
              <>
                <Editor
                  path={"component-" + component}
                  height="55vh"
                  language="java"
                  value={files[component] ?? "Файл больше не входит в проект"}
                  theme={dark ? "mirutex-dark" : "mirutex-light"}
                  options={{ ...option, readOnly: true }}
                />
                <p className="dialog-description">
                  Файл входит в компиляцию проекта и ZIP-экспорт. Просмотр
                  компонента не меняет исходный граф.
                </p>
                <button
                  className="button primary"
                  onClick={() => {
                    void download();
                  }}
                >
                  Экспортировать проект <Download size={14} />
                </button>
              </>
            )}
            {dialog === "project" && (
              <>
                <p className="dialog-description">
                  {project.filename} · {source.split("\n").length} строк ·{" "}
                  {Object.keys(files).filter((f) => f !== project.filename)
                    .length + 1}{" "}
                  файлов
                </p>
                <div className="project-metrics">
                  <span>
                    <strong>{graph?.methods.length ?? 0}</strong>методов
                  </span>
                  <span>
                    <strong>{graph?.nodes.length ?? 0}</strong>узлов
                  </span>
                  <span>
                    <strong>{history.length}</strong>снимков
                  </span>
                </div>
                <div className="dialog-actions">
                  <button
                    className="button primary"
                    onClick={() => setDialog("methods")}
                  >
                    Карта методов
                  </button>
                  <button
                    className="button"
                    onClick={() => {
                      setDialog("");
                      setTab("history");
                    }}
                  >
                    История
                  </button>
                  <button className="button" onClick={() => setDialog("diff")}>
                    Изменения
                  </button>
                </div>
              </>
            )}
            {(dialog === "profile" || dialog === "access") && (
              <>
                <div className="access-icon">
                  <Sparkles size={28} />
                </div>
                <h3>
                  {account
                    ? `${account.display_name} · @${account.username}`
                    : demoMode
                      ? "user · демо без пароля"
                      : "Локальное рабочее пространство"}
                </h3>
                <p className="dialog-description">
                  {demoMode
                    ? "Можно писать Java, исследовать граф, запускать анализ, сохранять снимки и скачивать код. Создайте аккаунт для личных пространств, компиляции, запуска и рефакторинга."
                    : "Доступен полный цикл: граф, компиляция, выполнение, рефакторинг и сравнение поведения. В аккаунте код, снимки и входные данные сохраняются на сервере."}
                </p>
                <div className="shortcut-sheet">
                  <span>
                    Палитра команд <kbd>Ctrl/⌘ Shift P</kbd>
                  </span>
                  <span>
                    Анализ <kbd>Ctrl/⌘ K</kbd>
                  </span>
                  <span>
                    Экспорт <kbd>Ctrl/⌘ S</kbd>
                  </span>
                  <span>
                    Java assist <kbd>Ctrl Space / Tab</kbd>
                  </span>
                </div>
                <div className="dialog-actions">
                  {demoMode && (
                    <button className="button primary" onClick={onExit}>
                      Вход и регистрация <ArrowRight size={14} />
                    </button>
                  )}
                  <button
                    className="button"
                    onClick={() => {
                      setDialog("");
                      setPalette(true);
                    }}
                  >
                    <Search size={14} />
                    Все команды
                  </button>
                  <button
                    className="button"
                    onClick={() => {
                      void action("Паспорт проверки", () =>
                        downloadPassport(
                          { project, baseline, history, inputs },
                          { graph, analyzed, issues, run, comparison },
                        ),
                      );
                    }}
                  >
                    Паспорт проверки
                  </button>
                  <button className="button" onClick={onExit}>
                    <LogOut size={14} />
                    {demoMode ? "Выйти из демо" : "На экран входа"}
                  </button>
                </div>
              </>
            )}
          </StudioDialog>
        )}
        <footer className="statusbar">
          <span>
            <span className={"dot " + (parseError ? "bad" : "")} />
            {parsing
              ? "Разбор Java…"
              : parseError
                ? "Ошибка разбора"
                : "JavaParser · Java 21"}
            <span className="status-separator" />
            {graph?.methods.length ?? 0} методов
          </span>
          <span>
            <button
              title="Горячие клавиши"
              onClick={() => setDialog("profile")}
            >
              <HelpCircle size={13} />
            </button>{" "}
            <Command size={12} />K анализ <Command size={12} />↵ компиляция{" "}
            <span className="status-separator" />
            Mirutex IDE
          </span>
        </footer>
      </main>
    </div>
  );
}
function RunOutput({ run }: { run: Run | null }) {
  return run ? (
    <div className="run-output">
      <span
        className={
          "badge " +
          (run.compiled &&
          run.exit_code === 0 &&
          !run.timed_out &&
          !run.output_limited
            ? "success"
            : "warning")
        }
      >
        {run.timed_out
          ? "Превышено время"
          : run.output_limited
            ? "Лимит вывода"
            : !run.compiled
              ? "Ошибка компиляции"
              : run.exit_code === 0
                ? "Успешно"
                : "Ошибка выполнения"}
      </span>
      <small>
        {" "}
        {run.phase} · exit {run.exit_code ?? "—"}
      </small>
      <pre>{run.stdout + run.stderr || "Без вывода"}</pre>
    </div>
  ) : (
    <p className="quiet">
      Результат появится после запуска компиляции или программы.
    </p>
  );
}
function TestOutput({ comparison }: { comparison: Comparison | null }) {
  return comparison ? (
    <div className="test-output">
      <span
        className={
          "badge " +
          (comparison.behavior_preserved_for_tests ? "success" : "warning")
        }
      >
        {comparison.behavior_preserved_for_tests
          ? "Поведение совпало на тестах"
          : "Обнаружено расхождение или ошибка"}
      </span>
      {comparison.results.map((t, i) => (
        <details key={i} className="test-case">
          <summary>
            {t.passed ? <Check size={15} /> : <X size={15} />}Сценарий {i + 1}
            <code>stdin: {JSON.stringify(t.stdin)}</code>
            <span>{t.passed ? "PASS" : "FAIL"}</span>
          </summary>
          <div className="test-logs">
            <div>
              <strong>Исходная версия</strong>
              <RunOutput run={t.original} />
            </div>
            <div>
              <strong>Текущая версия</strong>
              <RunOutput run={t.modified} />
            </div>
          </div>
        </details>
      ))}
      <p className="fine-print">{comparison.note}</p>
    </div>
  ) : (
    <div className="empty-state">
      <FlaskConical size={25} />
      <div>
        <strong>Проверки ещё не запускались</strong>
        <p>
          Откройте «Тестирование», задайте входные данные и сравните версии.
        </p>
      </div>
    </div>
  );
}
export default App;
