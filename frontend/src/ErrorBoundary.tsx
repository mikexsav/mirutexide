import { Component, type ReactNode } from "react";
export default class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: string }
> {
  state = { error: "" };
  static getDerivedStateFromError(error: Error) {
    return { error: error.message };
  }
  render() {
    if (this.state.error)
      return (
        <div className="recovery">
          <h1>Не удалось отобразить рабочее пространство</h1>
          <p>Сохранённый проект можно восстановить после перезагрузки.</p>
          <pre>{this.state.error}</pre>
          <button
            className="button primary"
            onClick={() => window.location.reload()}
          >
            Перезагрузить
          </button>
        </div>
      );
    return this.props.children;
  }
}
