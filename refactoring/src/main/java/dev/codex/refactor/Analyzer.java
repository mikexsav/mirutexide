package dev.codex.refactor;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

final class Analyzer {
  record Method(String name, String declaration, String body, int start, int end, int line, boolean extractable, List<String> calls, Set<String> words) {}
  record Group(String name, List<Method> methods) {}
  private static final Pattern CLASS = Pattern.compile("\\b(class|record|enum)\\s+(\\w+)");
  private static final Pattern HEADER = Pattern.compile("(?m)(?:^|[;{}])\\s*((?:(?:public|protected|private|static|final|synchronized|strictfp|native|abstract)\\s+)*[\\w.$<>?,\\[\\]\\s]+?\\s+(\\w+)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*)\\{");
  static List<Group> groups(Path p) throws IOException { return group(read(p)); }
  static String read(Path p) throws IOException { return Files.readString(p); }
  static String className(String s) { Matcher m=CLASS.matcher(s); if(!m.find()) throw new IllegalArgumentException("No top-level class/record/enum found"); return m.group(2); }
  static List<Method> methods(String source) {
    String s=maskCommentsAndStrings(source); Matcher m=HEADER.matcher(s); List<Method> out=new ArrayList<>();
    while(m.find()) {
      String decl=m.group(1).trim(), name=m.group(2); int brace=m.end()-1, depth=1, i=brace+1;
      if (classBodyDepth(s, m.start(1)) != 1) continue;
      while(i<s.length() && depth>0) { char c=s.charAt(i++); if(c=='{') depth++; else if(c=='}') depth--; }
      if(depth!=0) continue;
      String body=source.substring(brace+1,i-1); String originalDecl=source.substring(m.start(1),brace).trim();
      boolean isStatic=Pattern.compile("\\bstatic\\b").matcher(decl).find();
      boolean safe=isStatic && !decl.contains("<") && !Pattern.compile("\\b(this|super)\\b").matcher(maskCommentsAndStrings(body)).find() && !hasFieldReference(body, name);
      List<String> calls=new ArrayList<>(); Matcher cm=Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\(").matcher(maskCommentsAndStrings(body)); while(cm.find()) calls.add(cm.group(1));
      Set<String> words=new HashSet<>(); Matcher wm=Pattern.compile("\\b[A-Za-z_$][\\w$]*\\b").matcher(maskCommentsAndStrings(body)); while(wm.find()) words.add(wm.group());
      out.add(new Method(name,originalDecl,body,m.start(1),i,lineAt(source,m.start(1)),safe,calls,words));
      m.region(i,s.length());
    }
    return out;
  }
  private static int classBodyDepth(String s,int end) {
    int depth=0; for(int i=0;i<end;i++){ if(s.charAt(i)=='{') depth++; else if(s.charAt(i)=='}') depth--; } return depth;
  }
  private static boolean hasFieldReference(String body,String method) {
    // Conservative check is refined by the extractor using parameter/local names.
    return Pattern.compile("\\b(?:[A-Z][A-Z0-9_]{2,}|this)\\b").matcher(body).find();
  }
  static List<Group> group(String source) {
    List<Method> ms=methods(source); List<Group> gs=new ArrayList<>(); Set<Method> seen=new HashSet<>(); int n=1;
    for(Method seed:ms) if(!seen.contains(seed)) {
      Set<Method> cluster=new LinkedHashSet<>(); cluster.add(seed); boolean changed;
      do { changed=false; for(Method a:ms) if(!cluster.contains(a)) for(Method b:cluster) {
        // Call-graph edges are reliable signals; identifier overlap alone creates
        // false clusters because nearly every method shares Java vocabulary.
        boolean linked=a.calls.contains(b.name)||b.calls.contains(a.name);
        if(linked) { cluster.add(a); changed=true; break; }
      }} while(changed);
      seen.addAll(cluster); gs.add(new Group("Group"+n++,List.copyOf(cluster)));
    }
    return gs;
  }
  private static int lineAt(String s,int i) { int n=1; for(int k=0;k<i;k++) if(s.charAt(k)=='\n') n++; return n; }
  static String maskCommentsAndStrings(String s) {
    StringBuilder b=new StringBuilder(s); int i=0; while(i<b.length()) {
      if(i+1<b.length()&&b.charAt(i)=='/'&&b.charAt(i+1)=='/') { int j=i; while(j<b.length()&&b.charAt(j)!='\n') { b.setCharAt(j++,' '); } i=j; }
      else if(i+1<b.length()&&b.charAt(i)=='/'&&b.charAt(i+1)=='*') { int j=i; b.setCharAt(j++,' '); b.setCharAt(j++,' '); while(j+1<b.length()&&!(b.charAt(j)=='*'&&b.charAt(j+1)=='/')) b.setCharAt(j++,' '); if(j+1<b.length()){b.setCharAt(j++,' ');b.setCharAt(j++,' ');} i=j; }
      else if(b.charAt(i)=='"'||b.charAt(i)=='\'') { char q=b.charAt(i); b.setCharAt(i++,' '); while(i<b.length()){char c=b.charAt(i); b.setCharAt(i++,' '); if(c=='\\'&&i<b.length()) b.setCharAt(i++,' '); else if(c==q) break;} }
      else i++;
    } return b.toString();
  }
}
