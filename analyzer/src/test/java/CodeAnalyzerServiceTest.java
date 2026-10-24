package com.example.codeanalyzer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAnalyzerServiceTest {

    @Test
    void shouldDetectForLoop() {
        CodeAnalyzerService service = new CodeAnalyzerService();

        String sourceCode = """
                public class Example {
                    public void calculate() {
                        for (int i = 0; i < 10; i++) {
                            System.out.println(i);
                        }
                    }
                }
                """;

        List<String> result = service.analyze(sourceCode);

        assertTrue(
                result.stream().anyMatch(
                        finding -> finding.contains("calculate")
                )
        );
    }
}