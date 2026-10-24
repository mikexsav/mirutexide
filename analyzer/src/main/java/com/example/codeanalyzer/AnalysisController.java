package com.example.codeanalyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private final ComplexityAnalyzer complexityAnalyzer;
    private final RepeatedComputationAnalyzer repeatedComputationAnalyzer;
    private final DuplicateCodeAnalyzer duplicateCodeAnalyzer;
    private final DivisionByZeroAnalyzer divisionByZeroAnalyzer;
    private final NullPointerAnalyzer nullPointerAnalyzer;
    private final ArrayBoundsAnalyzer arrayBoundsAnalyzer;
    private final UnreachableCodeAnalyzer unreachableCodeAnalyzer;
    private final InfiniteLoopAnalyzer infiniteLoopAnalyzer;
    private final JavaSyntaxValidator syntaxValidator;

    private final ReadableReportFormatter readableReportFormatter;

    public AnalysisController(
            ComplexityAnalyzer complexityAnalyzer,
            RepeatedComputationAnalyzer repeatedComputationAnalyzer,
            DuplicateCodeAnalyzer duplicateCodeAnalyzer,
            DivisionByZeroAnalyzer divisionByZeroAnalyzer,
            NullPointerAnalyzer nullPointerAnalyzer,
            ArrayBoundsAnalyzer arrayBoundsAnalyzer,
            UnreachableCodeAnalyzer unreachableCodeAnalyzer,
            InfiniteLoopAnalyzer infiniteLoopAnalyzer,
            JavaSyntaxValidator syntaxValidator,
            ReadableReportFormatter readableReportFormatter
    ) {

        this.complexityAnalyzer = complexityAnalyzer;
        this.repeatedComputationAnalyzer = repeatedComputationAnalyzer;
        this.duplicateCodeAnalyzer = duplicateCodeAnalyzer;
        this.divisionByZeroAnalyzer = divisionByZeroAnalyzer;
        this.nullPointerAnalyzer = nullPointerAnalyzer;
        this.arrayBoundsAnalyzer = arrayBoundsAnalyzer;
        this.unreachableCodeAnalyzer = unreachableCodeAnalyzer;
        this.infiniteLoopAnalyzer = infiniteLoopAnalyzer;
        this.syntaxValidator = syntaxValidator;
        this.readableReportFormatter = readableReportFormatter;
    }

    @PostMapping
    public ResponseEntity<?> analyze(
            @RequestBody(required = false) AnalysisRequest request
    ) {

        if (request == null
                || request.getCode() == null
                || request.getCode().isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Поле 'code' не должно быть пустым."
                    )
            );
        }

        AnalysisReport report = createReport(request.getCode());

        return ResponseEntity.ok(report);
    }

    @PostMapping(
            value = "/readable",
            produces = "text/plain;charset=UTF-8"
    )
    public ResponseEntity<String> analyzeReadable(
            @RequestBody(required = false) AnalysisRequest request
    ) {

        MediaType textMediaType =
                MediaType.parseMediaType("text/plain;charset=UTF-8");

        if (request == null
                || request.getCode() == null
                || request.getCode().isBlank()) {

            return ResponseEntity.badRequest()
                    .contentType(textMediaType)
                    .body("Ошибка: поле 'code' не должно быть пустым.");
        }

        AnalysisReport report = createReport(request.getCode());

        String readableReport = readableReportFormatter.format(report);

        return ResponseEntity.ok()
                .contentType(textMediaType)
                .body(readableReport);
    }

    private AnalysisReport createReport(String code) {

        JavaSyntaxValidator.ValidationResult validation =
                syntaxValidator.validate(code);

        if (!validation.valid()) {

            return new AnalysisReport(
                    0,
                    validation.issues()
            );
        }

        CompilationUnit unit = validation.compilationUnit();

        int methodsAnalyzed =
                unit.findAll(MethodDeclaration.class).size();

        List<AnalysisIssue> issues = new ArrayList<>();

        issues.addAll(complexityAnalyzer.analyze(code));
        issues.addAll(repeatedComputationAnalyzer.analyze(code));
        issues.addAll(duplicateCodeAnalyzer.analyze(code));
        issues.addAll(divisionByZeroAnalyzer.analyze(code));
        issues.addAll(nullPointerAnalyzer.analyze(code));
        issues.addAll(arrayBoundsAnalyzer.analyze(code));
        issues.addAll(unreachableCodeAnalyzer.analyze(code));
        issues.addAll(infiniteLoopAnalyzer.analyze(code));

        return new AnalysisReport(
                methodsAnalyzed,
                issues
        );
    }

    public static class AnalysisRequest {

        private String code;

        public AnalysisRequest() {
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }
}