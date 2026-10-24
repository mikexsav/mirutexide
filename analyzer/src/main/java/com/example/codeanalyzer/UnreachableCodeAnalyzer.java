package com.example.codeanalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UnreachableCodeAnalyzer {

    public List<AnalysisIssue> analyze(String sourceCode) {

        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            for (MethodDeclaration method :
                    unit.findAll(MethodDeclaration.class)) {

                if (method.getBody().isEmpty()) {
                    continue;
                }

                analyzeBlock(
                        method.getBody().get(),
                        method.getNameAsString(),
                        issues
                );
            }

        } catch (Exception ignored) {
            // Синтаксические ошибки обрабатываются отдельно.
        }

        return issues;
    }

    private void analyzeBlock(
            BlockStmt block,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        boolean terminated = false;

        for (Statement statement : block.getStatements()) {

            if (terminated) {

                issues.add(new AnalysisIssue(
                        "UNREACHABLE_CODE",
                        "MEDIUM",
                        methodName,
                        getLine(statement),
                        "UNKNOWN",
                        "Недостижимый код: инструкция расположена "
                                + "после безусловного завершения "
                                + "текущего блока.",
                        "Удалите недостижимую инструкцию "
                                + "или измените порядок выполнения."
                ));

                continue;
            }

            analyzeNestedBlocks(statement, methodName, issues);

            if (statement.isReturnStmt()
                    || statement.isThrowStmt()
                    || statement.isBreakStmt()
                    || statement.isContinueStmt()) {

                terminated = true;
            }
        }
    }

    private void analyzeNestedBlocks(
            Statement statement,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        if (statement.isBlockStmt()) {

            analyzeBlock(
                    statement.asBlockStmt(),
                    methodName,
                    issues
            );

            return;
        }

        if (statement.isIfStmt()) {

            Statement thenStatement =
                    statement.asIfStmt().getThenStmt();

            analyzeNestedBlocks(
                    thenStatement,
                    methodName,
                    issues
            );

            statement.asIfStmt()
                    .getElseStmt()
                    .ifPresent(elseStatement ->
                            analyzeNestedBlocks(
                                    elseStatement,
                                    methodName,
                                    issues
                            )
                    );

            return;
        }

        if (statement.isWhileStmt()) {

            analyzeNestedBlocks(
                    statement.asWhileStmt().getBody(),
                    methodName,
                    issues
            );

            return;
        }

        if (statement.isForStmt()) {

            analyzeNestedBlocks(
                    statement.asForStmt().getBody(),
                    methodName,
                    issues
            );

            return;
        }

        if (statement.isForEachStmt()) {

            analyzeNestedBlocks(
                    statement.asForEachStmt().getBody(),
                    methodName,
                    issues
            );

            return;
        }

        if (statement.isDoStmt()) {

            analyzeNestedBlocks(
                    statement.asDoStmt().getBody(),
                    methodName,
                    issues
            );
        }
    }

    private int getLine(Node node) {

        return node.getBegin()
                .map(position -> position.line)
                .orElse(1);
    }
}