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
public class ComplexityAnalyzer {

    private enum Growth {
        CONSTANT,
        LOGARITHMIC,
        LINEAR,
        UNKNOWN
    }

    private record Cost(int polynomialDegree, int logarithmicDegree) {

        static Cost constant() {
            return new Cost(0, 0);
        }

        static Cost logarithmic() {
            return new Cost(0, 1);
        }

        static Cost linear() {
            return new Cost(1, 0);
        }

        Cost multiply(Cost other) {
            return new Cost(
                    polynomialDegree + other.polynomialDegree,
                    logarithmicDegree + other.logarithmicDegree
            );
        }

        String notation() {
            if (polynomialDegree == 0 && logarithmicDegree == 0) {
                return "O(1)";
            }

            StringBuilder result = new StringBuilder("O(");

            if (polynomialDegree > 0) {
                result.append("n");

                if (polynomialDegree > 1) {
                    result.append("^").append(polynomialDegree);
                }
            }

            if (logarithmicDegree > 0) {
                if (polynomialDegree > 0) {
                    result.append(" ");
                }

                result.append("log");

                if (logarithmicDegree > 1) {
                    result.append("^")
                            .append(logarithmicDegree);
                }

                result.append(" n");
            }

            return result.append(")").toString();
        }
    }

    private record Estimate(Cost cost, boolean known) {

        static Estimate known(Cost cost) {
            return new Estimate(cost, true);
        }

        static Estimate unknown() {
            return new Estimate(Cost.constant(), false);
        }

        String notation() {
            return known ? cost.notation() : "UNKNOWN";
        }
    }

    public List<AnalysisIssue> analyze(String sourceCode) {
        List<AnalysisIssue> issues = new ArrayList<>();

        try {
            CompilationUnit unit = dev.mirutex.core.BlueprintEngine.ast(sourceCode);

            for (MethodDeclaration method :
                    unit.findAll(MethodDeclaration.class)) {

                boolean recursion = hasDirectRecursion(method);

                if (recursion) {
                    issues.add(new AnalysisIssue(
                            "RECURSION_DETECTED",
                            "MEDIUM",
                            method.getNameAsString(),
                            getLine(method),
                            "UNKNOWN",
                            "Обнаружен возможный прямой рекурсивный вызов.",
                            "Проверьте условие завершения рекурсии, "
                                    + "количество вызовов и изменение аргументов."
                    ));
                }

                List<Statement> statements = method.getBody()
                        .map(body -> new ArrayList<Statement>(
                                body.getStatements()
                        ))
                        .orElseGet(ArrayList::new);

                Estimate estimate = estimateStatements(
                        statements,
                        method,
                        issues
                );

                if (recursion) {
                    estimate = Estimate.unknown();
                }

                issues.add(new AnalysisIssue(
                        "COMPLEXITY_ESTIMATE",
                        "LOW",
                        method.getNameAsString(),
                        getLine(method),
                        estimate.notation(),
                        "Предварительная оценка сложности метода: "
                                + estimate.notation(),
                        estimate.known()
                                ? "Оценка основана на распознанных циклах. "
                                + "Стоимость вызываемых методов и "
                                + "все зависимости пока не учитываются."
                                : "Не удалось надёжно оценить сложность. "
                                + "Проверьте границы циклов, изменение "
                                + "переменных, условия выхода и рекурсию."
                ));
            }

        } catch (Exception e) {
            issues.add(new AnalysisIssue(
                    "PARSE_ERROR",
                    "HIGH",
                    "unknown",
                    1,
                    "UNKNOWN",
                    "Не удалось разобрать Java-код.",
                    "Проверьте синтаксис исходного кода."
            ));
        }

        return issues;
    }

    private Estimate estimateStatements(
            List<Statement> statements,
            MethodDeclaration method,
            List<AnalysisIssue> issues
    ) {
        Estimate result = Estimate.known(Cost.constant());

        for (Statement statement : statements) {
            result = maximum(
                    result,
                    estimateStatement(statement, method, issues)
            );
        }

        return result;
    }

