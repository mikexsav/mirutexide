import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor/editor/editor.api.js";
import "monaco-editor/languages/definitions/java/register.js";
import "monaco-editor/editor/contrib/suggest/browser/suggestController.js";
import "monaco-editor/editor/contrib/snippet/browser/snippetController2.js";
import "monaco-editor/editor/contrib/hover/browser/hoverContribution.js";
import EditorWorker from "monaco-editor/editor/editor.worker.js?worker";
import { getJavaSuggestions } from "./javaSuggestions";
self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
loader.config({ monaco });
const kinds = {
  snippet: monaco.languages.CompletionItemKind.Snippet,
  method: monaco.languages.CompletionItemKind.Method,
  field: monaco.languages.CompletionItemKind.Field,
  variable: monaco.languages.CompletionItemKind.Variable,
  class: monaco.languages.CompletionItemKind.Class,
  keyword: monaco.languages.CompletionItemKind.Keyword,
};
const provider = monaco.languages.registerCompletionItemProvider("java", {
  triggerCharacters: ["."],
  provideCompletionItems(model, position) {
    const word = model.getWordUntilPosition(position);
    const range = new monaco.Range(
      position.lineNumber,
      word.startColumn,
      position.lineNumber,
      word.endColumn,
    );
    return {
      suggestions: getJavaSuggestions(
        model.getValue(),
        model.getOffsetAt(position),
      ).map((s) => ({
        label: s.label,
        insertText: s.insertText,
        kind: kinds[s.kind],
        detail: s.detail,
        documentation: s.description || s.detail,
        range,
        sortText: s.sort ?? s.label,
        insertTextRules: /\$\{|\$0/.test(s.insertText)
          ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet
          : undefined,
      })),
    };
  },
});
// Vite hot reload must not accumulate duplicate providers.
if (import.meta.hot) import.meta.hot.dispose(() => provider.dispose());
monaco.editor.defineTheme("mirutex-light", {
  base: "vs",
  inherit: true,
  rules: [],
  colors: {
    "editor.background": "#ffffff",
    "editor.foreground": "#2b3a50",
    "editor.lineHighlightBackground": "#f4f7fc",
    "editorLineNumber.foreground": "#a0adc0",
    "editorSuggestWidget.background": "#ffffff",
    "editorSuggestWidget.foreground": "#283448",
    "editorSuggestWidget.selectedForeground": "#20334e",
    "editorSuggestWidget.focusHighlightForeground": "#315ee8",
    "editorSuggestWidget.border": "#d7e0ee",
    "editorSuggestWidget.selectedBackground": "#edf2ff",
    "editorSuggestWidget.highlightForeground": "#315ee8",
    "editorWidget.border": "#d7e0ee",
  },
});
monaco.editor.defineTheme("mirutex-dark", {
  base: "vs-dark",
  inherit: true,
  rules: [],
  colors: {
    "editor.background": "#182333",
    "editor.foreground": "#d8e3f4",
    "editor.lineHighlightBackground": "#202e43",
    "editorLineNumber.foreground": "#7386a3",
    "editorSuggestWidget.background": "#223149",
    "editorSuggestWidget.foreground": "#d8e3f4",
    "editorSuggestWidget.selectedForeground": "#f1f5ff",
    "editorSuggestWidget.focusHighlightForeground": "#b0c7ff",
    "editorSuggestWidget.border": "#455c80",
    "editorSuggestWidget.selectedBackground": "#334d76",
    "editorSuggestWidget.highlightForeground": "#94b3ff",
    "editorWidget.border": "#455c80",
  },
});
