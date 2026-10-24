# API и модель данных

FastAPI публикует OpenAPI на `/docs`. Source-request: `{ "source": "...Java...", "files": {}, "stdin": "" }`.

| Метод | Путь | Действие |
|---|---|---|
| GET | `/health` | Фактическое состояние API, engine, runner image |
| POST | `/api/files/upload` | Multipart `file`, UTF-8 Java; возвращает исходник и граф |
| POST | `/api/graph/parse` | Source-request → Graph |
| POST | `/api/graph/generate` | Graph → заново проверенный Graph и Java source |
| POST | `/api/graph/edit` | `{graph, files?, operation:{type,nodeId,code}}` → Graph после компиляции |
| POST | `/api/analysis` | Source-request → issues, methodsAnalyzed, summary, note |
| POST | `/api/refactor` | Source-request + `inputs` → preview, files, candidates, validation, accepted |
| POST | `/api/compile` | Source-request → реальный javac result, без запуска main |
| POST | `/api/execute` | Source-request → реальный результат запуска main |
| POST | `/api/tests/compare` | `{original,modified,original_files?,modified_files?,inputs}` → результаты сценариев |
| POST | `/api/files/download` | Source-request → `.java` / ZIP |

`Graph` версии `1.0` содержит `source`, SHA-256 `sourceHash`, `filename`, `mainClass`, `nodes`, `edges`, `methods`, `declarations`, `limitations`. Клиент хранит исходный Graph для отправки изменений. Сериализация/десериализация — обычный JSON, DTO record на Java, типизированный контракт TypeScript.

Узел: `id`, `kind`, `label`, `code`, `method`, `line`, `endLine`, `start`, `end`, `editable`, `depth`. Диапазон `start/end` — полуоткрытые UTF-16 смещения исходной строки, строки начинаются с 1. Типы: start/end, operation/call, condition/loopCondition, return/throw/break/continue, exception. Служебные узлы могут ссылаться на диапазон содержащей конструкции, но всегда read-only.

Ребро: `id`, `source`, `target`, `label`. Рёбра имеют управление потоком (true/false, next/exhausted, break/continue, repeat, case/default), а не отношение родитель/потомок AST. Связи `possible abrupt completion` атомарных регионов — консервативное описание; полный CFG исключений не строится.

Поддерживаемые операции: `replace`, `insertBefore`, `delete`. Для replace условия нужен Java expression; обычного узла — expression statement того же вида управления; return/throw сохраняют свой тип. Insert/delete допускаются только для обычных операторов в `{}`. JavaParser и затем javac проверяют результат. Ошибка оставляет исходный проект клиента без изменений.

Изменение структуры/диапазонов/типов/ID/рёбер в Graph вручную отклоняется. Для `generate` узлы изменяются только через `code`, а топология сверяется с заново разобранным исходником. Если source изменился при преобразовании, выполняется проверка компиляции.

Runner-result: `compiled`, `exit_code`, `stdout`, `stderr`, `timed_out`, `output_limited`, `phase` (compile / compile_only / execution). Две одинаковые ошибки компиляции, два timeout или два обрезанных вывода никогда не дают успешного сравнения. Одинаковые runtime-исключения с ненулевым кодом могут считаться совпавшим наблюдаемым поведением, но **не допускают** автоматического принятия рефакторинга.

Ошибки: 400 — формат/кодировка файла; 413 — размер; 422 — синтаксис/валидация/неподдерживаемая операция; 503 — недоступен engine/Docker. Внутренний Spring engine работает на 8081, маршруты `/engine/{parse,generate,edit,analyze,refactor}`; в Compose не публикуется на host.
