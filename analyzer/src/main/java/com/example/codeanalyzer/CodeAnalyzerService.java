package com.example.codeanalyzer;

import org.springframework.stereotype.Service;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

import java.util.ArrayList;
import java.util.List;

@Service
public class CodeAnalyzerService {

    public List<String> analyze(String sourceCode) {
        List<String> findings = new ArrayList<>();

        try {
            CompilationUnit unit =
                    dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            unit.findAll(MethodDeclaration.class).forEach(method -> {
                String methodName = method.getNameAsString();

                int forLoops = method.findAll(ForStmt.class).size();
                int whileLoops = method.findAll(WhileStmt.class).size();

                if (forLoops + whileLoops > 0) {
                    findings.add(
                            "Метод '" + methodName
                                    + "' содержит циклы: for = "
                                    + forLoops + ", while = "
                                    + whileLoops
                    );
                }

                if (forLoops + whileLoops >= 2) {
                    findings.add(
                            "Метод '" + methodName
                                    + "' содержит несколько циклов. "
                                    + "Проверь, не вложены ли они друг в друга."
                    );
                }
            });

            if (findings.isEmpty()) {
                findings.add(
                        "Методы с циклами for или while не найдены."
                );
            }

        } catch (Exception e) {
            findings.add("Не удалось разобрать Java-код: "
                    + e.getMessage());
        }

        return findings;
    }
}