    private Estimate estimateStatement(
            Statement statement,
            MethodDeclaration method,
            List<AnalysisIssue> issues
    ) {
        if (statement instanceof BlockStmt block) {
            return estimateStatements(
                    new ArrayList<>(block.getStatements()),
                    method,
                    issues
            );
        }

        if (statement instanceof ForStmt loop) {
            Estimate body = estimateStatement(
                    loop.getBody(),
                    method,
                    issues
            );

            return estimateLoop(
                    classifyForLoop(loop),
                    body,
                    loop,
                    method,
                    issues
            );
        }

        if (statement instanceof ForEachStmt loop) {
            Estimate body = estimateStatement(
                    loop.getBody(),
                    method,
                    issues
            );

            Growth growth = hasUnsafeControlFlow(loop.getBody())
                    ? Growth.UNKNOWN
                    : Growth.LINEAR;

            return estimateLoop(
                    growth,
                    body,
                    loop,
                    method,
                    issues
            );
        }

        if (statement instanceof WhileStmt loop) {
            estimateStatement(loop.getBody(), method, issues);

            return Estimate.unknown();
        }

        if (statement instanceof DoStmt loop) {
            estimateStatement(loop.getBody(), method, issues);

            return Estimate.unknown();
        }

        if (statement instanceof IfStmt ifStmt) {
            Estimate thenEstimate = estimateStatement(
                    ifStmt.getThenStmt(),
                    method,
                    issues
            );

            Estimate elseEstimate = ifStmt.getElseStmt()
                    .map(other -> estimateStatement(
                            other,
                            method,
                            issues
                    ))
                    .orElse(Estimate.known(Cost.constant()));

            return maximum(thenEstimate, elseEstimate);
        }

        if (statement instanceof SwitchStmt switchStmt) {
            Estimate result = Estimate.known(Cost.constant());

            for (SwitchEntry entry : switchStmt.getEntries()) {
                result = maximum(
                        result,
                        estimateStatements(
                                new ArrayList<>(entry.getStatements()),
                                method,
                                issues
                        )
                );
            }

            return result;
        }

        if (statement instanceof TryStmt tryStmt) {
            Estimate result = estimateStatement(
                    tryStmt.getTryBlock(),
                    method,
                    issues
            );

            for (CatchClause catchClause :
                    tryStmt.getCatchClauses()) {

                result = maximum(
                        result,
                        estimateStatement(
                                catchClause.getBody(),
                                method,
                                issues
                        )
                );
            }

            if (tryStmt.getFinallyBlock().isPresent()) {
                result = maximum(
                        result,
                        estimateStatement(
                                tryStmt.getFinallyBlock().get(),
                                method,
                                issues
                        )
                );
            }

            return result;
        }

        return Estimate.known(Cost.constant());
    }

    private Estimate estimateLoop(
            Growth growth,
            Estimate body,
            Statement loop,
            MethodDeclaration method,
            List<AnalysisIssue> issues
    ) {
        if (growth == Growth.UNKNOWN || !body.known()) {
            return Estimate.unknown();
        }

        Cost loopCost = switch (growth) {
            case CONSTANT -> Cost.constant();
            case LOGARITHMIC -> Cost.logarithmic();
            case LINEAR -> Cost.linear();
            case UNKNOWN -> Cost.constant();
        };

        Cost combined = loopCost.multiply(body.cost());

        if (containsNestedLoop(loop)) {
            issues.add(new AnalysisIssue(
                    "NESTED_LOOP",
                    "MEDIUM",
                    method.getNameAsString(),
                    getLine(loop),
                    combined.notation(),
                    "Обнаружены вложенные циклы. "
                            + "Предварительная сложность участка: "
                            + combined.notation(),
                    "Проверьте зависимости между циклами. "
                            + "Вложенность не всегда означает O(n²)."
            ));
        }

        return Estimate.known(combined);
    }

