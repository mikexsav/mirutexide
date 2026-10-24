package com.example.codeanalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;

@Component
public class DivisionByZeroAnalyzer {

    public List<AnalysisIssue> analyze(String sourceCode) {

        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            for (MethodDeclaration method :
                    unit.findAll(MethodDeclaration.class)) {

                if (method.getBody().isEmpty()) {
                    continue;
                }

                Map<String, BigInteger> values = new HashMap<>();

                analyzeBlock(
                        method.getBody().get(),
                        values,
                        method.getNameAsString(),
                        issues
                );
            }

        } catch (Exception ignored) {
            // Синтаксические ошибки обрабатывает JavaSyntaxValidator.
        }

        return issues;
    }

    private void analyzeBlock(
            BlockStmt block,
            Map<String, BigInteger> values,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        Set<String> declaredVariables = new HashSet<>();
        Map<String, BigInteger> shadowedVariables = new HashMap<>();

        for (Statement statement : block.getStatements()) {

            if (statement.isExpressionStmt()) {

                analyzeExpression(
                        statement.asExpressionStmt().getExpression(),
                        values,
                        methodName,
                        issues,
                        declaredVariables,
                        shadowedVariables
                );

            } else if (statement.isIfStmt()) {

                analyzeIf(
                        statement.asIfStmt(),
                        values,
                        methodName,
                        issues
                );

            } else if (statement.isBlockStmt()) {

                // Изменения внешних переменных внутри
                // обычного блока должны сохраняться.
                analyzeBlock(
                        statement.asBlockStmt(),
                        values,
                        methodName,
                        issues
                );

            } else if (statement.isReturnStmt()) {

                statement.asReturnStmt()
                        .getExpression()
                        .ifPresent(expression -> {
                            invalidateMutations(expression, values);
                            checkDivisions(
                                    expression,
                                    values,
                                    methodName,
                                    issues
                            );
                        });

                break;

            } else if (statement.isThrowStmt()) {

                Expression expression =
                        statement.asThrowStmt().getExpression();

                invalidateMutations(expression, values);

                checkDivisions(
                        expression,
                        values,
                        methodName,
                        issues
                );

                break;

            } else {

                // Для сложных конструкций, например циклов,
                // пока не сохраняем неподтверждённые значения.
                values.clear();
            }
        }

        // Восстанавливаем внешние переменные,
        // которые были затенены объявлениями в блоке.
        for (String name : declaredVariables) {

            values.remove(name);

            if (shadowedVariables.containsKey(name)) {
                values.put(name, shadowedVariables.get(name));
            }
        }
    }

    private void analyzeExpression(
            Expression expression,
            Map<String, BigInteger> values,
            String methodName,
            List<AnalysisIssue> issues,
            Set<String> declaredVariables,
            Map<String, BigInteger> shadowedVariables
    ) {

        if (expression.isVariableDeclarationExpr()) {

            VariableDeclarationExpr declaration =
                    expression.asVariableDeclarationExpr();

            for (var variable : declaration.getVariables()) {

                String name = variable.getNameAsString();

                if (declaredVariables.add(name)
                        && values.containsKey(name)) {

                    shadowedVariables.put(name, values.get(name));
                }

                // Новая переменная не должна наследовать
                // значение одноимённой внешней переменной.
                values.remove(name);

                if (variable.getInitializer().isEmpty()) {
                    continue;
                }

                Expression initializer =
                        variable.getInitializer().get();

                invalidateMutations(initializer, values);

                checkDivisions(
                        initializer,
                        values,
                        methodName,
                        issues
                );

                evaluate(initializer, values)
                        .ifPresent(value -> values.put(name, value));
            }

        } else if (expression.isAssignExpr()) {

            AssignExpr assignment = expression.asAssignExpr();

            // Побочные изменения внутри правой части
            // делают прежние значения ненадёжными.
            invalidateMutations(assignment.getValue(), values);

            checkDivisions(
                    assignment,
                    values,
                    methodName,
                    issues
            );

            if (assignment.getTarget().isNameExpr()) {

                String name = assignment.getTarget()
                        .asNameExpr()
                        .getNameAsString();

                Optional<BigInteger> newValue = Optional.empty();

                if (assignment.getOperator()
                        == AssignExpr.Operator.ASSIGN) {

                    newValue = evaluate(
                            assignment.getValue(),
                            values
                    );
                }

                values.remove(name);

                newValue.ifPresent(value ->
                        values.put(name, value)
                );
            }

        } else {

            // Исправление:
            // НЕ удаляем все значения после каждого выражения.
            //
            // System.out.println(x) не изменяет локальную
            // примитивную переменную x.
            //
            // Но x++, x-- и присваивания внутри выражений
            // могут изменить известные значения.

            invalidateMutations(expression, values);

            checkDivisions(
                    expression,
                    values,
                    methodName,
                    issues
            );
        }
    }

    private void analyzeIf(
            IfStmt ifStmt,
            Map<String, BigInteger> values,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        invalidateMutations(ifStmt.getCondition(), values);

        checkDivisions(
                ifStmt.getCondition(),
                values,
                methodName,
                issues
        );

        Map<String, BigInteger> thenValues =
                new HashMap<>(values);

        Map<String, BigInteger> elseValues =
                new HashMap<>(values);

        analyzeBranch(
                ifStmt.getThenStmt(),
                thenValues,
                methodName,
                issues
        );

        ifStmt.getElseStmt().ifPresent(elseStatement ->
                analyzeBranch(
                        elseStatement,
                        elseValues,
                        methodName,
                        issues
                )
        );

        values.clear();

        // Сохраняем только значения, одинаковые
        // после обеих веток.
        for (Map.Entry<String, BigInteger> entry :
                thenValues.entrySet()) {

            String name = entry.getKey();

            if (elseValues.containsKey(name)
                    && entry.getValue().equals(
                    elseValues.get(name)
            )) {

                values.put(name, entry.getValue());
            }
        }
    }

    private void analyzeBranch(
            Statement statement,
            Map<String, BigInteger> values,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        if (statement.isBlockStmt()) {

            analyzeBlock(
                    statement.asBlockStmt(),
                    values,
                    methodName,
                    issues
            );

        } else {

            BlockStmt temporaryBlock = new BlockStmt();
            temporaryBlock.addStatement(statement.clone());

            analyzeBlock(
                    temporaryBlock,
                    values,
                    methodName,
                    issues
            );
        }
    }

    private void invalidateMutations(
            Node node,
            Map<String, BigInteger> values
    ) {

        for (AssignExpr assignment :
                node.findAll(AssignExpr.class)) {

            if (assignment.getTarget().isNameExpr()) {

                String name = assignment.getTarget()
                        .asNameExpr()
                        .getNameAsString();

                values.remove(name);
            }
        }

        for (UnaryExpr unary :
                node.findAll(UnaryExpr.class)) {

            if (!unary.getExpression().isNameExpr()) {
                continue;
            }

            switch (unary.getOperator()) {

                case PREFIX_INCREMENT,
                     PREFIX_DECREMENT,
                     POSTFIX_INCREMENT,
                     POSTFIX_DECREMENT -> {

                    String name = unary.getExpression()
                            .asNameExpr()
                            .getNameAsString();

                    values.remove(name);
                }

                default -> {
                }
            }
        }
    }

    private void checkDivisions(
            Node node,
            Map<String, BigInteger> values,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        for (BinaryExpr binary :
                node.findAll(BinaryExpr.class)) {

            if (binary.getOperator() != BinaryExpr.Operator.DIVIDE
                    && binary.getOperator()
                    != BinaryExpr.Operator.REMAINDER) {

                continue;
            }

            Optional<BigInteger> divisor =
                    evaluate(binary.getRight(), values);

            if (divisor.isPresent()
                    && divisor.get().signum() == 0) {

                issues.add(new AnalysisIssue(
                        "DIVISION_BY_ZERO",
                        "HIGH",
                        methodName,
                        getLine(binary),
                        "UNKNOWN",
                        "Обнаружено деление или вычисление остатка "
                                + "с делителем, равным нулю.",
                        "Убедитесь, что делитель не равен нулю "
                                + "перед выполнением операции."
                ));
            }
        }

        for (AssignExpr assignment :
                node.findAll(AssignExpr.class)) {

            if (assignment.getOperator()
                    != AssignExpr.Operator.DIVIDE
                    && assignment.getOperator()
                    != AssignExpr.Operator.REMAINDER) {

                continue;
            }

            Optional<BigInteger> divisor =
                    evaluate(assignment.getValue(), values);

            if (divisor.isPresent()
                    && divisor.get().signum() == 0) {

                issues.add(new AnalysisIssue(
                        "DIVISION_BY_ZERO",
                        "HIGH",
                        methodName,
                        getLine(assignment),
                        "UNKNOWN",
                        "Составная операция деления "
                                + "использует нулевой делитель.",
                        "Проверьте делитель перед операцией."
                ));
            }
        }
    }

    private Optional<BigInteger> evaluate(
            Expression expression,
            Map<String, BigInteger> values
    ) {

        try {

            if (expression.isIntegerLiteralExpr()) {

                String text = expression
                        .asIntegerLiteralExpr()
                        .getValue()
                        .replace("_", "");

                if (text.startsWith("0x")
                        || text.startsWith("0X")) {

                    return Optional.of(
                            new BigInteger(text.substring(2), 16)
                    );
                }

                if (text.startsWith("0b")
                        || text.startsWith("0B")) {

                    return Optional.of(
                            new BigInteger(text.substring(2), 2)
                    );
                }

                return Optional.of(new BigInteger(text));
            }

            if (expression.isNameExpr()) {

                return Optional.ofNullable(
                        values.get(
                                expression.asNameExpr()
                                        .getNameAsString()
                        )
                );
            }

            if (expression.isEnclosedExpr()) {

                return evaluate(
                        expression.asEnclosedExpr().getInner(),
                        values
                );
            }

            if (expression.isUnaryExpr()) {

                UnaryExpr unary = expression.asUnaryExpr();

                Optional<BigInteger> value =
                        evaluate(unary.getExpression(), values);

                if (value.isEmpty()) {
                    return Optional.empty();
                }

                if (unary.getOperator()
                        == UnaryExpr.Operator.MINUS) {

                    return Optional.of(value.get().negate());
                }

                if (unary.getOperator()
                        == UnaryExpr.Operator.PLUS) {

                    return value;
                }
            }

            if (expression.isBinaryExpr()) {

                BinaryExpr binary = expression.asBinaryExpr();

                Optional<BigInteger> left =
                        evaluate(binary.getLeft(), values);

                Optional<BigInteger> right =
                        evaluate(binary.getRight(), values);

                if (left.isEmpty() || right.isEmpty()) {
                    return Optional.empty();
                }

                return switch (binary.getOperator()) {

                    case PLUS ->
                            Optional.of(
                                    left.get().add(right.get())
                            );

                    case MINUS ->
                            Optional.of(
                                    left.get().subtract(right.get())
                            );

                    case MULTIPLY ->
                            Optional.of(
                                    left.get().multiply(right.get())
                            );

                    default -> Optional.empty();
                };
            }

        } catch (Exception ignored) {
            // Точное значение определить не удалось.
        }

        return Optional.empty();
    }

    private int getLine(Node node) {

        return node.getBegin()
                .map(position -> position.line)
                .orElse(1);
    }
}