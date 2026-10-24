package demo;

public class LegacyCalculator {
    public static int add(int a, int b) { return a + b; }
    public static int subtract(int a, int b) { return a - b; }
    public static int sumThenSubtract(int a, int b, int c) { return subtract(add(a, b), c); }

    public static void main(String[] args) {
        System.out.println(sumThenSubtract(9, 4, 2));
    }
}
