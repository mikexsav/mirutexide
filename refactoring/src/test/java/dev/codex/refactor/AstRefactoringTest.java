package dev.codex.refactor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AstRefactoringTest {
 @Test void extractsIndependentMethodsWithWrapper(){var p=new AstRefactoring().preview("public class Main {public static void main(String[] args){System.out.println(twice(3));} private static int twice(int n){return n*2;}}");assertEquals(2,p.files().size());assertTrue(p.source().contains("MainComponent.twice(n)"));assertEquals(1,p.extracted().size());}
 @Test void refusesFieldsAndInternalCalls(){var p=new AstRefactoring().preview("class Main {static int factor=2; private static int a(int n){return n*factor;} private static int b(int n){return a(n);}}");assertTrue(p.extracted().isEmpty());}
 @Test void refusesInstanceMethods(){assertTrue(new AstRefactoring().preview("class Main {private int x(){return 1;}}").extracted().isEmpty());}
}
