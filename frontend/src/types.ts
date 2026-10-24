export type GraphNode = {
  id: string;
  kind: string;
  label: string;
  code: string;
  method: string;
  line: number;
  endLine: number;
  start: number;
  end: number;
  editable: boolean;
  depth: number;
};
export type Graph = {
  schemaVersion: string;
  source: string;
  sourceHash: string;
  filename: string;
  mainClass: string;
  nodes: GraphNode[];
  edges: { id: string; source: string; target: string; label: string }[];
  methods: {
    id: string;
    name: string;
    signature: string;
    line: number;
    endLine: number;
  }[];
  declarations: string[];
  limitations: string[];
};
export type Issue = {
  type: string;
  severity: string;
  method: string;
  line: number;
  complexity: string;
  message: string;
  recommendation: string;
  nodeId: string;
};
export type Run = {
  compiled: boolean;
  exit_code: number | null;
  stdout: string;
  stderr: string;
  timed_out: boolean;
  output_limited: boolean;
  phase: string;
};
export type Comparison = {
  behavior_preserved_for_tests: boolean;
  results: { stdin: string; passed: boolean; original: Run; modified: Run }[];
  note: string;
};
export type Preview = {
  source: string;
  original: string;
  files: Record<string, string>;
  candidates: {
    name: string;
    line: number;
    eligible: boolean;
    reason: string;
  }[];
  extracted: string[];
  accepted: boolean;
  note: string;
  validation: Comparison | null;
};
export async function api<T>(path: string, data?: unknown): Promise<T> {
  const r = await fetch(path, {
    method: data === undefined ? "GET" : "POST",
    headers:
      data instanceof FormData ? {} : { "Content-Type": "application/json" },
    body:
      data === undefined
        ? undefined
        : data instanceof FormData
          ? data
          : JSON.stringify(data),
  });
  if (!r.ok) {
    let message = await r.text();
    try {
      const j = JSON.parse(message);
      message =
        typeof j.detail === "string" ? j.detail : JSON.stringify(j.detail ?? j);
    } catch {}
    throw new Error(message);
  }
  return r.json();
}
