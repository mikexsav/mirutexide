package com.example.codeanalyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NullPointerAnalyzer {

    private enum NullState {
        NULL,
        NON_NULL,
        UNKNOWN
    }

    private static class FlowState {

        private final Map<String, NullState> variables;
        private boolean reachable;

        FlowState() {
            this.variables = new HashMap<>();
            this.reachable = true;
        }

        FlowState(Map<String, NullState> variables, boolean reachable) {
            this.variables = new HashMap<>(variables);
            this.reachable = reachable;
        }

        FlowState copy() {
            return new FlowState(variables, reachable);
        }
    }

    public List<AnalysisIssue> analyze(String sourceCode) {

        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            for (MethodDeclaration method :
                    unit.findAll(MethodDeclaration.class)) {

                if (method.getBody().isEmpty()) {
                    continue;
                }

                FlowState state = new FlowState();

                analyzeBlock(
                        method.getBody().get(),
                        state,
                        method.getNameAsString(),
                        issues
                );
            }

        } catch (Exception ignored) {
            // JavaSyntaxValidator обрабатывает ошибки синтаксиса.
        }

        return issues;
    }

    private void analyzeBlock(
            BlockStmt block,
            FlowState state,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        Map<String, NullState> shadowed = new HashMap<>();
        Set<String> declared = new HashSet<>();

        for (Statement statement : block.getStatements()) {

            if (!state.reachable) {
                break;
            }

            if (statement.isExpressionStmt()) {

                Expression expression =
                        statement.asExpressionStmt().getExpression();

                if (expression.isVariableDeclarationExpr()) {

                    VariableDeclarationExpr declaration =
                            expression.asVariableDeclarationExpr();

                    for (var variable : declaration.getVariables()) {

                        String name = variable.getNameAsString();

                        if (!declared.contains(name)) {
                            declared.add(name);

                            if (state.variables.containsKey(name)) {
                                shadowed.put(
                                        name,
                                        state.variables.get(name)
                                );
                            }
                        }

                        if (variable.getInitializer().isPresent()) {

                            Expression initializer =
                                    variable.getInitializer().get();

                            checkDereferences(
                                    initializer,
                                    state,
                                    methodName,
                                    issues
                            );

                            state.variables.put(
                                    name,
                                    evaluate(initializer, state)
                            );

                        } else {
                            state.variables.put(
                                    name,
                                    NullState.UNKNOWN
                            );
                        }
                    }

                } else if (expression.isAssignExpr()) {

                    AssignExpr assignment = expression.asAssignExpr();

                    checkDereferences(
                            assignment,
                            state,
                            methodName,
                            issues
                    );

                    if (assignment.getTarget().isNameExpr()) {

                        String name = assignment.getTarget()
                                .asNameExpr()
                                .getNameAsString();

                        if (assignment.getOperator()
                                == AssignExpr.Operator.ASSIGN) {

                            state.variables.put(
                                    name,
                                    evaluate(
                                            assignment.getValue(),
                                            state
                                    )
                            );

                        } else {
                            state.variables.put(
                                    name,
                                    NullState.UNKNOWN
                            );
                        }
                    }

                } else {

                    checkDereferences(
                            expression,
                            state,
                            methodName,
                            issues
                    );
                }

            } else if (statement.isIfStmt()) {

                analyzeIf(
                        statement.asIfStmt(),
                        state,
                        methodName,
                        issues
                );

            } else if (statement.isBlockStmt()) {

                analyzeBlock(
                        statement.asBlockStmt(),
                        state,
                        methodName,
                        issues
                );

            } else if (statement.isReturnStmt()) {

                statement.asReturnStmt()
                        .getExpression()
                        .ifPresent(expression ->
                                checkDereferences(
                                        expression,
                                        state,
                                        methodName,
                                        issues
                                )
                        );

                state.reachable = false;

            } else if (statement.isThrowStmt()) {

                checkDereferences(
                        statement.asThrowStmt().getExpression(),
                        state,
                        methodName,
                        issues
                );

                state.reachable = false;

            } else {

                // Для сложных конструкций пока сбрасываем
                // значения, чтобы не делать ложных выводов.
                state.variables.replaceAll(
                        (name, value) -> NullState.UNKNOWN
                );
            }
        }

        // Удаляем локальные переменные этого блока.
        for (String name : declared) {

            if (shadowed.containsKey(name)) {
                state.variables.put(name, shadowed.get(name));
            } else {
                state.variables.remove(name);
            }
        }
    }

    private void analyzeIf(
            IfStmt ifStmt,
            FlowState state,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        checkDereferences(
                ifStmt.getCondition(),
                state,
                methodName,
                issues
        );

        FlowState thenState = state.copy();
        FlowState elseState = state.copy();

        applyNullCondition(
                ifStmt.getCondition(),
                thenState,
                true
        );

        applyNullCondition(
                ifStmt.getCondition(),
                elseState,
                false
        );

        analyzeBranch(
                ifStmt.getThenStmt(),
                thenState,
                methodName,
                issues
        );

        ifStmt.getElseStmt().ifPresent(elseStatement ->
                analyzeBranch(
                        elseStatement,
                        elseState,
                        methodName,
                        issues
                )
        );

        mergeStates(state, thenState, elseState);
    }

    private void analyzeBranch(
            Statement statement,
            FlowState state,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        if (!state.reachable) {
            return;
        }

        if (statement.isBlockStmt()) {

            analyzeBlock(
                    statement.asBlockStmt(),
                    state,
                    methodName,
                    issues
            );

        } else {

            BlockStmt temporary = new BlockStmt();
            temporary.addStatement(statement.clone());

            analyzeBlock(
                    temporary,
                    state,
                    methodName,
                    issues
            );
        }
    }

    private void applyNullCondition(
            Expression condition,
            FlowState state,
            boolean trueBranch
    ) {

        if (condition.isEnclosedExpr()) {

            applyNullCondition(
                    condition.asEnclosedExpr().getInner(),
                    state,
                    trueBranch
            );

            return;
        }

        if (condition.isUnaryExpr()) {

            UnaryExpr unary = condition.asUnaryExpr();

            if (unary.getOperator()
                    == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {

                applyNullCondition(
                        unary.getExpression(),
                        state,
                        !trueBranch
                );
            }

            return;
        }

        if (!condition.isBinaryExpr()) {
            return;
        }

        BinaryExpr binary = condition.asBinaryExpr();

        boolean equalsNull =
                binary.getOperator() == BinaryExpr.Operator.EQUALS;

        boolean notEqualsNull =
                binary.getOperator() == BinaryExpr.Operator.NOT_EQUALS;

        if (!equalsNull && !notEqualsNull) {
            return;
        }

        String variable = null;

        if (binary.getLeft().isNameExpr()
                && binary.getRight().isNullLiteralExpr()) {

            variable = binary.getLeft()
                    .asNameExpr()
                    .getNameAsString();

        } else if (binary.getRight().isNameExpr()
                && binary.getLeft().isNullLiteralExpr()) {

            variable = binary.getRight()
                    .asNameExpr()
                    .getNameAsString();
        }

        if (variable == null) {
            return;
        }

        boolean isNullBranch =
                equalsNull == trueBranch;

        NullState expected = isNullBranch
                ? NullState.NULL
                : NullState.NON_NULL;

        NullState previous = state.variables.getOrDefault(
                variable,
                NullState.UNKNOWN
        );

        // Если состояние уже известно и противоречит
        // условию, эта ветка недостижима.
        if (previous != NullState.UNKNOWN
                && previous != expected) {

            state.reachable = false;
            return;
        }

        state.variables.put(variable, expected);
    }

    private void mergeStates(
            FlowState target,
            FlowState thenState,
            FlowState elseState
    ) {

        if (!thenState.reachable && !elseState.reachable) {

            target.reachable = false;
            target.variables.clear();
            return;
        }

        if (thenState.reachable && !elseState.reachable) {

            target.reachable = true;
            target.variables.clear();
            target.variables.putAll(thenState.variables);
            return;
        }

        if (!thenState.reachable && elseState.reachable) {

            target.reachable = true;
            target.variables.clear();
            target.variables.putAll(elseState.variables);
            return;
        }

        target.reachable = true;

        Set<String> names = new HashSet<>();
        names.addAll(thenState.variables.keySet());
        names.addAll(elseState.variables.keySet());

        target.variables.clear();

        for (String name : names) {

            NullState left = thenState.variables.getOrDefault(
                    name,
                    NullState.UNKNOWN
            );

            NullState right = elseState.variables.getOrDefault(
                    name,
                    NullState.UNKNOWN
            );

            target.variables.put(
                    name,
                    left == right ? left : NullState.UNKNOWN
            );
        }
    }

    private NullState evaluate(
            Expression expression,
            FlowState state
    ) {

        if (expression.isNullLiteralExpr()) {
            return NullState.NULL;
        }

        if (expression.isStringLiteralExpr()
                || expression.isObjectCreationExpr()
                || expression.isArrayCreationExpr()
                || expression.isArrayInitializerExpr()
                || expression.isTextBlockLiteralExpr()) {

            return NullState.NON_NULL;
        }

        if (expression.isNameExpr()) {

            return state.variables.getOrDefault(
                    expression.asNameExpr().getNameAsString(),
                    NullState.UNKNOWN
            );
        }

        if (expression.isEnclosedExpr()) {

            return evaluate(
                    expression.asEnclosedExpr().getInner(),
                    state
            );
        }

        if (expression.isConditionalExpr()) {

            ConditionalExpr conditional =
                    expression.asConditionalExpr();

            NullState thenValue =
                    evaluate(conditional.getThenExpr(), state);

            NullState elseValue =
                    evaluate(conditional.getElseExpr(), state);

            return thenValue == elseValue
                    ? thenValue
                    : NullState.UNKNOWN;
        }

        return NullState.UNKNOWN;
    }

    private void checkDereferences(
            Node node,
            FlowState state,
            String methodName,
            List<AnalysisIssue> issues
    ) {

        for (MethodCallExpr call :
                node.findAll(MethodCallExpr.class)) {

            if (call.getScope().isEmpty()) {
                continue;
            }

            Expression scope = call.getScope().get();

            if (!scope.isNameExpr()) {
                continue;
            }

            String name = scope.asNameExpr().getNameAsString();

            if (state.variables.getOrDefault(
                    name,
                    NullState.UNKNOWN
            ) == NullState.NULL) {

                addIssue(
                        methodName,
                        call,
                        "Вызов метода " + call.getNameAsString()
                                + "() у переменной " + name
                                + ", которая равна null.",
                        issues
                );
            }
        }

        for (FieldAccessExpr field :
                node.findAll(FieldAccessExpr.class)) {

            if (!field.getScope().isNameExpr()) {
                continue;
            }

            String name = field.getScope()
                    .asNameExpr()
                    .getNameAsString();

            if (state.variables.getOrDefault(
                    name,
                    NullState.UNKNOWN
            ) == NullState.NULL) {

                addIssue(
                        methodName,
                        field,
                        "Обращение к полю "
                                + field.getNameAsString()
                                + " у переменной "
                                + name
                                + ", которая равна null.",
                        issues
                );
            }
        }
    }

    private void addIssue(
            String methodName,
            Node node,
            String message,
            List<AnalysisIssue> issues
    ) {

        issues.add(new AnalysisIssue(
                "NULL_POINTER_DEREFERENCE",
                "HIGH",
                methodName,
                getLine(node),
                "UNKNOWN",
                "Возможен NullPointerException: " + message,
                "Проверьте объект перед использованием. "
                        + "Убедитесь, что переменная "
                        + "инициализирована ненулевым значением."
        ));
    }

    private int getLine(Node node) {

        return node.getBegin()
                .map(position -> position.line)
                .orElse(1);
    }
}