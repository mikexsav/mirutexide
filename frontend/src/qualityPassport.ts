import type { Comparison, Graph, Issue, Run } from "./types";
import type { WorkspaceState } from "./workspaces";

export type Evidence = {
  graph: Graph | null;
  analyzed: boolean;
  issues: Issue[];
  run: Run | null;
  comparison: Comparison | null;
};
export function passportData(
  state: WorkspaceState,
  evidence: Evidence,
  hash: string,
  now: string,
) {
  const freshGraph = evidence.graph?.source === state.project.source;
  return {
    product: "Mirutex IDE",
    schema: "mirutex-quality-passport/1",
    created_at: now,
    filename: state.project.filename,
    project_sha256: hash,
    files: Object.keys({
      ...state.project.files,
      [state.project.filename]: state.project.source,
    }),
    lines: state.project.source.split("\n").length,
    graph: freshGraph
      ? {
          status: "parsed",
          methods: evidence.graph!.methods.length,
          nodes: evidence.graph!.nodes.length,
          limitations: evidence.graph!.limitations,
        }
      : { status: "not_current" },
    analysis:
      freshGraph && evidence.analyzed
        ? { status: "completed", issues: evidence.issues }
        : { status: "not_run" },
    compilation_or_run: freshGraph
      ? (evidence.run ?? { status: "not_run" })
      : { status: "not_current" },
    comparison:
      freshGraph &&
      JSON.stringify(evidence.comparison?.results.map((r) => r.stdin)) ===
        JSON.stringify(state.inputs.split(/\r?\n---\r?\n/))
        ? evidence.comparison
        : { status: "not_run" },
    note: "Статусы отражают только выполненные операции для текущей версии. Совпадение на конечном наборе входов не доказывает полную эквивалентность.",
  };
}
export async function downloadPassport(
  state: WorkspaceState,
  evidence: Evidence,
) {
  // Bind the report to all source files, not just the main editor buffer.
  const project = {
    ...state.project.files,
    [state.project.filename]: state.project.source,
  };
  const canonical = JSON.stringify(
    Object.keys(project)
      .sort()
      .map((k) => [k, project[k]]),
  );
  const raw = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(canonical),
  );
  const hash = Array.from(new Uint8Array(raw), (b) =>
    b.toString(16).padStart(2, "0"),
  ).join("");
  const data = passportData(state, evidence, hash, new Date().toISOString());
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], {
      type: "application/json;charset=utf-8",
    }),
  );
  const link = document.createElement("a");
  link.href = url;
  link.download = "mirutex-quality-passport.json";
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
