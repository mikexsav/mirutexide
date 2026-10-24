# Интеграция

Основной контракт находится в [../docs/API.md](../docs/API.md). Все адаптеры подключены к Spring Java engine через ENGINE_URL. Java source исполняется только в одноразовых контейнерах Docker.

Исторические HTTP 501 заменены реальными операциями parse, generate, edit, analyze и refactor. Внутренний порт engine — 8081, API — 8000, frontend/proxy — 5173.
