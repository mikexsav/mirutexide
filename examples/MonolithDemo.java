import java.util.*;
import java.nio.charset.StandardCharsets;

/** Deterministic 1000+ line demo: stdin, collections, loops, conditions, exceptions. */
public class MonolithDemo {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        int n = scanner.hasNextInt() ? scanner.nextInt() : 5;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < Math.max(0, Math.min(n, 30)); i++) {
            values.add(i + 1);
        }
        int pairs = 0;
        for (int a : values) {
            for (int b : values) {
                if (a < b) pairs++;
            }
        }
        long total = 0;
        total += metric00(values);
        total += metric01(values);
        total += metric02(values);
        total += metric03(values);
        total += metric04(values);
        total += metric05(values);
        total += metric06(values);
        total += metric07(values);
        total += metric08(values);
        total += metric09(values);
        total += metric10(values);
        total += metric11(values);
        total += metric12(values);
        total += metric13(values);
        total += metric14(values);
        total += metric15(values);
        total += metric16(values);
        total += metric17(values);
        total += metric18(values);
        total += metric19(values);
        total += metric20(values);
        total += metric21(values);
        total += metric22(values);
        total += metric23(values);
        total += metric24(values);
        total += metric25(values);
        total += metric26(values);
        total += metric27(values);
        total += metric28(values);
        total += metric29(values);
        total += metric30(values);
        total += metric31(values);
        total += metric32(values);
        total += metric33(values);
        total += metric34(values);
        total += metric35(values);
        total += metric36(values);
        total += metric37(values);
        total += metric38(values);
        total += metric39(values);
        total += metric40(values);
        total += metric41(values);
        total += metric42(values);
        total += metric43(values);
        total += metric44(values);
        total += metric45(values);
        total += metric46(values);
        total += metric47(values);
        total += metric48(values);
        total += metric49(values);
        total += metric50(values);
        total += metric51(values);
        total += metric52(values);
        total += metric53(values);
        total += metric54(values);
        total += metric55(values);
        total += metric56(values);
        total += metric57(values);
        total += metric58(values);
        total += metric59(values);
        total += metric60(values);
        total += metric61(values);
        total += metric62(values);
        total += metric63(values);
        total += metric64(values);
        total += metric65(values);
        total += metric66(values);
        total += metric67(values);
        total += metric68(values);
        total += metric69(values);
        total += metric70(values);
        total += metric71(values);
        total += metric72(values);
        total += metric73(values);
        total += metric74(values);
        total += metric75(values);
        total += metric76(values);
        total += metric77(values);
        total += metric78(values);
        total += metric79(values);
        total += metric80(values);
        total += metric81(values);
        total += metric82(values);
        total += metric83(values);
        total += metric84(values);
        total += metric85(values);
        total += metric86(values);
        total += metric87(values);
        total += metric88(values);
        total += metric89(values);
        total += metric90(values);
        total += metric91(values);
        total += metric92(values);
        total += metric93(values);
        total += metric94(values);
        total += metric95(values);
        total += metric96(values);
        total += metric97(values);
        total += metric98(values);
        total += metric99(values);
        System.out.println("Пар: " + pairs);
        System.out.println("Метрики: " + total);
        try {
            System.out.println("Первый: " + values.get(0));
        } catch (IndexOutOfBoundsException ex) {
            System.err.println("Нет элементов");
        } finally {
            System.out.println("Завершено");
        }
    }

    /** Computes independent metric 0. */
    private static int metric00(List<Integer> values) {
        int result = 0;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 1. */
    private static int metric01(List<Integer> values) {
        int result = 1;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 2. */
    private static int metric02(List<Integer> values) {
        int result = 2;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 3. */
    private static int metric03(List<Integer> values) {
        int result = 3;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 4. */
    private static int metric04(List<Integer> values) {
        int result = 4;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 5. */
    private static int metric05(List<Integer> values) {
        int result = 5;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 6. */
    private static int metric06(List<Integer> values) {
        int result = 6;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 7. */
    private static int metric07(List<Integer> values) {
        int result = 7;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 8. */
    private static int metric08(List<Integer> values) {
        int result = 8;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 9. */
    private static int metric09(List<Integer> values) {
        int result = 9;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 10. */
    private static int metric10(List<Integer> values) {
        int result = 10;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 11. */
    private static int metric11(List<Integer> values) {
        int result = 11;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 12. */
    private static int metric12(List<Integer> values) {
        int result = 12;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 13. */
    private static int metric13(List<Integer> values) {
        int result = 13;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 14. */
    private static int metric14(List<Integer> values) {
        int result = 14;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 15. */
    private static int metric15(List<Integer> values) {
        int result = 15;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 16. */
    private static int metric16(List<Integer> values) {
        int result = 16;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 17. */
    private static int metric17(List<Integer> values) {
        int result = 17;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 18. */
    private static int metric18(List<Integer> values) {
        int result = 18;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 19. */
    private static int metric19(List<Integer> values) {
        int result = 19;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 20. */
    private static int metric20(List<Integer> values) {
        int result = 20;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 21. */
    private static int metric21(List<Integer> values) {
        int result = 21;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 22. */
    private static int metric22(List<Integer> values) {
        int result = 22;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 23. */
    private static int metric23(List<Integer> values) {
        int result = 23;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 24. */
    private static int metric24(List<Integer> values) {
        int result = 24;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 25. */
    private static int metric25(List<Integer> values) {
        int result = 25;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 26. */
    private static int metric26(List<Integer> values) {
        int result = 26;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 27. */
    private static int metric27(List<Integer> values) {
        int result = 27;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 28. */
    private static int metric28(List<Integer> values) {
        int result = 28;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 29. */
    private static int metric29(List<Integer> values) {
        int result = 29;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 30. */
    private static int metric30(List<Integer> values) {
        int result = 30;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 31. */
    private static int metric31(List<Integer> values) {
        int result = 31;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 32. */
    private static int metric32(List<Integer> values) {
        int result = 32;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 33. */
    private static int metric33(List<Integer> values) {
        int result = 33;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 34. */
    private static int metric34(List<Integer> values) {
        int result = 34;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 35. */
    private static int metric35(List<Integer> values) {
        int result = 35;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 36. */
    private static int metric36(List<Integer> values) {
        int result = 36;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 37. */
    private static int metric37(List<Integer> values) {
        int result = 37;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 38. */
    private static int metric38(List<Integer> values) {
        int result = 38;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 39. */
    private static int metric39(List<Integer> values) {
        int result = 39;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 40. */
    private static int metric40(List<Integer> values) {
        int result = 40;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 41. */
    private static int metric41(List<Integer> values) {
        int result = 41;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 42. */
    private static int metric42(List<Integer> values) {
        int result = 42;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 43. */
    private static int metric43(List<Integer> values) {
        int result = 43;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 44. */
    private static int metric44(List<Integer> values) {
        int result = 44;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 45. */
    private static int metric45(List<Integer> values) {
        int result = 45;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 46. */
    private static int metric46(List<Integer> values) {
        int result = 46;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 47. */
    private static int metric47(List<Integer> values) {
        int result = 47;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 48. */
    private static int metric48(List<Integer> values) {
        int result = 48;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 49. */
    private static int metric49(List<Integer> values) {
        int result = 49;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 50. */
    private static int metric50(List<Integer> values) {
        int result = 50;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 51. */
    private static int metric51(List<Integer> values) {
        int result = 51;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 52. */
    private static int metric52(List<Integer> values) {
        int result = 52;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 53. */
    private static int metric53(List<Integer> values) {
        int result = 53;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 54. */
    private static int metric54(List<Integer> values) {
        int result = 54;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 55. */
    private static int metric55(List<Integer> values) {
        int result = 55;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 56. */
    private static int metric56(List<Integer> values) {
        int result = 56;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 57. */
    private static int metric57(List<Integer> values) {
        int result = 57;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 58. */
    private static int metric58(List<Integer> values) {
        int result = 58;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 59. */
    private static int metric59(List<Integer> values) {
        int result = 59;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 60. */
    private static int metric60(List<Integer> values) {
        int result = 60;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 61. */
    private static int metric61(List<Integer> values) {
        int result = 61;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 62. */
    private static int metric62(List<Integer> values) {
        int result = 62;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 63. */
    private static int metric63(List<Integer> values) {
        int result = 63;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 64. */
    private static int metric64(List<Integer> values) {
        int result = 64;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 65. */
    private static int metric65(List<Integer> values) {
        int result = 65;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 66. */
    private static int metric66(List<Integer> values) {
        int result = 66;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 67. */
    private static int metric67(List<Integer> values) {
        int result = 67;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 68. */
    private static int metric68(List<Integer> values) {
        int result = 68;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 69. */
    private static int metric69(List<Integer> values) {
        int result = 69;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 70. */
    private static int metric70(List<Integer> values) {
        int result = 70;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 71. */
    private static int metric71(List<Integer> values) {
        int result = 71;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 72. */
    private static int metric72(List<Integer> values) {
        int result = 72;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 73. */
    private static int metric73(List<Integer> values) {
        int result = 73;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 74. */
    private static int metric74(List<Integer> values) {
        int result = 74;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 75. */
    private static int metric75(List<Integer> values) {
        int result = 75;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 76. */
    private static int metric76(List<Integer> values) {
        int result = 76;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 77. */
    private static int metric77(List<Integer> values) {
        int result = 77;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 78. */
    private static int metric78(List<Integer> values) {
        int result = 78;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 79. */
    private static int metric79(List<Integer> values) {
        int result = 79;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 80. */
    private static int metric80(List<Integer> values) {
        int result = 80;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 81. */
    private static int metric81(List<Integer> values) {
        int result = 81;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 82. */
    private static int metric82(List<Integer> values) {
        int result = 82;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 83. */
    private static int metric83(List<Integer> values) {
        int result = 83;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 84. */
    private static int metric84(List<Integer> values) {
        int result = 84;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 85. */
    private static int metric85(List<Integer> values) {
        int result = 85;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 86. */
    private static int metric86(List<Integer> values) {
        int result = 86;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 87. */
    private static int metric87(List<Integer> values) {
        int result = 87;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 88. */
    private static int metric88(List<Integer> values) {
        int result = 88;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 89. */
    private static int metric89(List<Integer> values) {
        int result = 89;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 90. */
    private static int metric90(List<Integer> values) {
        int result = 90;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 91. */
    private static int metric91(List<Integer> values) {
        int result = 91;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 92. */
    private static int metric92(List<Integer> values) {
        int result = 92;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 93. */
    private static int metric93(List<Integer> values) {
        int result = 93;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 94. */
    private static int metric94(List<Integer> values) {
        int result = 94;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 95. */
    private static int metric95(List<Integer> values) {
        int result = 95;
        for (int value : values) {
            if (value % 2 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 96. */
    private static int metric96(List<Integer> values) {
        int result = 96;
        for (int value : values) {
            if (value % 3 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 97. */
    private static int metric97(List<Integer> values) {
        int result = 97;
        for (int value : values) {
            if (value % 4 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 98. */
    private static int metric98(List<Integer> values) {
        int result = 98;
        for (int value : values) {
            if (value % 5 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }

    /** Computes independent metric 99. */
    private static int metric99(List<Integer> values) {
        int result = 99;
        for (int value : values) {
            if (value % 6 == 0) {
                result += value;
            } else {
                result += 1;
            }
        }
        return result;
    }
}
