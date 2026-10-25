import java.util.Scanner;

public class FlowchartDemo {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int limit = input.hasNextInt() ? input.nextInt() : 3;
        int i = 0;
        while (i < limit) {
            if (i % 2 == 0) {
                System.out.println("Чётное: " + i);
            } else {
                System.out.println("Нечётное: " + i);
            }
            i++;
        }
        printDone();
    }

    private static void printDone() {
        System.out.println("Готово");
    }
}
