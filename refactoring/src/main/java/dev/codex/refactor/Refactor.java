package dev.codex.refactor;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;

final class Refactor {
  record Plan(Path source, Path component, Path backup, String before, String after, String componentText, String diff) {}
  static Plan plan(Path path, String groupName) throws IOException {
    Path source=path.toAbsolutePath().normalize(); String before=Files.readString(source); String className=Analyzer.className(before);
    List<Analyzer.Method> all=Analyzer.methods(before); List<Analyzer.Method> selected=null;
    for(var g:Analyzer.group(before)) if(g.name().equals(groupName)) selected=g.methods();
    if(selected==null) throw new IllegalArgumentException("Unknown group. Run analyze first.");
    if(selected.size()<2) throw new IllegalArgumentException("Group must contain at least two methods.");
    Set<String> groupNames=new HashSet<>(); selected.forEach(m->groupNames.add(m.name()));
    for(var m:selected) {
      if(!m.extractable()) throw new IllegalArgumentException("Method is not safe to extract: "+m.name()+" (requires static, non-generic method without instance dependencies)");
      for(String call:m.calls()) if(all.stream().anyMatch(x->x.name().equals(call))&&!groupNames.contains(call))
        throw new IllegalArgumentException("Selected method "+m.name()+" calls method outside group: "+call);
    }
    String componentName=className+groupName.substring("Group".length())+"Component";
    StringBuilder comp=new StringBuilder(); Matcher pkg=Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;").matcher(before); if(pkg.find()) comp.append(pkg.group().trim()).append("\n\n");
    Matcher imports=Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[\\w.*]+\\s*;").matcher(before);
    while(imports.find()) comp.append(imports.group().trim()).append('\n');
    if(comp.length()>0 && comp.charAt(comp.length()-1)!='\n') comp.append('\n');
    comp.append("/** Extracted cohesive methods from ").append(className).append(". */\nfinal class ").append(componentName).append(" {\n  private ").append(componentName).append("() {}\n");
    String after=before;
    List<Analyzer.Method> reverse=new ArrayList<>(selected); reverse.sort(Comparator.comparingInt(Analyzer.Method::start).reversed());
    for(var m:reverse) {
      String moved=publicStatic(m.declaration())+" {"+m.body()+"}\n";
      comp.append("\n").append(indent(moved,"  "));
      String args=parameterNames(m.declaration()); String invocation=componentName+"."+m.name()+"("+args+")";
      boolean isVoid=Pattern.compile("\\bvoid\\s+"+Pattern.quote(m.name())+"\\s*\\(").matcher(m.declaration()).find();
      String wrapper="{\n        "+(isVoid?"":"return ")+invocation+";\n    }";
      after=after.substring(0,m.start())+m.declaration()+" "+wrapper+after.substring(m.end());
    }
    comp.append("}\n");
    Path component=source.resolveSibling(componentName+".java");
    return new Plan(source,component,source.getParent().resolve(".refactor-backups"),before,after,comp.toString(),unified(before,after,source.getFileName().toString()));
  }
  private static String publicStatic(String d) {
    String x=d.replaceAll("\\bprivate\\b", "public").replaceAll("\\bprotected\\b", "public");
    if(!Pattern.compile("\\bpublic\\b").matcher(x).find()) x="public "+x;
    return x;
  }
  private static String parameterNames(String decl) {
    int a=decl.indexOf('('), b=decl.lastIndexOf(')'); if(a<0||b<a) return "";
    String params=decl.substring(a+1,b).trim(); if(params.isEmpty()) return "";
    List<String> names=new ArrayList<>(); for(String p:params.split(",")) {
      String[] tokens=p.trim().split("\\s+"); String last=tokens[tokens.length-1].replace("...", "");
      names.add(last.replaceAll("\\[\\]$", ""));
    } return String.join(", ",names);
  }
  private static String indent(String s,String prefix) { return prefix+s.replace("\n","\n"+prefix); }
  static void apply(Plan p) throws IOException {
    Files.createDirectories(p.backup()); String stamp=Long.toString(Instant.now().toEpochMilli());
    Path backup=p.backup().resolve(p.source().getFileName()+"."+stamp+".bak"); Files.copy(p.source(),backup);
    boolean componentExisted=Files.exists(p.component()); Path compBackup=componentExisted?p.backup().resolve(p.component().getFileName()+"."+stamp+".bak"):null;
    if(componentExisted) Files.copy(p.component(),compBackup);
    try {
      Files.writeString(p.component(),p.componentText(),StandardCharsets.UTF_8);
      Files.writeString(p.source(),p.after(),StandardCharsets.UTF_8);
      compile(p.source().getParent());
    } catch(Exception e) {
      Files.writeString(p.source(),p.before(),StandardCharsets.UTF_8);
      if(componentExisted) Files.copy(compBackup,p.component(),StandardCopyOption.REPLACE_EXISTING); else Files.deleteIfExists(p.component());
      if(e instanceof IOException io) throw io;
      throw new IOException("Compilation failed; source restored. "+e.getMessage(),e);
    }
  }
  private static void compile(Path dir) throws IOException {
    JavaCompiler c=ToolProvider.getSystemJavaCompiler(); if(c==null) throw new IOException("JDK compiler unavailable (run with a JDK)");
    List<Path> files; try(var walk=Files.walk(dir)){files=walk.filter(x->x.toString().endsWith(".java")).toList();}
    DiagnosticCollector<JavaFileObject> ds=new DiagnosticCollector<>();
    try(StandardJavaFileManager fm=c.getStandardFileManager(ds,null,StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units=fm.getJavaFileObjectsFromPaths(files);
      boolean ok=c.getTask(null,fm,ds,List.of("-proc:none","-d",Files.createTempDirectory("java-refactor-compile").toString()),null,units).call();
      if(!ok) { StringBuilder msg=new StringBuilder("Compilation rejected the refactor:"); for(var d:ds.getDiagnostics()) msg.append("\n").append(d.getSource()==null?"":Path.of(d.getSource().toUri()).getFileName()+":"+d.getLineNumber()+" ").append(d.getMessage(Locale.ROOT)); throw new IOException(msg.toString()); }
    }
  }
  static Path undo(Path source) throws IOException {
    Path p=source.toAbsolutePath().normalize(), dir=p.getParent().resolve(".refactor-backups");
    if(!Files.isDirectory(dir)) throw new IOException("No backup directory found");
    try(var files=Files.list(dir)) {
      Path latest=files.filter(x->x.getFileName().toString().startsWith(p.getFileName()+".")&&x.toString().endsWith(".bak")).max(Comparator.comparing(Path::toString)).orElseThrow(()->new IOException("No backup found"));
      Files.copy(latest,p,StandardCopyOption.REPLACE_EXISTING); return p;
    }
  }
  private static String unified(String a,String b,String name) {
    // Compact line-oriented preview; deliberately marks full old/new files to avoid a false claim of context-aware hunks.
    StringBuilder s=new StringBuilder("--- a/").append(name).append("\n+++ b/").append(name).append("\n");
    String[] x=a.split("\n",-1), y=b.split("\n",-1); s.append("@@ before ( ").append(x.length-1).append(" lines ) / after ( ").append(y.length-1).append(" lines ) @@\n");
    for(String l:x) s.append("- ").append(l).append('\n'); for(String l:y) s.append("+ ").append(l).append('\n'); return s.toString();
  }
}
