package com.example.codeanalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class RepeatedComputationAnalyzer {

    private static final Set<String> SAFE_MATH_METHODS = Set.of(
            "abs",
            "sqrt",
            "pow",
            "sin",
            "cos",
            "tan",
            "log",
            "log10",
            "exp",
            "floor",
            "ceil",
            "round",
            "min",
            "max"
    );

    public List<AnalysisIssue> analyze(String sourceCode) {

        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            for (MethodDeclaration method :
                    unit.findAll(MethodDeclaration.class)) {

                if (method.getBody().isEmpty()) {
                    continue;
                }

                analyzeStatement(
                        method.getBody().get(),
                        method.getNameAsString(),
                        issues
                );
            }

        } catch (Exception ignored) {
            // Ошибку синтаксиса обрабатывает ComplexityAnalyzer.
        }

        return issues;
    }

    private void analyzeStatement(
            Statement statement,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        if (statement instanceof BlockStmt block) {

            for (Statement child : block.getStatements()) {
                analyzeStatement(child, methodName, issues);
            }

            return;
        }

        if (statement instanceof ForStmt loop) {

            analyzeLoopBody(
                    loop.getBody(),
                    methodName,
                    issues
            );

            analyzeStatement(loop.getBody(), methodName, issues);
            return;
        }

        if (statement instanceof ForEachStmt loop) {

            analyzeLoopBody(
                    loop.getBody(),
                    methodName,
                    issues
            );

            analyzeStatement(loop.getBody(), methodName, issues);
            return;
        }

        if (statement instanceof WhileStmt loop) {

            analyzeLoopBody(
                    loop.getBody(),
                    methodName,
                    issues
            );

            analyzeStatement(loop.getBody(), methodName, issues);
            return;
        }

        if (statement instanceof DoStmt loop) {

            analyzeLoopBody(
                    loop.getBody(),
                    methodName,
                    issues
            );

            analyzeStatement(loop.getBody(), methodName, issues);
            return;
        }

        if (statement instanceof IfStmt ifStmt) {

            analyzeStatement(
                    ifStmt.getThenStmt(),
                    methodName,
                    issues
            );

            ifStmt.getElseStmt().ifPresent(
                    other -> analyzeStatement(
                            other,
                            methodName,
                            issues
                    )
            );

            return;
        }

        if (statement instanceof SwitchStmt switchStmt) {

            for (SwitchEntry entry : switchStmt.getEntries()) {

                for (Statement child : entry.getStatements()) {
                    analyzeStatement(child, methodName, issues);
                }
            }

            return;
        }

        if (statement instanceof TryStmt tryStmt) {

            analyzeStatement(
                    tryStmt.getTryBlock(),
                    methodName,
                    issues
            );

            for (CatchClause catchClause :
                    tryStmt.getCatchClauses()) {

                analyzeStatement(
                        catchClause.getBody(),
                        methodName,
                        issues
                );
            }

            tryStmt.getFinallyBlock().ifPresent(
                    block -> analyzeStatement(
                            block,
                            methodName,
                            issues
                    )
            );
        }
    }

    private void analyzeLoopBody(
            Statement body,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        /*
         * Анализируем только последовательность
         * операторов непосредственно в теле цикла.
         *
         * Не объединяем вызовы из разных веток if
         * и не смешиваем вызовы вложенных циклов.
         */

        List<Statement> statements;

        if (body instanceof BlockStmt block) {
            statements = new ArrayList<>(block.getStatements());
        } else {
            statements = List.of(body);
        }

        Set<String> reportedCalls = new HashSet<>();

        for (int i = 0; i < statements.size(); i++) {

            Statement firstStatement = statements.get(i);

            List<MethodCallExpr> firstCalls =
                    collectSafeCalls(firstStatement);

            for (MethodCallExpr firstCall : firstCalls) {

                String signature = firstCall.toString();

                if (reportedCalls.contains(signature)) {
                    continue;
                }

                for (int j = i + 1; j < statements.size(); j++) {

                    Statement secondStatement = statements.get(j);

                    if (isControlFlowStatement(secondStatement)) {
                        break;
                    }

                    if (changesDependencies(
                            statements,
                            i,
                            j,
                            firstCall
                    )) {
                        break;
                    }

                    List<MethodCallExpr> secondCalls =
                            collectSafeCalls(secondStatement);

                    boolean repeated = secondCalls.stream()
                            .anyMatch(call ->
                                    call.toString().equals(signature)
                            );

                    if (repeated) {

                        issues.add(new AnalysisIssue(
                                "REPEATED_COMPUTATION",
                                "MEDIUM",
                                methodName,
                                getLine(secondStatement),
                                "UNKNOWN",
                                "В цикле повторяется вычисление "
                                        + signature,
                                "Проверьте возможность сохранить "
                                        + "результат первого вызова "
                                        + "и использовать его повторно. "
                                        + "Оптимизация допустима только "
                                        + "если аргументы и состояние "
                                        + "программы не изменяются."
                        ));

                        reportedCalls.add(signature);
                        break;
                    }
                }
            }
        }
    }

    private List<MethodCallExpr> collectSafeCalls(
            Statement statement
    ) {

        List<MethodCallExpr> result = new ArrayList<>();

        if (isControlFlowStatement(statement)) {
            return result;
        }

        for (MethodCallExpr call :
                statement.findAll(MethodCallExpr.class)) {

            if (isSafeMathCall(call)) {
                result.add(call);
            }
        }

        return result;
    }

    private boolean isSafeMathCall(MethodCallExpr call) {

        if (call.getScope().isEmpty()) {
            return false;
        }

        Expression scope = call.getScope().get();

        if (!(scope instanceof NameExpr nameExpr)) {
            return false;
        }

        if (!nameExpr.getNameAsString().equals("Math")) {
            return false;
        }

        if (!SAFE_MATH_METHODS.contains(
                call.getNameAsString()
        )) {
            return false;
        }

        /*
         * Аргументы должны быть простыми выражениями:
         * переменные, литералы или арифметика.
         *
         * Вызовы других методов в аргументах
         * пока не допускаются.
         */

        for (Expression argument : call.getArguments()) {

            if (!argument.findAll(
                    MethodCallExpr.class
            ).isEmpty()) {
                return false;
            }

            if (!argument.findAll(
                    AssignExpr.class
            ).isEmpty()) {
                return false;
            }

            if (!argument.findAll(
                    UnaryExpr.class,
                    unary -> isModification(unary)
            ).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private boolean changesDependencies(
            List<Statement> statements,
            int firstIndex,
            int secondIndex,
            MethodCallExpr call
    ) {

        Set<String> dependencies = new HashSet<>();

        for (Expression argument : call.getArguments()) {

            for (NameExpr name :
                    argument.findAll(NameExpr.class)) {

                dependencies.add(
                        name.getNameAsString()
                );
            }
        }

        for (int i = firstIndex; i <= secondIndex; i++) {

            Statement statement = statements.get(i);

            if (isControlFlowStatement(statement)) {
                return true;
            }

            for (AssignExpr assignment :
                    statement.findAll(AssignExpr.class)) {

                if (assignment.getTarget().isNameExpr()) {

                    String variable = assignment.getTarget()
                            .asNameExpr()
                            .getNameAsString();

                    if (dependencies.contains(variable)) {
                        return true;
                    }
                }
            }

            for (UnaryExpr unary :
                    statement.findAll(UnaryExpr.class)) {

                if (!isModification(unary)) {
                    continue;
                }

                if (unary.getExpression().isNameExpr()) {

                    String variable = unary.getExpression()
                            .asNameExpr()
                            .getNameAsString();

                    if (dependencies.contains(variable)) {
                        return true;
                    }
                }
            }

            for (VariableDeclarationExpr declaration :
                    statement.findAll(
                            VariableDeclarationExpr.class
                    )) {

                for (var variable :
                        declaration.getVariables()) {

                    if (dependencies.contains(
                            variable.getNameAsString()
                    )) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isModification(UnaryExpr unary) {

        return unary.getOperator()
                == UnaryExpr.Operator.POSTFIX_INCREMENT
                || unary.getOperator()
                == UnaryExpr.Operator.PREFIX_INCREMENT
                || unary.getOperator()
                == UnaryExpr.Operator.POSTFIX_DECREMENT
                || unary.getOperator()
                == UnaryExpr.Operator.PREFIX_DECREMENT;
    }

    private boolean isControlFlowStatement(
            Statement statement
    ) {

        return statement instanceof IfStmt
                || statement instanceof ForStmt
                || statement instanceof ForEachStmt
                || statement instanceof WhileStmt
                || statement instanceof DoStmt
                || statement instanceof SwitchStmt
                || statement instanceof TryStmt
                || statement instanceof ReturnStmt
                || statement instanceof ThrowStmt
                || statement instanceof BreakStmt
                || statement instanceof ContinueStmt;
    }

    private int getLine(Node node) {

        return node.getBegin()
                .map(position -> position.line)
                .orElse(1);
    }
}