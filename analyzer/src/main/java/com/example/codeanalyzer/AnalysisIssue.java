package com.example.codeanalyzer;

public class AnalysisIssue {

    private String type;
    private String severity;
    private String method;
    private int line;
    private String complexity;
    private String message;
    private String recommendation;

    public AnalysisIssue(
            String type,
            String severity,
            String method,
            int line,
            String complexity,
            String message,
            String recommendation
    ) {
        this.type = type;
        this.severity = severity;
        this.method = method;
        this.line = line;
        this.complexity = complexity;
        this.message = message;
        this.recommendation = recommendation;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMethod() {
        return method;
    }

    public int getLine() {
        return line;
    }

    public String getComplexity() {
        return complexity;
    }

    public String getMessage() {
        return message;
    }

    public String getRecommendation() {
        return recommendation;
    }
}