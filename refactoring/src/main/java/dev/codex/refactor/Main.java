package dev.codex.refactor;

import java.nio.file.*;
import java.util.*;

public final class Main {
  public static void main(String[] args) throws Exception {
    if (args.length == 0 || args[0].equals("help")) { help(); return; }
    switch (args[0]) {
      case "analyze" -> {
        require(args, 2); Path p = Path.of(args[1]);
        for (var g : Analyzer.groups(p)) {
          System.out.println("\n" + g.name() + " (" + g.methods().size() + " methods)");
          g.methods().forEach(m -> System.out.printf("  %-24s line %d%s%n", m.name(), m.line(), m.extractable() ? " [extractable]" : " [review required]"));
        }
      }
      case "extract" -> {
        if (args.length < 3) throw new IllegalArgumentException("extract <file.java> <GroupName> [--preview|--apply]");
        Path source = Path.of(args[1]); boolean apply = Arrays.asList(args).contains("--apply");
        if (!apply && !Arrays.asList(args).contains("--preview")) throw new IllegalArgumentException("Choose --preview or --apply explicitly");
        Refactor.Plan plan = Refactor.plan(source, args[2]);
        System.out.println(plan.diff());
        if (apply) {
          System.out.print("Apply this change? [y/N] ");
          if (!new Scanner(System.in).nextLine().trim().equalsIgnoreCase("y")) { System.out.println("Cancelled."); return; }
          Refactor.apply(plan); System.out.println("Applied. Backup: " + plan.backup());
        }
      }
      case "undo" -> { require(args, 2); System.out.println("Restored " + Refactor.undo(Path.of(args[1]))); }
      case "verify" -> {
        if (args.length < 4 || !args[2].equals("--")) throw new IllegalArgumentException("verify <project-dir> -- <command> [args...]");
        Process p = new ProcessBuilder(Arrays.copyOfRange(args, 3, args.length)).directory(Path.of(args[1]).toFile()).inheritIO().start();
        System.exit(p.waitFor());
      }
      default -> throw new IllegalArgumentException("Unknown command: " + args[0]);
    }
  }
  private static void require(String[] a, int n) { if (a.length < n) throw new IllegalArgumentException("Missing argument"); }
  private static void help() { System.out.println("Java Refactor Assistant\n  analyze <file.java>\n  extract <file.java> <group> --preview|--apply\n  undo <file.java>\n  verify <project-dir> -- <command> [args...]"); }
}
