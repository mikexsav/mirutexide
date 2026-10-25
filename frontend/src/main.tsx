import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import AccessGate from "./AccessGate";
import ErrorBoundary from "./ErrorBoundary";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary>
      <AccessGate />
    </ErrorBoundary>
  </StrictMode>,
);
