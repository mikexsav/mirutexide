import { useEffect, useState } from "react";
import Editor from "@monaco-editor/react";
import "./monaco";
import "./App.css";
import "./Experience.css";
import { ArrowLeft, Copy, Eye, LoaderCircle } from "lucide-react";
import { api, type Graph } from "./types";
import { errorMessage, type WorkspaceState } from "./workspaces";
import Flowchart from "./Flowchart";
import "./Cloud.css";

export default function SharedWorkspace({ token }: { token: string }) {
  const [data, setData] = useState<{
    name: string;
    version: number;
    state: WorkspaceState;
    expires_at: number;
  } | null>(null);
  const [graph, setGraph] = useState<Graph | null>(null);
  const [error, setError] = useState("");
  const [method, setMethod] = useState("");
  const [selected, setSelected] = useState("");
  const [file, setFile] = useState("");
  const [notice, setNotice] = useState("");
  const [cloning, setCloning] = useState(false);
  useEffect(() => {
    let disposed = false;
    void api<typeof data>(`/api/shares/${encodeURIComponent(token)}`)
      .then(async (result) => {
        if (!result || disposed) return;
        setData(result);
        setFile(result.state.project.filename);
        try {
          const g = await api<Graph>("/api/graph/parse", {
            source: result.state.project.source,
          });
          if (!disposed) {
            setGraph(g);
            setMethod(g.methods[0]?.id ?? "");
          }
        } catch (e) {
          if (!disposed) setError(errorMessage(e));
        }
      })
      .catch((e) => {
        if (!disposed) setError(errorMessage(e));
      });
    return () => {
      disposed = true;
    };
  }, [token]);
  async function clone() {
    if (!data || cloning) return;
    setCloning(true);
    setNotice("");
    try {
      await api("/api/workspaces", {
        name: `${data.name.slice(0, 65)} · копия`,
        state: data.state,
      });
      setNotice("Копия сохранена в ваших пространствах.");
    } catch (e) {
      setNotice(errorMessage(e));
    } finally {
      setCloning(false);
    }
  }
  const p = data?.state.project;
  const files = p ? { ...p.files, [p.filename]: p.source } : {};
  return (
    <main className="shared-page">
      <header className="cloud-top">
        <a href="/" className="cloud-brand">
          <img src="/mirutex.png" alt="Mirutex VMO" />
          <span>
            Mirutex <b>IDE</b>
          </span>
        </a>
        <a className="button" href="/">
          <ArrowLeft size={15} />В аккаунт
        </a>
      </header>
      {data ? (
        <>
          <section className="shared-heading">
            <div>
              <span className="cloud-kicker">
                <Eye size={15} /> СНИМОК ТОЛЬКО ДЛЯ ПРОСМОТРА
              </span>
              <h1>{data.name}</h1>
              <p>
                Версия {data.version}. Ссылка действует до{" "}
                {new Date(data.expires_at * 1000).toLocaleDateString("ru-RU")}.
                Изменения автора после публикации здесь не появятся.
              </p>
            </div>
            <button
              className="button primary"
              disabled={cloning}
              onClick={clone}
            >
              <Copy size={16} />
              Копия в мои пространства
            </button>
          </section>
          {notice && (
            <p className="cloud-alert" role="status">
              {notice}
            </p>
          )}
          <div className="shared-controls">
            <label>
              Файл{" "}
              <select value={file} onChange={(e) => setFile(e.target.value)}>
                {Object.keys(files).map((name) => (
                  <option key={name}>{name}</option>
                ))}
              </select>
            </label>
            <label>
              Метод графа{" "}
              <select
                value={method}
                onChange={(e) => setMethod(e.target.value)}
              >
                {graph?.methods.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} · {m.signature}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="shared-editors">
            <section className="panel">
              <div className="panel-title">{file}</div>
              <Editor
                language="java"
                value={files[file] ?? ""}
                height="530px"
                theme="mirutex-light"
                options={{
                  readOnly: true,
                  domReadOnly: true,
                  minimap: { enabled: false },
                  fontSize: 13,
                  automaticLayout: true,
                }}
              />
            </section>
            <section className="panel shared-graph">
              <Flowchart
                graph={graph}
                method={method}
                selected={selected}
                onSelect={(n) => setSelected(n.id)}
                focus={null}
                layoutVersion={0}
                dark={false}
              />
            </section>
          </div>
          <p className="dialog-description">
            Можно исследовать код и перемещать фигуры в своём просмотре.
            Содержимое опубликованного снимка не меняется.
          </p>
        </>
      ) : (
        !error && (
          <div className="cloud-empty">
            <LoaderCircle className="spin" />
            Открываем снимок…
          </div>
        )
      )}
      {error && (
        <div className="cloud-alert" role="alert">
          {error}
        </div>
      )}
    </main>
  );
}
