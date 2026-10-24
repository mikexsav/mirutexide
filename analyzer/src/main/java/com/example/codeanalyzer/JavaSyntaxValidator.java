package com.example.codeanalyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaSyntaxValidator {

    private static final Pattern LINE_PATTERN =
            Pattern.compile("\\(line\\s+(\\d+),\\s*col\\s+\\d+\\)");

    public record ValidationResult(
            boolean valid,
            CompilationUnit compilationUnit,
            List<AnalysisIssue> issues
    ) {
    }

    public ValidationResult validate(String sourceCode) {

        JavaParser parser = new JavaParser(
                new ParserConfiguration()
                        .setLanguageLevel(
                                ParserConfiguration.LanguageLevel.JAVA_21
                        )
        );

        ParseResult<CompilationUnit> result = parser.parse(sourceCode);

        if (result.isSuccessful() && result.getResult().isPresent()) {
            return new ValidationResult(
                    true,
                    result.getResult().get(),
                    List.of()
            );
        }

        List<AnalysisIssue> issues = new ArrayList<>();

        for (Problem problem : result.getProblems()) {

            String description = problem.getVerboseMessage();

            int line = extractLine(description);

            issues.add(new AnalysisIssue(
                    "PARSE_ERROR",
                    "HIGH",
                    "unknown",
                    line,
                    "UNKNOWN",
                    "Синтаксическая ошибка Java: "
                            + problem.getMessage(),
                    "Проверьте код рядом со строкой "
                            + line
                            + ". Убедитесь, что правильно "
                            + "расставлены скобки, точки с запятой "
                            + "и другие элементы синтаксиса."
            ));
        }

        if (issues.isEmpty()) {
            issues.add(new AnalysisIssue(
                    "PARSE_ERROR",
                    "HIGH",
                    "unknown",
                    1,
                    "UNKNOWN",
                    "Не удалось разобрать Java-код.",
                    "Проверьте синтаксис исходного кода."
            ));
        }

        return new ValidationResult(
                false,
                null,
                issues
        );
    }

    private int extractLine(String description) {

        Matcher matcher = LINE_PATTERN.matcher(description);

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Используем значение по умолчанию.
            }
        }

        return 1;
    }
}