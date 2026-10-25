# Соответствие официальному кейсу AXENIX

Источник: предоставленный PDF «Java Blueprints / AXENIX CASE BRIEF», все 11 пронумерованных страниц и дополнительная страница «Общая инфо». В PDF упомянут отдельный технический документ с полным перечнем конструкций; он не был предоставлен. Эта матрица покрывает именно доступный PDF, без предположения о требованиях отсутствующего документа.

| Требование PDF | Реализация | Ограничение / способ проверки |
|---|---|---|
| Загрузка монолита ≈1000 строк | UTF-8 upload, MonolithDemo 1430 строк | JUnit roundtrip, HTTP интеграция, ручная загрузка |
| Разобрать Java | JavaParser Java 21 | Синтаксический AST, разрешение типов — javac отдельно |
| Интерактивный граф | Внутрипроцедурный CFG, React Flow | Отдельный выбор метода, динамические вызовы атомарны |
| Править граф | replace / insertBefore / delete | Только ограниченные операции в допустимом контексте; javac gate |
| Собрать Java | Сохранённый исходник + проверенные замены | Нет произвольной генерации из свободно нарисованного графа |
| Двусторонняя синхронизация | Debounced parse, AST graph edit, переходы строка/узел | Последний корректный граф сохраняется при ошибке текста и помечается устаревшим |
| Запустить и сравнить | Docker javac/java, одинаковые входы | stdout + stderr + exit code; конечные тесты не доказательство |
| Классы, интерфейсы | AST, декларации, методы, конструкторы | Поддержка JDK javac; типы не разворачиваются в отдельную class diagram |
| Примитивы, обёртки, массивы | Сохранение выражений и компиляция | ScopeShowcase покрывает восемь примитивов, Integer, массив |
| Арифметика, логика, сравнения, приведение | Выражения узлов | ScopeShowcase + проверка компиляции |
| if/else, switch | Ветки true/false, case/default и fall-through | Core JUnit и ScopeShowcase |
| for/foreach/while/do | Циклические рёбра, инициализация и update for | Core JUnit и ScopeShowcase |
| break/continue/return | Отдельные типы и целевые рёбра | Continue for направлен в update; break switch — за switch |
| Методы, конструкторы, поля, static | AST, список methods и declarations | Выполнение исходных конструкций не меняется |
| try/catch/finally/throw | Сохранение Java и атомарный регион исключений | **Частичная визуализация:** внутренний CFG региона скрыт; самостоятельный throw вне региона показан отдельно |
| Object, String, Math, System | Атомарные выражения/вызовы JDK | Реальное выполнение ScopeShowcase |
| System.in/out/err | stdin контейнера, раздельные stdout/stderr | UTF-8 сравнение; stderr ошибок и ненулевой exit учитываются |
| Scanner, Formatter, String.format, printf | Реальные JDK 21 вызовы | ScopeShowcase |
| StandardCharsets и кириллица | UTF-8 upload, javac, JVM, JSON | ScopeShowcase, Unicode JUnit, HTTP test |
| Collection / Iterable / List / Set / Map | Сохранены в AST, JDK execution | ScopeShowcase |
| ArrayList / LinkedList / HashSet / TreeSet / HashMap / TreeMap | Выражения и вызовы стандартной библиотеки | ScopeShowcase |
| Iterator / Collections / Arrays / Comparator / Optional | Стандартная библиотека Java 21 | ScopeShowcase |
| List.of / Set.of / Map.of | Стандартная библиотека Java 21 | ScopeShowcase |
| Checked и unchecked исключения | IOException throws, custom exception, multi-catch, finally | ScopeShowcase; консервативная визуализация региона |
| Собственное исключение наследованием | CaseException extends Exception | Компиляция и реальные запуски ScopeShowcase |
| Типы java.lang / java.util / java.io | Без whitelist синтаксиса: сохраняются и компилируются JDK | Анализ не претендует на полный semantic model JDK |
| Строка + узел диагностики | issue.line + issue.nodeId | HTTP/MockMvc и browser |
| Объяснение + риск + рекомендация | Существующие AST-анализаторы | Эвристические предупреждения, возможны ложные срабатывания |
| Big O метода/фрагмента | Распознанные циклы, вложенность, UNKNOWN | Без symbol solver, без доказательства стоимости вызовов |
| Повторные вычисления, дубли, bottlenecks | RepeatedComputationAnalyzer / DuplicateCodeAnalyzer / ComplexityAnalyzer | Предложения не применяются автоматически |
| Оптимизация/декомпозиция при необходимости | Выделение самостоятельных private static методов | **Частично:** нет автоматической оптимизации алгоритмов и произвольной stateful декомпозиции |
| Сборка + автотесты после преобразования | Compile gate графа; quality gate рефакторинга | Тесты проверяют только заданные входы |
| Рефакторинг в несколько Java-файлов | Обёртки + Component.java, diff, ZIP, undo | Самостоятельные методы, отказ при зависимостях |
| Веб-демо и инструкция | React/FastAPI/Java, run.sh, Compose | Точные результаты среды — VERIFICATION.md |
| Исходники, архитектура, scope, ограничения | README и docs | История исходных модулей сохранена насколько была доступна |
| Полный демонстрационный путь | DEMO.md | Реальный API + Docker; без фиктивных successful статусов |

Бонусы (record, enum, generics, лямбды, streams) синтаксически принимаются JavaParser/JDK 21 и сохраняются в исходнике; отдельные обещания полноценного CFG для их внутренней семантики не даются. Приоритет — обязательная база.

Многопоточность, reflection, файловые операции, сеть и время указаны в PDF как вне рамок оценки. Runner намеренно блокирует сеть и host filesystem. Отдельные тесты java.nio.file/java.net используются только для проверки изоляции.

## Перепроверка 25.10.2026

См. FINAL_CRITERIA_AUDIT.md: 83 разных теста и повтор 16 интеграционных на сервере. Новая SVG-геометрия отличает условия/циклы, операции, ввод-вывод, начало/конец и вызовы методов. Обновление визуализации не расширяет семантический scope try/catch/finally или stateful-рефакторинга.
