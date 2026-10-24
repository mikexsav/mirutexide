export const demo = `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        int n = input.hasNextInt() ? input.nextInt() : 5;
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            numbers.add(i + 1);
        }
        int pairs = 0;
        for (int a : numbers) {
            for (int b : numbers) {
                if (a < b) {
                    pairs++;
                }
            }
        }
        System.out.println("Пар: " + pairs);
        System.out.println("Сумма: " + total(numbers));
        try {
            System.out.println("Первый: " + numbers.get(0));
        } catch (IndexOutOfBoundsException ex) {
            System.err.println("Список пуст");
        } finally {
            System.out.println("Готово");
        }
    }

    private static int total(List<Integer> values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }
}
`;
