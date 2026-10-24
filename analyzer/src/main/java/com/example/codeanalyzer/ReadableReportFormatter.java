package com.example.codeanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ReadableReportFormatter {

    private final ObjectMapper objectMapper;

    public ReadableReportFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String format(AnalysisReport report) {

        JsonNode data = objectMapper.valueToTree(report);
        JsonNode issues = data.path("issues");
        JsonNode summary = data.path("summary");

        boolean successful = data.path("analysisSuccessful").asBoolean(false);

        int methods = data.path("methodsAnalyzed").asInt(0);
        int actionable = data.path("actionableIssuesCount").asInt(0);
        int informational = data.path("informationalCount").asInt(0);

        int high = summary.path("high").asInt(0);
        int medium = summary.path("medium").asInt(0);
        int low = countActionableLow(issues);

        StringBuilder result = new StringBuilder();

        result.append("==================================================\n");
        result.append("             JAVA CODE ANALYZER\n");
        result.append("==================================================\n\n");

        if (!successful) {
            result.append("РЕЗУЛЬТАТ: НЕ УДАЛОСЬ ПРОАНАЛИЗИРОВАТЬ КОД\n\n");
            result.append("Проверьте синтаксис Java-кода.\n\n");
            appendIssues(result, issues, false);
            return result.toString();
        }

        result.append("ОБЩИЙ РЕЗУЛЬТАТ\n");
        result.append("--------------------------------------------------\n");

        if (actionable == 0) {
            result.append("Статус: Не обнаружено проблем, требующих исправления\n");
        } else {
            result.append("Статус: Обнаружены проблемы\n");
        }

        JsonNode score = data.path("qualityScore");

        if (!score.isMissingNode() && !score.isNull()) {
            result.append("Оценка качества: ")
                    .append(score.asInt())
                    .append(" / 100\n");
        } else {
            result.append("Оценка качества: недоступна\n");
        }

        result.append("Проанализировано методов: ")
                .append(methods)
                .append("\n\n");

        result.append("НАЙДЕННЫЕ ПРОБЛЕМЫ\n");
        result.append("--------------------------------------------------\n");

        result.append("Высокий приоритет: ").append(high).append("\n");
        result.append("Средний приоритет: ").append(medium).append("\n");
        result.append("Низкий приоритет: ").append(low).append("\n");
        result.append("Всего проблем: ").append(actionable).append("\n");
        result.append("Информационных сообщений: ").append(informational).append("\n\n");

        if (actionable > 0) {

            result.append("ПОДРОБНЫЙ РАЗБОР ПРОБЛЕМ\n\n");

            appendIssues(result, issues, false);

        } else {
            result.append("Проблем, требующих исправления, не обнаружено.\n\n");
        }

        if (informational > 0) {

            result.append("ИНФОРМАЦИЯ ОБ АЛГОРИТМАХ\n");
            result.append("--------------------------------------------------\n\n");

            appendIssues(result, issues, true);
        }

        result.append("==================================================\n");
        result.append("ИТОГ\n");
        result.append("==================================================\n");

        if (high > 0) {

            result.append(
                    "Рекомендуется в первую очередь исправить\n" +
                            "проблемы высокого приоритета.\n"
            );

        } else if (actionable > 0) {

            result.append(
                    "Рекомендуется изучить найденные предупреждения\n" +
                            "и устранить подтверждённые проблемы.\n"
            );

        } else {

            result.append(
                    "В рамках выполненных проверок проблем не найдено.\n"
            );
        }

        result.append(
                "\nПримечание: статический анализ не гарантирует\n" +
                        "полное отсутствие ошибок в программе.\n"
        );

        return result.toString();
    }

    private void appendIssues(
            StringBuilder result,
            JsonNode issues,
            boolean informationalOnly
    ) {

        int number = 0;

        for (JsonNode issue : issues) {

            String type = issue.path("type").asText("");
            boolean info = isInformational(type);

            if (info != informationalOnly) {
                continue;
            }

            number++;

            String severity = issue.path("severity").asText("");
            String method = issue.path("method").asText("");
            int line = issue.path("line").asInt(0);

            String message = issue.path("message").asText("");
            String recommendation = issue.path("recommendation").asText("");
            String complexity = issue.path("complexity").asText("");

            result.append("--------------------------------------------------\n");

            result.append(number)
                    .append(". ")
                    .append(translateType(type))
                    .append("\n");

            result.append("--------------------------------------------------\n");

            if (!info) {
                result.append("Приоритет: ")
                        .append(translateSeverity(severity))
                        .append("\n");
            }

            if (!method.isBlank()) {
                result.append("Метод: ")
                        .append(method)
                        .append("()\n");
            }

            if (line > 0) {
                result.append("Строка: ")
                        .append(line)
                        .append("\n");
            }

            if (info
                    && !complexity.isBlank()
                    && !complexity.equals("UNKNOWN")) {

                result.append("Сложность: ")
                        .append(complexity)
                        .append("\n");
            }

            if (!message.isBlank()) {

                result.append("\nЧто обнаружено:\n");
                result.append(message).append("\n");
            }

            if (!recommendation.isBlank()) {

                result.append("\nРекомендация:\n");
                result.append(recommendation).append("\n");
            }

            result.append("\n");
        }
    }

    private int countActionableLow(JsonNode issues) {

        int count = 0;

        for (JsonNode issue : issues) {

            String type = issue.path("type").asText("");
            String severity = issue.path("severity").asText("");

            if (!isInformational(type) && severity.equals("LOW")) {
                count++;
            }
        }

        return count;
    }

    private boolean isInformational(String type) {

        return switch (type) {

            case "COMPLEXITY_ESTIMATE",
                 "NESTED_LOOP",
                 "RECURSION_DETECTED" -> true;

            default -> false;
        };
    }

    private String translateSeverity(String severity) {

        return switch (severity) {

            case "HIGH" -> "ВЫСОКИЙ";
            case "MEDIUM" -> "СРЕДНИЙ";
            case "LOW" -> "НИЗКИЙ";

            default -> "НЕ ОПРЕДЕЛЁН";
        };
    }

    private String translateType(String type) {

        return switch (type) {

            case "DIVISION_BY_ZERO" ->
                    "Деление на ноль";

            case "NULL_POINTER_DEREFERENCE" ->
                    "Обращение к null";

            case "ARRAY_INDEX_OUT_OF_BOUNDS" ->
                    "Выход за границы массива";

            case "UNREACHABLE_CODE" ->
                    "Недостижимый код";

            case "POTENTIAL_INFINITE_LOOP" ->
                    "Возможно бесконечный цикл";

            case "DUPLICATE_CODE" ->
                    "Дублирование кода";

            case "REPEATED_COMPUTATION" ->
                    "Повторные вычисления";

            case "COMPLEXITY_ESTIMATE" ->
                    "Оценка сложности алгоритма";

            case "NESTED_LOOP" ->
                    "Вложенные циклы";

            case "RECURSION_DETECTED" ->
                    "Обнаружена рекурсия";

            case "PARSE_ERROR" ->
                    "Ошибка синтаксиса Java";

            default -> type.replace("_", " ");
        };
    }
}