package dev.mirutex.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintEngineTest {
 final BlueprintEngine engine=new BlueprintEngine();
 final String sample="class Main { static int f(int n) { int sum=0; for(int i=0;i<n;i++){ if(i==2)continue; if(i==4)break; sum+=i; } return sum; } }";
 @Test void losslessRoundtripAndStableIdentity(){
  var g=engine.parse(sample);assertEquals(sample,engine.generate(g).source());
  assertEquals(g.nodes(),engine.parse(sample).nodes());assertEquals(g.edges(),engine.parse(sample).edges());
  assertTrue(g.nodes().stream().anyMatch(n->n.kind().equals("condition")));
 }
 @Test void controlTransfersHaveCorrectDestinations(){
  var g=engine.parse(sample);var cont=g.nodes().stream().filter(n->n.kind().equals("continue")).findFirst().orElseThrow();
  var edge=g.edges().stream().filter(e->e.source().equals(cont.id())).findFirst().orElseThrow();
  assertTrue(edge.target().endsWith("/update"));
  var brk=g.nodes().stream().filter(n->n.kind().equals("break")).findFirst().orElseThrow();
  var target=g.edges().stream().filter(e->e.source().equals(brk.id())).findFirst().orElseThrow().target();
  assertEquals("return",g.nodes().stream().filter(n->n.id().equals(target)).findFirst().orElseThrow().kind());
 }
 @Test void supportedEditsPreserveSurroundingSource(){
  var g=engine.parse(sample);var n=g.nodes().stream().filter(x->x.code().equals("sum+=i;")).findFirst().orElseThrow();
  var after=engine.operate(g,new BlueprintEngine.Operation("replace",n.id(),"sum += i + 0;"));
  assertEquals(sample.replace("sum+=i;","sum += i + 0;"),after.source());
  assertTrue(after.nodes().stream().anyMatch(x->x.id().equals(n.id())));
  var inserted=engine.operate(g,new BlueprintEngine.Operation("insertBefore",n.id(),"sum += 0;"));assertTrue(inserted.source().contains("sum += 0;"));
  var deleted=engine.operate(g,new BlueprintEngine.Operation("delete",n.id(),""));assertFalse(deleted.source().contains("sum+=i;"));
 }
 @Test void refuseForgedGraph(){var g=engine.parse(sample);
  var forged=new BlueprintEngine.Graph(g.schemaVersion(),g.source(),g.sourceHash(),g.filename(),g.mainClass(),g.nodes(),List.of(),g.methods(),g.declarations(),g.limitations());
  assertThrows(IllegalArgumentException.class,()->engine.generate(forged));
 }
 @Test void refuseUnsafeMutation(){var g=engine.parse(sample);var n=g.nodes().stream().filter(x->x.code().equals("sum+=i;")).findFirst().orElseThrow();
  assertThrows(IllegalArgumentException.class,()->engine.operate(g,new BlueprintEngine.Operation("replace",n.id(),"return;")));
  var terminal=g.nodes().stream().filter(x->x.kind().equals("end")).findFirst().orElseThrow();
  assertThrows(IllegalArgumentException.class,()->engine.operate(g,new BlueprintEngine.Operation("delete",terminal.id(),"")));
 }
 @Test void rejectSyntaxErrors(){assertThrows(IllegalArgumentException.class,()->engine.parse("class {"));}
 @Test void preserveUnicodeTabsAndCrLf(){String s="class Main {\r\n\tvoid f(){ System.out.println(\"Я 😀\"); }\r\n}";var g=engine.parse(s);assertEquals(s,engine.generate(g).source());var n=g.nodes().stream().filter(x->x.kind().equals("call")).findFirst().orElseThrow();assertEquals("System.out.println(\"Я 😀\");",n.code());}
 @Test void tryFinallyIsHonestAtomicRegion(){var g=engine.parse("class X {int f(){try{return 1;}finally{System.out.println(2);}}}");assertTrue(g.nodes().stream().anyMatch(n->n.kind().equals("exception")&&!n.editable()));assertTrue(g.limitations().stream().anyMatch(l->l.contains("атомарный")));}
 @Test void switchesDoWhileAndConstructors(){String s="class X {X(){} int f(int n){ do{n--;}while(n>5); switch(n){case 1: n++; case 2: break; default: n=0;} return n;}}";var g=engine.parse(s);assertEquals(2,g.methods().size());assertTrue(g.edges().stream().anyMatch(e->e.label().equals("default")));assertEquals(s,engine.generate(g).source());}
 @ParameterizedTest @ValueSource(strings={"ScopeShowcase.java","MonolithDemo.java"})
 void officialScopeAndLargeMonolithRoundtripCompile(String name)throws Exception{
  String source=Files.readString(Path.of("../examples/"+name));long start=System.nanoTime();var g=engine.parse(source);
  assertEquals(source,engine.generate(g).source());assertFalse(g.nodes().isEmpty());
  Path dir=Files.createTempDirectory("mirutex-fixture-");try{Path file=dir.resolve(name);Files.writeString(file,g.source());assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","21","-encoding","UTF-8","-proc:none","-d",dir.toString(),file.toString()));}finally{try(var walk=Files.walk(dir)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.printf("%s: %d lines, %d nodes, roundtrip + compile %.0f ms%n",name,source.lines().count(),g.nodes().size(),(System.nanoTime()-start)/1e6);
 }
}
