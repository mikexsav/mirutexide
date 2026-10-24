# Mirutex Frontend

React / TypeScript / Monaco / React Flow / Dagre. Общий запуск и проверка: [../README.md](../README.md).

Для отдельной разработки: `npm ci && npm run dev`. Vite проксирует `/api` и `/health` на 127.0.0.1:8000. Monaco и его worker поставляются локально, без editor CDN. `npm run build` проверяет TypeScript и создаёт production bundle. DOM интерфейса и CSS поддерживают desktop/mobile и обе темы.
