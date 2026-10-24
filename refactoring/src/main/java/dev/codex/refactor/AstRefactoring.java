package dev.codex.refactor;

import dev.mirutex.core.BlueprintEngine;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.util.*;
import java.util.stream.Collectors;

/** Web adapter: conservative AST extraction. The legacy CLI is retained separately. */
public final class AstRefactoring {
    public record Candidate(String name,int line,boolean eligible,String reason) {}
    public record Preview(String original,String source,Map<String,String> files,List<Candidate> candidates,
                          List<String> extracted,String note) {}
    public Preview preview(String source) {
        CompilationUnit unit=BlueprintEngine.ast(source);
        var top=unit.getTypes().stream().filter(t->t instanceof ClassOrInterfaceDeclaration && !((ClassOrInterfaceDeclaration)t).isInterface()).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Для выделения нужен обычный верхнеуровневый класс"));
        ClassOrInterfaceDeclaration owner=(ClassOrInterfaceDeclaration)top;
        if(!owner.getTypeParameters().isEmpty()) throw new IllegalArgumentException("Выделение из generic-класса требует ручной проверки");
        String component=owner.getNameAsString()+"Component";
        if(unit.getTypes().stream().anyMatch(t->t.getNameAsString().equals(component)))throw new IllegalArgumentException("Имя компонента уже занято");
        Set<String> fields=owner.getFields().stream().flatMap(f->f.getVariables().stream()).map(VariableDeclarator::getNameAsString).collect(Collectors.toSet());
        Set<String> ownMethods=owner.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(Collectors.toSet());
        Set<String> nestedTypes=unit.findAll(TypeDeclaration.class).stream().map(TypeDeclaration::getNameAsString).collect(Collectors.toSet());
        List<Candidate> candidates=new ArrayList<>(); List<MethodDeclaration> eligible=new ArrayList<>();
        for(MethodDeclaration m:owner.getMethods()) {
            String reason="";
            if(!m.isStatic() || !m.isPrivate() || m.isSynchronized() || m.getBody().isEmpty() || !m.getTypeParameters().isEmpty() || !m.getAnnotations().isEmpty())reason="Требуется private static метод с телом, без synchronized, аннотаций и type parameters";
            Set<String> locals=m.findAll(VariableDeclarator.class).stream().map(VariableDeclarator::getNameAsString).collect(Collectors.toSet());
            m.getParameters().forEach(p->locals.add(p.getNameAsString()));
            m.findAll(com.github.javaparser.ast.stmt.CatchClause.class).forEach(c->locals.add(c.getParameter().getNameAsString()));
            if(m.findAll(NameExpr.class).stream().anyMatch(n->fields.contains(n.getNameAsString()) && !locals.contains(n.getNameAsString())))reason="Зависимость от полей исходного класса";
            if(!m.findAll(ThisExpr.class).isEmpty() || !m.findAll(SuperExpr.class).isEmpty())reason="Зависимость от this/super";
            if(m.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class).stream().anyMatch(t->nestedTypes.contains(t.getNameAsString())))reason="Зависимость от пользовательского типа исходного файла";
            if(m.findAll(MethodCallExpr.class).stream().anyMatch(c->ownMethods.contains(c.getNameAsString())))reason="Вызов метода исходного класса: требуется разрешение зависимостей";
            if(m.findAll(NameExpr.class).stream().anyMatch(n->nestedTypes.contains(n.getNameAsString()) && !locals.contains(n.getNameAsString())))reason="Квалифицированная ссылка на исходный класс";
            if(!m.findAll(LocalClassDeclarationStmt.class).isEmpty() || m.findAll(ObjectCreationExpr.class).stream().anyMatch(o->o.getAnonymousClassBody().isPresent()))reason="Локальные/анонимные классы требуют ручной проверки";
            candidates.add(new Candidate(m.getNameAsString(),m.getBegin().orElseThrow().line,reason.isEmpty(),reason.isEmpty()?"Самостоятельный private static метод":reason));
            if(reason.isEmpty())eligible.add(m);
        }
        if(eligible.isEmpty())return new Preview(source,source,Map.of(new BlueprintEngine().parse(source).filename(),source),candidates,List.of(),"Безопасных кандидатов нет; код не изменён");
        CompilationUnit extracted=new CompilationUnit();unit.getPackageDeclaration().ifPresent(p->extracted.setPackageDeclaration(p.clone()));
        unit.getImports().forEach(i->extracted.addImport(i.clone()));
        ClassOrInterfaceDeclaration cls=extracted.addClass(component);cls.setPublic(false).setFinal(true);
        cls.addConstructor(Modifier.Keyword.PRIVATE).setBody(new BlockStmt());
        List<String> names=new ArrayList<>();
        for(MethodDeclaration m:eligible) {
            MethodDeclaration moved=m.clone();moved.setPrivate(false).setPublic(true);cls.addMember(moved);names.add(m.getNameAsString());
            MethodCallExpr call=new MethodCallExpr(new NameExpr(component),m.getNameAsString());
            m.getParameters().forEach(p->call.addArgument(new NameExpr(p.getNameAsString())));
            Statement body=m.getType().isVoidType()?new ExpressionStmt(call):new ReturnStmt(call);
            m.setBody(new BlockStmt().addStatement(body));
        }
        String after=unit.toString();String filename=new BlueprintEngine().parse(source).filename();
        return new Preview(source,after,Map.of(filename,after,component+".java",extracted.toString()),candidates,names,
                "Предпросмотр: результат ещё не принят. Требуются компиляция всех файлов и сравнение поведения на заданных входах");
    }
}
