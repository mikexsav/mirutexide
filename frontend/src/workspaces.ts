import { demo } from "./demo.ts";

export type Account = { id: string; username: string; display_name: string };
export type Session = {
  user: Account | null;
  csrf_token: string | null;
  execution_requires_account?: boolean;
};
export type ProjectData = {
  source: string;
  files: Record<string, string>;
  filename: string;
  label: string;
};
export type Snapshot = ProjectData & { baseline?: ProjectData };
export type WorkspaceState = {
  project: ProjectData;
  baseline: ProjectData;
  history: Snapshot[];
  inputs: string;
};
export type WorkspaceSummary = {
  id: string;
  name: string;
  description: string;
  version: number;
  archived: boolean;
  created_at: number;
  updated_at: number;
  filename: string;
  lines: number;
  files: number;
  snapshots: number;
};
export type Workspace = WorkspaceSummary & { state: WorkspaceState };
export type Share = {
  id: string;
  version: number;
  created_at: number;
  expires_at: number;
  revoked: number;
};
export const fresh: ProjectData = {
  source: demo,
  files: {},
  filename: "Main.java",
  label: "Демонстрационный проект",
};

const flowDemo = `import java.util.Scanner;

public class FlowchartDemo {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int limit = input.hasNextInt() ? input.nextInt() : 3;
        int i = 0;
        while (i < limit) {
            if (i % 2 == 0) {
                System.out.println("Чётное: " + i);
            } else {
                System.out.println("Нечётное: " + i);
            }
            i++;
        }
        printDone();
    }

    private static void printDone() {
        System.out.println("Готово");
    }
}
`;
export const templates = [
  {
    id: "demo",
    name: "Анализ и рефакторинг",
    description: "Вложенные циклы, коллекции и самостоятельный метод",
    project: fresh,
  },
  {
    id: "flow",
    name: "Циклы и условия",
    description: "Scanner, while, if / else и вывод в консоль",
    project: {
      source: flowDemo,
      files: {},
      filename: "FlowchartDemo.java",
      label: "Циклы и условия",
    },
  },
  {
    id: "blank",
    name: "Чистый Java-проект",
    description: "Метод main и место для вашей идеи",
    project: {
      source:
        'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, Mirutex!");\n    }\n}\n',
      files: {},
      filename: "Main.java",
      label: "Новый проект",
    },
  },
];
export function newState(project = fresh): WorkspaceState {
  return {
    project: { ...project, files: { ...project.files } },
    baseline: { ...project, files: { ...project.files } },
    history: [],
    inputs: "5\n---\n0\n---\n3",
  };
}
export function restoreLocal(key = "mirutex.project"): WorkspaceState {
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? "null");
    if (
      typeof saved?.project?.source === "string" &&
      typeof saved?.baseline?.source === "string" &&
      typeof saved.project.filename === "string"
    )
      return {
        ...saved,
        history: Array.isArray(saved.history) ? saved.history.slice(-20) : [],
        inputs:
          typeof saved.inputs === "string"
            ? saved.inputs
            : (localStorage.getItem("mirutex.inputs") ?? "5\n---\n0\n---\n3"),
      };
  } catch {
    /* A damaged browser draft must not prevent entering the IDE. */
  }
  return newState();
}
export function errorMessage(e: unknown) {
  return e instanceof Error ? e.message : String(e);
}
