import java.util.*;

public class CompletionDemo {
    public static void main(String[] args) {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(3);
        // Попробуйте sout, numbers. или System.out.
        System.out.println(total(numbers));
    }

    private static int total(List<Integer> values) {
        int result = 0;
        for (int value : values) {
            result += value;
        }
        return result;
    }
}