    private Growth classifyForLoop(ForStmt loop) {

        if (loop.getCompare().isEmpty()) {
            return Growth.UNKNOWN;
        }

        if (!(loop.getCompare().get()
                instanceof BinaryExpr condition)) {
            return Growth.UNKNOWN;
        }

        if (!isComparison(condition.getOperator())) {
            return Growth.UNKNOWN;
        }

        if (!(condition.getLeft() instanceof NameExpr counter)) {
            return Growth.UNKNOWN;
        }

        String variable = counter.getNameAsString();

        if (loop.getInitialization().size() != 1
                || loop.getUpdate().size() != 1) {
            return Growth.UNKNOWN;
        }

        if (!(loop.getInitialization().get(0)
                instanceof VariableDeclarationExpr declaration)) {
            return Growth.UNKNOWN;
        }

        if (declaration.getVariables().size() != 1) {
            return Growth.UNKNOWN;
        }

        var declared = declaration.getVariable(0);

        if (!declared.getNameAsString().equals(variable)
                || declared.getInitializer().isEmpty()) {
            return Growth.UNKNOWN;
        }

        Expression start = declared.getInitializer().get();
        Expression bound = condition.getRight();
        Expression update = loop.getUpdate().get(0);

        if (hasUnsafeControlFlow(loop.getBody())) {
            return Growth.UNKNOWN;
        }

        if (modifiesVariable(loop.getBody(), variable)) {
            return Growth.UNKNOWN;
        }

        // Проверяем, не меняются ли переменные,
        // от которых зависит граница цикла.
        Set<String> boundVariables = new HashSet<>();

        for (NameExpr name : bound.findAll(NameExpr.class)) {
            boundVariables.add(name.getNameAsString());
        }

        for (String boundVariable : boundVariables) {
            if (modifiesVariable(loop.getBody(), boundVariable)) {
                return Growth.UNKNOWN;
            }
        }

        // Не пытаемся оценивать циклы, если условие
        // содержит вызовы методов.
        if (!bound.findAll(MethodCallExpr.class).isEmpty()) {
            return Growth.UNKNOWN;
        }

        if (isIntegerLiteral(start)
                && isIntegerLiteral(bound)
                && isRecognizedUpdate(update, variable)) {
            return Growth.CONSTANT;
        }

        if (isMultiplicativeUpdate(update, variable)
                && isPositiveIntegerLiteral(start)
                && isGrowingBound(condition)) {
            return Growth.LOGARITHMIC;
        }

        if (isUnitIncrement(update, variable)
                && isIntegerLiteral(start)
                && isGrowingBound(condition)) {
            return Growth.LINEAR;
        }

        return Growth.UNKNOWN;
    }

    private boolean hasUnsafeControlFlow(Statement body) {
        return !body.findAll(BreakStmt.class).isEmpty()
                || !body.findAll(ContinueStmt.class).isEmpty()
                || !body.findAll(ReturnStmt.class).isEmpty()
                || !body.findAll(ThrowStmt.class).isEmpty();
    }

    private boolean modifiesVariable(
            Statement body,
            String variable
    ) {
        for (AssignExpr assignment :
                body.findAll(AssignExpr.class)) {

            if (assignment.getTarget().isNameExpr()
                    && assignment.getTarget()
                    .asNameExpr()
                    .getNameAsString()
                    .equals(variable)) {
                return true;
            }
        }

        for (UnaryExpr unary : body.findAll(UnaryExpr.class)) {

            if (!unary.getExpression().isNameExpr()) {
                continue;
            }

            if (!unary.getExpression()
                    .asNameExpr()
                    .getNameAsString()
                    .equals(variable)) {
                continue;
            }

            if (unary.getOperator()
                    == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || unary.getOperator()
                    == UnaryExpr.Operator.PREFIX_INCREMENT
                    || unary.getOperator()
                    == UnaryExpr.Operator.POSTFIX_DECREMENT
                    || unary.getOperator()
                    == UnaryExpr.Operator.PREFIX_DECREMENT) {
                return true;
            }
        }

        return false;
    }

    private boolean isComparison(BinaryExpr.Operator operator) {
        return operator == BinaryExpr.Operator.LESS
                || operator == BinaryExpr.Operator.LESS_EQUALS
                || operator == BinaryExpr.Operator.GREATER
                || operator == BinaryExpr.Operator.GREATER_EQUALS;
    }

