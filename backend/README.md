# Backend

FastAPI orchestration и Docker runner Mirutex. Общий запуск — `../run.sh` из корня проекта. Заглушки исходного модуля заменены вызовами Java engine.

Контракт и ошибки: [../docs/API.md](../docs/API.md). Ограничения изоляции, конфигурация и запуск Compose: [../README.md](../README.md).

Unit-тесты с mock runner: `PYTHONPATH=backend .venv/bin/pytest backend -q` из корня. Реальные проверки: `.venv/bin/pytest testing/test_integration.py -q -s` при запущенном приложении.
