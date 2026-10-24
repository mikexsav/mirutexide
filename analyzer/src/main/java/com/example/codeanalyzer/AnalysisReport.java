package com.example.codeanalyzer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalysisReport {

    private final int methodsAnalyzed;
    private final int issuesFound;
    private final List<AnalysisIssue> issues;
    private final Map<String, Integer> summary;

    private final Integer qualityScore;

    private final int informationalCount;
    private final int actionableIssuesCount;
    private final String scoreExplanation;

    private final boolean analysisSuccessful;

    public AnalysisReport(
            int methodsAnalyzed,
            List<AnalysisIssue> issues
    ) {

        this.methodsAnalyzed = methodsAnalyzed;
        this.issues = List.copyOf(issues);
        this.issuesFound = issues.size();

        int high = 0;
        int medium = 0;
        int low = 0;

        int informational = 0;
        int actionable = 0;

        int penalty = 0;

        boolean syntaxErrorFound = false;

        for (AnalysisIssue issue : issues) {

            String severity = issue.getSeverity();
            String type = issue.getType();

            if ("PARSE_ERROR".equals(type)) {
                syntaxErrorFound = true;
            }

            switch (severity) {
                case "HIGH" -> high++;
                case "MEDIUM" -> medium++;
                case "LOW" -> low++;
                default -> {
                }
            }

            if (isInformational(type)) {
                informational++;
                continue;
            }

            actionable++;

            switch (severity) {
                case "HIGH" -> penalty += 15;
                case "MEDIUM" -> penalty += 7;
                case "LOW" -> penalty += 2;
                default -> {
                }
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();

        counts.put("high", high);
        counts.put("medium", medium);
        counts.put("low", low);

        this.summary = Map.copyOf(counts);

        this.informationalCount = informational;
        this.actionableIssuesCount = actionable;

        this.analysisSuccessful = !syntaxErrorFound;

        if (syntaxErrorFound) {

            this.qualityScore = null;

            this.scoreExplanation =
                    "Оценка качества недоступна, поскольку "
                            + "Java-код содержит синтаксические ошибки. "
                            + "Исправьте ошибки и повторите анализ.";

        } else {

            this.qualityScore = Math.max(
                    0,
                    Math.min(100, 100 - penalty)
            );

            this.scoreExplanation =
                    "Эвристическая оценка: 100 баллов минус штрафы "
                            + "за потенциальные проблемы. "
                            + "Информационные сообщения о сложности, "
                            + "вложенных циклах и рекурсии "
                            + "не снижают балл. "
                            + "Оценка не гарантирует отсутствие ошибок.";
        }
    }

    private boolean isInformational(String type) {

        return "COMPLEXITY_ESTIMATE".equals(type)
                || "NESTED_LOOP".equals(type)
                || "RECURSION_DETECTED".equals(type);
    }

    public int getMethodsAnalyzed() {
        return methodsAnalyzed;
    }

    public int getIssuesFound() {
        return issuesFound;
    }

    public List<AnalysisIssue> getIssues() {
        return issues;
    }

    public Map<String, Integer> getSummary() {
        return summary;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public int getInformationalCount() {
        return informationalCount;
    }

    public int getActionableIssuesCount() {
        return actionableIssuesCount;
    }

    public String getScoreExplanation() {
        return scoreExplanation;
    }

    public boolean isAnalysisSuccessful() {
        return analysisSuccessful;
    }
}