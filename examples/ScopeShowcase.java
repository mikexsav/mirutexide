import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

interface Describer { String describe(); }
class Item implements Describer {
    private final int value;
    Item(int value) { this.value = value; }
    public String describe() { return "Элемент:" + value; }
}
class CaseException extends Exception {
    CaseException(String message) { super(message); }
}
public class ScopeShowcase {
    static final String TITLE = "Проверка кириллицы";
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        int n = scanner.hasNextInt() ? scanner.nextInt() : 4;
        boolean flag = n >= 0 && n != 13;
        byte b = 2; short s = 3; long l = 4L; float f = 1.5f; double d = 2.5;
        char letter = 'Я'; Integer wrapped = n;
        int[] array = {b, s, (int) l, (int) (f + d), wrapped};
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            if (i == 1) continue;
            sum += array[i];
            if (sum > 100) break;
        }
        int k = 0;
        while (k < Math.min(Math.abs(n), 5)) { k++; }
        do { k--; } while (k > 2);
        switch (n % 3) {
            case 0: sum += 1; break;
            case 1: sum += 2;
            default: sum += 3;
        }
        List<Integer> list = new ArrayList<>(List.of(3, 1, 2));
        List<Integer> linked = new LinkedList<>(list);
        Set<Integer> hashSet = new HashSet<>(Set.of(2, 1));
        Set<Integer> treeSet = new TreeSet<>(hashSet);
        Map<String, Integer> map = new HashMap<>(Map.of("я", 1));
        Map<String, Integer> treeMap = new TreeMap<>(map);
        Collection<Integer> collection = linked;
        Iterable<Integer> iterable = collection;
        for (Integer value : iterable) { sum += value; }
        Iterator<Integer> iterator = list.iterator();
        if (iterator.hasNext()) sum += iterator.next();
        Collections.sort(list, Comparator.naturalOrder());
        Arrays.sort(array);
        Optional<Integer> optional = Optional.of(n);
        Describer item = new Item(optional.orElse(0));
        Object object = item;
        StringBuilder result = new StringBuilder(TITLE);
        result.append(" ").append(letter).append(" ").append(flag);
        System.out.println(result);
        System.out.println(object.toString().startsWith("Item@") ? item.describe() : item.describe());
        System.out.println(treeSet + " " + treeMap + " " + list);
        System.out.printf("sum=%d k=%d helper=%d%n", sum, k, calculate(n));
        try {
            checked(n);
            if (n == 0) throw new IllegalArgumentException("ноль");
        } catch (CaseException | IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
        } finally {
            System.out.println(String.format("Готово %d", n));
        }
        try (Formatter formatter = new Formatter()) {
            System.out.println(formatter.format("%s", "UTF-8"));
        }
    }
    private static int calculate(int n) {
        if (n < 0) return -n;
        return n * 2;
    }
    static void checked(int n) throws CaseException {
        if (n < 0) throw new CaseException("отрицательное");
    }
}
