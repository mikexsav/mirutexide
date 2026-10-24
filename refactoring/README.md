# Java Refactor Assistant

Небольшой автономный модуль на Java 17 для анализа монолитных `.java` файлов и безопасного выделения статических утилитарных методов в отдельный класс.

## Возможности

- Структурный разбор объявлений методов и вызовов без внешних библиотек.
- Группировка методов по связям вызовов и общим идентификаторам.
- Консервативное выделение группы `static`-методов в отдельный класс с делегирующими методами-обёртками в исходном классе.
- Предпросмотр unified diff, запрос подтверждения, компиляционная проверка через `javac`.
- Атомарное применение: при ошибке компиляции исходный файл восстанавливается.
- `undo` восстанавливает предыдущую версию по созданной резервной копии.

Ограничение безопасности: автоматическое выделение поддерживает только статические методы без generic-параметров и без ссылок на поля класса; сложные случаи намеренно отклоняются. Передача на поведенческие проверки предоставляется через команду `verify` с передачей каталога проекта внешней команде проверок.

## Сборка и запуск

```sh
mvn package
java -jar target/java-refactor-assistant-1.0.0.jar help
```

Команды:

```sh
java -jar target/java-refactor-assistant-1.0.0.jar analyze src/main/java/example/Large.java
java -jar target/java-refactor-assistant-1.0.0.jar extract src/main/java/example/Large.java GroupName --preview
java -jar target/java-refactor-assistant-1.0.0.jar extract src/main/java/example/Large.java GroupName --apply
java -jar target/java-refactor-assistant-1.0.0.jar undo src/main/java/example/Large.java
java -jar target/java-refactor-assistant-1.0.0.jar verify /path/to/project -- ./mvnw test
```

Группы для `extract` берутся из вывода `analyze`. Команда требует явного `--apply`; без него выполняется только предпросмотр. Резервные копии сохраняются рядом с исходником в `.refactor-backups/`.

## Интеграция Mirutex

Для web используется новый `AstRefactoring` с JavaParser, строгими ограничениями зависимостей и обязательным Docker compile/compare gate в backend. Первоначальный regex CLI сохранён как legacy-инструмент; он не используется интерфейсом и не обладает всеми ограничениями AST-фасада. Основная документация и границы безопасных автоматических изменений: [../README.md](../README.md).
