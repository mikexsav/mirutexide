package com.example.codeanalyzer;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class InfiniteLoopAnalyzer {

    private static final int MAX_SIMULATION_STEPS = 10_000;

    public List<AnalysisIssue> analyze(String code) {
        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            StaticJavaParser.getParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(code);

            unit.findAll(WhileStmt.class)
                    .forEach(loop -> analyzeLoop(loop, loop.getCondition(),
                            loop.getBody(), null, issues));

            unit.findAll(ForStmt.class)
                    .forEach(loop -> {
                        if (loop.getCompare().isPresent()) {
                            analyzeLoop(
                                    loop,
                                    loop.getCompare().get(),
                                    loop.getBody(),
                                    loop.getUpdate(),
                                    issues
                            );
                        } else {
                            analyzeLoop(
                                    loop,
                                    new BooleanLiteralExpr(true),
                                    loop.getBody(),
                                    loop.getUpdate(),
                                    issues
                            );
                        }
                    });

            unit.findAll(DoStmt.class)
                    .forEach(loop -> analyzeLoop(
                            loop,
                            loop.getCondition(),
                            loop.getBody(),
                            null,
                            issues
                    ));

        } catch (Exception ignored) {
            // Синтаксические ошибки отдельно обрабатывает JavaSyntaxValidator.
        }

        return issues;
    }

    private void analyzeLoop(
            Statement loop,
            Expression condition,
            Statement body,
            List<Expression> updates,
            List<AnalysisIssue> issues
    ) {
        Map<String, Long> initialValues = collectInitialValues(loop);

        Optional<Boolean> initiallyTrue =
                evaluateBoolean(condition, initialValues);

        // Условие заведомо ложно при входе в цикл.
        if (initiallyTrue.isPresent() && !initiallyTrue.get()) {
            return;
        }

        // У безусловного цикла не должно быть предупреждения,
        // если обнаружен очевидный выход.
        if (isLiteralTrue(condition)) {
            if (hasGuaranteedExit(body)) {
                return;
            }

            // Для простого бесконечного цикла предупреждаем,
            // если не найден очевидный выход.
            addWarning(loop, issues);
            return;
        }

        Set<String> conditionVariables = new HashSet<>();
        for (NameExpr name : condition.findAll(NameExpr.class)) {
            conditionVariables.add(name.getNameAsString());
        }

        Set<String> modifiedVariables = collectModifiedVariables(body);

        if (updates != null) {
            for (Expression update : updates) {
                modifiedVariables.addAll(collectModifiedVariables(update));
            }
        }

        // Если условие зависит от переменных, которые цикл не меняет,
        // проверяем, не является ли оно постоянно истинным.
        boolean conditionVariablesUnchanged =
                !conditionVariables.isEmpty()
                        && conditionVariables.stream()
                        .noneMatch(modifiedVariables::contains);

        if (conditionVariablesUnchanged) {
            Optional<Boolean> value =
                    evaluateBoolean(condition, initialValues);

            if (value.isPresent() && !value.get()) {
                return;
            }

            if (value.isPresent() && value.get()) {
                addWarning(loop, issues);
            } else {
                addWarning(loop, issues);
            }

            return;
        }

        // Проверяем типичные случаи:
        // while (x < 10) { if (x == 5) break; x++; }
        // for (int i = 0; i < 10; i--) { ... }
        if (hasReachableConditionalBreak(
                condition, body, updates, initialValues, loop)) {
            return;
        }

        if (hasSuspiciousDirection(condition, body, updates)) {
            addWarning(loop, issues);
        }
    }

    private Map<String, Long> collectInitialValues(Statement loop) {
        Map<String, Long> values = new HashMap<>();

        // Ищем объявления и присваивания до цикла в том же блоке.
        Optional<BlockStmt> parentBlock =
                loop.findAncestor(BlockStmt.class);

        if (parentBlock.isPresent()) {
            BlockStmt block = parentBlock.get();
            int loopIndex = -1;

            for (int i = 0; i < block.getStatements().size(); i++) {
                if (block.getStatement(i) == loop) {
                    loopIndex = i;
                    break;
                }
            }

            if (loopIndex >= 0) {
                for (int i = 0; i < loopIndex; i++) {
                    Statement statement = block.getStatement(i);
                    processSimpleStatement(statement, values);
                }
            }
        }

        // Для for (int i = 0; ...) учитываем начальное значение.
        if (loop instanceof ForStmt forStmt) {
            for (Expression initialization : forStmt.getInitialization()) {
                if (initialization.isVariableDeclarationExpr()) {
                    initialization.asVariableDeclarationExpr()
                            .getVariables()
                            .forEach(variable -> {
                                Optional<Long> value = variable.getInitializer()
                                        .flatMap(this::evaluateLongLiteral);

                                if (value.isPresent()) {
                                    values.put(
                                            variable.getNameAsString(),
                                            value.get()
                                    );
                                }
                            });
                } else {
                    processSimpleExpression(initialization, values);
                }
            }
        }

        return values;
    }

    private void processSimpleStatement(
            Statement statement,
            Map<String, Long> values
    ) {
        if (statement.isExpressionStmt()) {
            processSimpleExpression(
                    statement.asExpressionStmt().getExpression(),
                    values
            );
            return;
        }

        if (statement.isBlockStmt()) {
            for (Statement child : statement.asBlockStmt().getStatements()) {
                processSimpleStatement(child, values);
            }
            return;
        }

        // Если перед циклом есть сложное ветвление,
        // консервативно сбрасываем сведения о переменных.
        if (statement.isIfStmt()
                || statement.isWhileStmt()
                || statement.isForStmt()
                || statement.isDoStmt()
                || statement.isTryStmt()) {
            values.clear();
        }
    }

    private void processSimpleExpression(
            Expression expression,
            Map<String, Long> values
    ) {
        if (expression.isVariableDeclarationExpr()) {
            expression.asVariableDeclarationExpr()
                    .getVariables()
                    .forEach(variable -> {
                        Optional<Long> value = variable.getInitializer()
                                .flatMap(initializer ->
                                        evaluateNumeric(initializer, values));

                        if (value.isPresent()) {
                            values.put(variable.getNameAsString(), value.get());
                        } else {
                            values.remove(variable.getNameAsString());
                        }
                    });
            return;
        }

        if (expression.isAssignExpr()) {
            AssignExpr assignment = expression.asAssignExpr();

            if (!assignment.getTarget().isNameExpr()) {
                return;
            }

            String name = assignment.getTarget().asNameExpr().getNameAsString();
            Optional<Long> right = evaluateNumeric(assignment.getValue(), values);

            if (assignment.getOperator() == AssignExpr.Operator.ASSIGN
                    && right.isPresent()) {
                values.put(name, right.get());
            } else {
                values.remove(name);
            }
        }
    }

    private Optional<Long> evaluateLongLiteral(Expression expression) {
        return evaluateNumeric(expression, Map.of());
    }

    private Optional<Long> evaluateNumeric(
            Expression expression,
            Map<String, Long> values
    ) {
        try {
            if (expression.isIntegerLiteralExpr()) {
                String text = expression.asIntegerLiteralExpr()
                        .getValue()
                        .replace("_", "");

                return Optional.of(Long.decode(text));
            }

            if (expression.isLongLiteralExpr()) {
                String text = expression.asLongLiteralExpr()
                        .getValue()
                        .replace("_", "");

                if (text.endsWith("L") || text.endsWith("l")) {
                    text = text.substring(0, text.length() - 1);
                }

                return Optional.of(Long.decode(text));
            }

            if (expression.isNameExpr()) {
                return Optional.ofNullable(
                        values.get(expression.asNameExpr().getNameAsString())
                );
            }

            if (expression.isUnaryExpr()) {
                UnaryExpr unary = expression.asUnaryExpr();
                Optional<Long> value = evaluateNumeric(
                        unary.getExpression(), values);

                if (value.isEmpty()) {
                    return Optional.empty();
                }

                return switch (unary.getOperator()) {
                    case MINUS -> Optional.of(-value.get());
                    case PLUS -> value;
                    default -> Optional.empty();
                };
            }

            if (expression.isBinaryExpr()) {
                BinaryExpr binary = expression.asBinaryExpr();

                Optional<Long> left =
                        evaluateNumeric(binary.getLeft(), values);
                Optional<Long> right =
                        evaluateNumeric(binary.getRight(), values);

                if (left.isEmpty() || right.isEmpty()) {
                    return Optional.empty();
                }

                long a = left.get();
                long b = right.get();

                return switch (binary.getOperator()) {
                    case PLUS -> Optional.of(a + b);
                    case MINUS -> Optional.of(a - b);
                    case MULTIPLY -> Optional.of(a * b);
                    case DIVIDE -> b == 0
                            ? Optional.empty()
                            : Optional.of(a / b);
                    case REMAINDER -> b == 0
                            ? Optional.empty()
                            : Optional.of(a % b);
                    default -> Optional.empty();
                };
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private Optional<Boolean> evaluateBoolean(
            Expression expression,
            Map<String, Long> values
    ) {
        if (expression.isBooleanLiteralExpr()) {
            return Optional.of(
                    expression.asBooleanLiteralExpr().getValue()
            );
        }

        if (!expression.isBinaryExpr()) {
            return Optional.empty();
        }

        BinaryExpr binary = expression.asBinaryExpr();

        if (binary.getOperator() == BinaryExpr.Operator.AND
                || binary.getOperator() == BinaryExpr.Operator.OR) {
            Optional<Boolean> left =
                    evaluateBoolean(binary.getLeft(), values);
            Optional<Boolean> right =
                    evaluateBoolean(binary.getRight(), values);

            if (binary.getOperator() == BinaryExpr.Operator.AND) {
                if (left.isPresent() && !left.get()
                        || right.isPresent() && !right.get()) {
                    return Optional.of(false);
                }

                if (left.isPresent() && right.isPresent()) {
                    return Optional.of(left.get() && right.get());
                }
            } else {
                if (left.isPresent() && left.get()
                        || right.isPresent() && right.get()) {
                    return Optional.of(true);
                }

                if (left.isPresent() && right.isPresent()) {
                    return Optional.of(left.get() || right.get());
                }
            }

            return Optional.empty();
        }

        Optional<Long> left = evaluateNumeric(binary.getLeft(), values);
        Optional<Long> right = evaluateNumeric(binary.getRight(), values);

        if (left.isEmpty() || right.isEmpty()) {
            return Optional.empty();
        }

        long a = left.get();
        long b = right.get();

        return switch (binary.getOperator()) {
            case EQUALS -> Optional.of(a == b);
            case NOT_EQUALS -> Optional.of(a != b);
            case LESS -> Optional.of(a < b);
            case LESS_EQUALS -> Optional.of(a <= b);
            case GREATER -> Optional.of(a > b);
            case GREATER_EQUALS -> Optional.of(a >= b);
            default -> Optional.empty();
        };
    }

    private boolean hasReachableConditionalBreak(
            Expression loopCondition,
            Statement body,
            List<Expression> updates,
            Map<String, Long> initialValues,
            Statement loop
    ) {
        Optional<LoopVariable> variableInfo =
                findLoopVariable(loopCondition, body, updates);

        if (variableInfo.isEmpty()) {
            return false;
        }

        LoopVariable variable = variableInfo.get();

        if (!initialValues.containsKey(variable.name())) {
            return false;
        }

        long current = initialValues.get(variable.name());

        for (int iteration = 0; iteration < MAX_SIMULATION_STEPS; iteration++) {
            Map<String, Long> values = new HashMap<>(initialValues);
            values.put(variable.name(), current);

            Optional<Boolean> conditionValue =
                    evaluateBoolean(loopCondition, values);

            if (conditionValue.isEmpty()) {
                return false;
            }

            if (!conditionValue.get()) {
                // Условие цикла само по себе завершает цикл.
                return true;
            }

            if (hasReachableBreakForValue(body, values)) {
                return true;
            }

            Optional<Long> next =
                    applyStep(current, variable.step());

            if (next.isEmpty() || next.get() == current) {
                return false;
            }

            current = next.get();
        }

        // Не делаем вывод о завершении, если моделирование
        // не нашло выход в установленном пределе.
        return false;
    }

    private boolean hasReachableBreakForValue(
            Statement statement,
            Map<String, Long> values
    ) {
        if (statement.isBreakStmt()) {
            return true;
        }

        if (statement.isBlockStmt()) {
            for (Statement child : statement.asBlockStmt().getStatements()) {
                if (hasReachableBreakForValue(child, values)) {
                    return true;
                }

                // После безусловного return/throw дальнейшие строки
                // в этом блоке не исполняются.
                if (child.isReturnStmt() || child.isThrowStmt()) {
                    return false;
                }
            }
            return false;
        }

        if (statement.isIfStmt()) {
            IfStmt ifStmt = statement.asIfStmt();
            Optional<Boolean> result =
                    evaluateBoolean(ifStmt.getCondition(), values);

            if (result.isPresent()) {
                if (result.get()) {
                    return hasReachableBreakForValue(
                            ifStmt.getThenStmt(), values);
                }

                return ifStmt.getElseStmt()
                        .map(elseStmt ->
                                hasReachableBreakForValue(elseStmt, values))
                        .orElse(false);
            }

            // При неизвестном условии break не считаем гарантированным.
            return false;
        }

        // Не заходим во вложенные циклы: их break относится к ним.
        return false;
    }

    private Optional<LoopVariable> findLoopVariable(
            Expression condition,
            Statement body,
            List<Expression> updates
    ) {
        if (!condition.isBinaryExpr()) {
            return Optional.empty();
        }

        BinaryExpr binary = condition.asBinaryExpr();

        String name = null;
        if (binary.getLeft().isNameExpr()) {
            name = binary.getLeft().asNameExpr().getNameAsString();
        } else if (binary.getRight().isNameExpr()) {
            name = binary.getRight().asNameExpr().getNameAsString();
        }

        if (name == null) {
            return Optional.empty();
        }

        Optional<Integer> step = findStepForVariable(name, body, updates);

        if (step.isEmpty() || step.get() == 0) {
            return Optional.empty();
        }

        return Optional.of(new LoopVariable(name, step.get()));
    }

    private Optional<Integer> findStepForVariable(
            String name,
            Statement body,
            List<Expression> updates
    ) {
        List<Integer> steps = new ArrayList<>();

        for (UnaryExpr unary : body.findAll(UnaryExpr.class)) {
            if (!unary.getExpression().isNameExpr()
                    || !unary.getExpression().asNameExpr()
                    .getNameAsString().equals(name)) {
                continue;
            }

            switch (unary.getOperator()) {
                case POSTFIX_INCREMENT, PREFIX_INCREMENT -> steps.add(1);
                case POSTFIX_DECREMENT, PREFIX_DECREMENT -> steps.add(-1);
                default -> {
                }
            }
        }

        for (AssignExpr assignment : body.findAll(AssignExpr.class)) {
            if (!assignment.getTarget().isNameExpr()
                    || !assignment.getTarget().asNameExpr()
                    .getNameAsString().equals(name)) {
                continue;
            }

            if (assignment.getOperator() == AssignExpr.Operator.PLUS
                    && assignment.getValue().isIntegerLiteralExpr()) {
                steps.add(Integer.parseInt(
                        assignment.getValue().asIntegerLiteralExpr().getValue()));
            } else if (assignment.getOperator() == AssignExpr.Operator.MINUS
                    && assignment.getValue().isIntegerLiteralExpr()) {
                steps.add(-Integer.parseInt(
                        assignment.getValue().asIntegerLiteralExpr().getValue()));
            }
        }

        if (updates != null) {
            for (Expression update : updates) {
                if (update.isUnaryExpr()) {
                    UnaryExpr unary = update.asUnaryExpr();

                    if (unary.getExpression().isNameExpr()
                            && unary.getExpression().asNameExpr()
                            .getNameAsString().equals(name)) {
                        switch (unary.getOperator()) {
                            case POSTFIX_INCREMENT, PREFIX_INCREMENT ->
                                    steps.add(1);
                            case POSTFIX_DECREMENT, PREFIX_DECREMENT ->
                                    steps.add(-1);
                            default -> {
                            }
                        }
                    }
                }
            }
        }

        if (steps.isEmpty()) {
            return Optional.empty();
        }

        int first = steps.get(0);

        // Несколько разных изменений одной переменной могут означать
        // сложный поток управления. В таком случае не моделируем цикл.
        if (steps.stream().anyMatch(step -> step != first)) {
            return Optional.empty();
        }

        return Optional.of(first);
    }

    private Optional<Long> applyStep(long value, int step) {
        try {
            return Optional.of(Math.addExact(value, step));
        } catch (ArithmeticException ignored) {
            return Optional.empty();
        }
    }

    private boolean hasSuspiciousDirection(
            Expression condition,
            Statement body,
            List<Expression> updates
    ) {
        if (!condition.isBinaryExpr()) {
            return false;
        }

        BinaryExpr binary = condition.asBinaryExpr();

        String variable;
        BinaryExpr.Operator operator = binary.getOperator();

        if (binary.getLeft().isNameExpr()
                && !binary.getRight().isNameExpr()) {
            variable = binary.getLeft().asNameExpr().getNameAsString();
        } else if (binary.getRight().isNameExpr()
                && !binary.getLeft().isNameExpr()) {
            variable = binary.getRight().asNameExpr().getNameAsString();

            operator = switch (operator) {
                case LESS -> BinaryExpr.Operator.GREATER;
                case LESS_EQUALS -> BinaryExpr.Operator.GREATER_EQUALS;
                case GREATER -> BinaryExpr.Operator.LESS;
                case GREATER_EQUALS -> BinaryExpr.Operator.LESS_EQUALS;
                default -> operator;
            };
        } else {
            return false;
        }

        int direction = getVariableDirection(variable, body, updates);

        if (direction == 0) {
            return false;
        }

        return switch (operator) {
            case LESS, LESS_EQUALS -> direction < 0;
            case GREATER, GREATER_EQUALS -> direction > 0;
            default -> false;
        };
    }

    private int getVariableDirection(
            String variable,
            Statement body,
            List<Expression> updates
    ) {
        List<Integer> directions = new ArrayList<>();

        for (UnaryExpr unary : body.findAll(UnaryExpr.class)) {
            if (!unary.getExpression().isNameExpr()
                    || !unary.getExpression().asNameExpr()
                    .getNameAsString().equals(variable)) {
                continue;
            }

            switch (unary.getOperator()) {
                case POSTFIX_INCREMENT, PREFIX_INCREMENT -> directions.add(1);
                case POSTFIX_DECREMENT, PREFIX_DECREMENT -> directions.add(-1);
                default -> {
                }
            }
        }

        for (AssignExpr assignment : body.findAll(AssignExpr.class)) {
            if (!assignment.getTarget().isNameExpr()
                    || !assignment.getTarget().asNameExpr()
                    .getNameAsString().equals(variable)) {
                continue;
            }

            switch (assignment.getOperator()) {
                case PLUS -> directions.add(1);
                case MINUS -> directions.add(-1);
                default -> {
                }
            }
        }

        if (updates != null) {
            for (Expression update : updates) {
                if (!update.isUnaryExpr()) {
                    continue;
                }

                UnaryExpr unary = update.asUnaryExpr();

                if (!unary.getExpression().isNameExpr()
                        || !unary.getExpression().asNameExpr()
                        .getNameAsString().equals(variable)) {
                    continue;
                }

                switch (unary.getOperator()) {
                    case POSTFIX_INCREMENT, PREFIX_INCREMENT ->
                            directions.add(1);
                    case POSTFIX_DECREMENT, PREFIX_DECREMENT ->
                            directions.add(-1);
                    default -> {
                    }
                }
            }
        }

        if (directions.isEmpty()) {
            return 0;
        }

        int first = directions.get(0);

        if (directions.stream().anyMatch(direction -> direction != first)) {
            return 0;
        }

        return first;
    }

    private Set<String> collectModifiedVariables(Node node) {
        Set<String> modified = new HashSet<>();

        for (AssignExpr assignment : node.findAll(AssignExpr.class)) {
            if (assignment.getTarget().isNameExpr()) {
                modified.add(
                        assignment.getTarget().asNameExpr().getNameAsString()
                );
            }
        }

        for (UnaryExpr unary : node.findAll(UnaryExpr.class)) {
            if (unary.getExpression().isNameExpr()) {
                switch (unary.getOperator()) {
                    case POSTFIX_INCREMENT, PREFIX_INCREMENT,
                         POSTFIX_DECREMENT, PREFIX_DECREMENT ->
                            modified.add(
                                    unary.getExpression().asNameExpr()
                                            .getNameAsString()
                            );
                    default -> {
                    }
                }
            }
        }

        return modified;
    }

    private boolean hasGuaranteedExit(Statement statement) {
        if (statement.isBreakStmt()) {
            return !statement.asBreakStmt().getLabel().isPresent();
        }

        if (statement.isReturnStmt() || statement.isThrowStmt()) {
            return true;
        }

        if (statement.isBlockStmt()) {
            for (Statement child : statement.asBlockStmt().getStatements()) {
                if (hasGuaranteedExit(child)) {
                    return true;
                }
            }
            return false;
        }

        if (statement.isIfStmt()) {
            IfStmt ifStmt = statement.asIfStmt();

            Optional<Boolean> condition = evaluateBoolean(
                    ifStmt.getCondition(), Map.of());

            if (condition.isPresent()) {
                if (condition.get()) {
                    return hasGuaranteedExit(ifStmt.getThenStmt());
                }

                return ifStmt.getElseStmt()
                        .map(this::hasGuaranteedExit)
                        .orElse(false);
            }

            return ifStmt.getElseStmt().isPresent()
                    && hasGuaranteedExit(ifStmt.getThenStmt())
                    && hasGuaranteedExit(ifStmt.getElseStmt().get());
        }

        return false;
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression.isBooleanLiteralExpr()
                && expression.asBooleanLiteralExpr().getValue();
    }

    private void addWarning(
            Statement loop,
            List<AnalysisIssue> issues
    ) {
        int line = loop.getBegin()
                .map(position -> position.line)
                .orElse(1);

        issues.add(new AnalysisIssue(
                "INFINITE_LOOP",
                "MEDIUM",
                getMethodName(loop),
                line,
                "UNKNOWN",
                "Возможен бесконечный цикл: условие выхода может никогда не стать ложным.",
                "Проверьте изменение переменных в условии цикла и убедитесь, что существует достижимое условие завершения."
        ));
    }

    private String getMethodName(Node node) {
        return node.findAncestor(
                        com.github.javaparser.ast.body.MethodDeclaration.class)
                .map(method -> method.getNameAsString())
                .orElse("<unknown>");
    }

    private record LoopVariable(String name, int step) {
    }
}