    private boolean isGrowingBound(BinaryExpr condition) {
        return condition.getOperator() == BinaryExpr.Operator.LESS
                || condition.getOperator()
                == BinaryExpr.Operator.LESS_EQUALS;
    }

    private boolean isRecognizedUpdate(
            Expression update,
            String variable
    ) {
        return isUnitIncrement(update, variable)
                || isMultiplicativeUpdate(update, variable);
    }

    private boolean isUnitIncrement(
            Expression update,
            String variable
    ) {
        if (update instanceof UnaryExpr unary) {
            return unary.getExpression().isNameExpr()
                    && unary.getExpression()
                    .asNameExpr()
                    .getNameAsString()
                    .equals(variable)
                    && (unary.getOperator()
                    == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || unary.getOperator()
                    == UnaryExpr.Operator.PREFIX_INCREMENT);
        }

        if (update instanceof AssignExpr assignment) {
            return assignment.getTarget().isNameExpr()
                    && assignment.getTarget()
                    .asNameExpr()
                    .getNameAsString()
                    .equals(variable)
                    && assignment.getOperator()
                    == AssignExpr.Operator.PLUS
                    && isPositiveIntegerLiteral(
                    assignment.getValue()
            );
        }

        return false;
    }

    private boolean isMultiplicativeUpdate(
            Expression update,
            String variable
    ) {
        if (!(update instanceof AssignExpr assignment)) {
            return false;
        }

        Long multiplier = integerValue(assignment.getValue());

        return assignment.getTarget().isNameExpr()
                && assignment.getTarget()
                .asNameExpr()
                .getNameAsString()
                .equals(variable)
                && assignment.getOperator()
                == AssignExpr.Operator.MULTIPLY
                && multiplier != null
                && multiplier > 1;
    }

    private boolean isIntegerLiteral(Expression expression) {
        return integerValue(expression) != null;
    }

    private boolean isPositiveIntegerLiteral(Expression expression) {
        Long value = integerValue(expression);
        return value != null && value > 0;
    }

    private Long integerValue(Expression expression) {
        try {
            if (expression instanceof IntegerLiteralExpr literal) {
                return Long.decode(
                        literal.getValue().replace("_", "")
                );
            }

            if (expression instanceof UnaryExpr unary
                    && unary.getOperator()
                    == UnaryExpr.Operator.MINUS
                    && unary.getExpression()
                    instanceof IntegerLiteralExpr literal) {

                return -Long.decode(
                        literal.getValue().replace("_", "")
                );
            }
        } catch (NumberFormatException ignored) {
            return null;
        }

        return null;
    }

    private Estimate maximum(Estimate first, Estimate second) {
        if (!first.known() || !second.known()) {
            return Estimate.unknown();
        }

        Cost a = first.cost();
        Cost b = second.cost();

        if (a.polynomialDegree() != b.polynomialDegree()) {
            return a.polynomialDegree() > b.polynomialDegree()
                    ? first
                    : second;
        }

        return a.logarithmicDegree() >= b.logarithmicDegree()
                ? first
                : second;
    }

    private boolean containsNestedLoop(Statement loop) {
        long count = loop.findAll(ForStmt.class).size()
                + loop.findAll(ForEachStmt.class).size()
                + loop.findAll(WhileStmt.class).size()
                + loop.findAll(DoStmt.class).size();

        return count > 1;
    }

    private boolean hasDirectRecursion(MethodDeclaration method) {
        String methodName = method.getNameAsString();

        for (MethodCallExpr call :
                method.findAll(MethodCallExpr.class)) {

            if (!call.getNameAsString().equals(methodName)) {
                continue;
            }

            if (call.getArguments().size()
                    != method.getParameters().size()) {
                continue;
            }

            if (call.getScope().isEmpty()
                    || call.getScope().get().isThisExpr()) {
                return true;
            }
        }

        return false;
    }

    private int getLine(Node node) {
        return node.getBegin()
                .map(position -> position.line)
                .orElse(1);
    }
}