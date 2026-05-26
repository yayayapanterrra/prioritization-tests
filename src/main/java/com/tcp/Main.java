package com.tcp;

import com.tcp.algorithm.AdditionalPrioritizer;
import com.tcp.algorithm.GeneticPrioritizer;
import com.tcp.algorithm.Prioritizer;
import com.tcp.algorithm.RandomPrioritizer;
import com.tcp.data.DataLoader;
import com.tcp.data.DataLoader.FaultMatrixData;
import com.tcp.fitness.GaFitnessFunction;
import com.tcp.metric.ApfdCalculator;
import com.tcp.metric.ApfdCostCalculator;
import com.tcp.model.TestCase;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Точка входа бенчмарка приоритизации регрессионных тестов.
 *
 * Алгоритмы:
 *   Random      -- случайная перестановка (среднее по 5 прогонам)
 *   Additional  -- жадный baseline по покрытию
 *   GA-Base     -- ГА только с покрытием (coverage weight = 1.0)
 *   GA-FP+Div   -- ГА с fault-proneness и диверсификацией (0.4 / 0.4 / 0.2)
 *
 * Метрики: APFD, APFDc
 * Результаты сохраняются в results/results.csv и results/results.json
 */
public class Main {

    private static final String BFM_PATH    = "data/bigfaultmatrix/bigfaultmatrix.txt";
    private static final String RESULTS_DIR = "results";
    private static final String CSV_PATH    = RESULTS_DIR + "/results.csv";
    private static final String JSON_PATH   = RESULTS_DIR + "/results.json";
    private static final int    RANDOM_RUNS = 5;

    private static final List<String> DEFAULT_PROJECTS = List.of(
            "Jsoup/93", "Csv/16", "JacksonDatabind/112",
            "Time/27", "Codec/18", "JacksonCore/26", "Lang/65"
    );

    // Хранит все результаты текущей сессии для записи в файлы
    private static final List<ResultRow> allResults = new ArrayList<>();

    public static void main(String[] args) {
        initResultsDir();
        Scanner sc = new Scanner(System.in);
        System.out.println("=== TCP Benchmark ===");

        while (true) {
            System.out.println("\n1. BigFaultMatrix");
            System.out.println("2. Defects4J (tcp-methods)");
            System.out.println("3. Синтетические данные");
            System.out.println("4. Все Defects4J проекты");
            System.out.println("5. Анализ чувствительности весов");
            System.out.println("0. Выход");
            System.out.print("Выбор: ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1": runBigFaultMatrix(); break;
                case "2":
                    System.out.print("Путь к проекту (например data/raw/Jsoup/93): ");
                    String path = sc.nextLine().trim();
                    runBenchmark(path, loadOrNull(() -> DataLoader.loadDefects4JData(path)));
                    break;
                case "3": runBenchmark("synthetic", runSynthetic(sc)); break;
                case "4": runAllProjects(sc); break;
                case "5": runWeightSensitivity(sc); break;
                case "0":
                    saveResults();
                    sc.close();
                    return;
                default: System.out.println("Введите 0-4.");
            }

            System.out.print("\nНажмите Enter...");
            sc.nextLine();
        }
    }

    // --- Прогоны ---

    private static void runBigFaultMatrix() {
        FaultMatrixData original = loadOrNull(() -> DataLoader.loadFaultMatrix(BFM_PATH));
        if (original == null) return;

        System.out.println("\n=== BigFaultMatrix: 4 вариации ===");

        String[] labels = { "BFM-original", "BFM-flip10", "BFM-flip20", "BFM-flip30" };
        double[] flips  = { 0.0, 0.1, 0.2, 0.3 };
        long[]   seeds  = { 0L,  1L,  2L,  3L  };

        System.out.printf("%n%-22s | %-8s | %-8s | %-8s | %-8s | %s%n",
                "Вариация", "Random", "Additional", "GA-Base", "GA-FP+Div", "Лучший");
        System.out.println("-".repeat(82));

        for (int v = 0; v < 4; v++) {
            FaultMatrixData data = (v == 0)
                    ? original
                    : DataLoader.createVariation(original, flips[v], seeds[v]);

            int gens = adaptiveGens(data.tests.size());
            Map<String, double[]> metrics = collectMetrics(data, gens);

            double[] rnd = metrics.get("Random");
            double[] add = metrics.get("Additional");
            double[] gab = metrics.get("GA-Base");
            double[] gaf = metrics.get("GA-FP+Div");

            String best = bestName(new double[]{rnd[0], add[0], gab[0], gaf[0]},
                    new String[]{"Random", "Additional", "GA-Base", "GA-FP+Div"});

            System.out.printf("%-22s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %s%n",
                    labels[v], rnd[0], add[0], gab[0], gaf[0], best);

            allResults.add(new ResultRow(labels[v], data.tests.size(), data.totalFaults,
                    rnd[0], rnd[1], add[0], add[1], gab[0], gab[1], gaf[0], gaf[1]));
        }
        System.out.println("-".repeat(82));
    }

    private static FaultMatrixData runSynthetic(Scanner sc) {
        int tests   = readInt(sc,    "Тестов (100): ",   100);
        int faults  = readInt(sc,    "Фолтов (50): ",     50);
        double dens = readDouble(sc, "Плотность (0.1): ", 0.1);
        return DataLoader.generateSynthetic(tests, faults, dens);
    }

    private static void runBenchmark(String label, FaultMatrixData data) {
        if (data == null || data.tests.isEmpty() || data.totalFaults == 0) {
            System.err.println("Нет данных для бенчмарка.");
            return;
        }

        int gens = adaptiveGens(data.tests.size());
        System.out.printf("  Поколений ГА: %d%n", gens);

        Map<String, double[]> metrics = collectMetrics(data, gens);
        printResults(metrics);

        double[] rnd = metrics.get("Random");
        double[] add = metrics.get("Additional");
        double[] gab = metrics.get("GA-Base");
        double[] gaf = metrics.get("GA-FP+Div");

        allResults.add(new ResultRow(label, data.tests.size(), data.totalFaults,
                rnd[0], rnd[1], add[0], add[1], gab[0], gab[1], gaf[0], gaf[1]));
    }

    private static void runAllProjects(Scanner sc) {
        System.out.print("Базовый путь к данным (например data/raw): ");
        String base = sc.nextLine().trim();

        System.out.println("\n=== ПАКЕТНЫЙ ЗАПУСК ===");
        System.out.printf("%-25s | %-8s | %-8s | %-8s | %-8s | %s%n",
                "Проект", "Random", "Additional", "GA-Base", "GA-FP+Div", "Лучший");
        System.out.println("-".repeat(82));

        for (String project : DEFAULT_PROJECTS) {
            FaultMatrixData data = loadOrNull(() -> DataLoader.loadDefects4JData(base + "/" + project));

            if (data == null || data.tests.isEmpty() || data.totalFaults == 0) {
                System.out.printf("%-25s | ПРОПУЩЕН%n", project);
                continue;
            }

            int gens = adaptiveGens(data.tests.size());
            Map<String, double[]> metrics = collectMetrics(data, gens);

            double[] rnd = metrics.get("Random");
            double[] add = metrics.get("Additional");
            double[] gab = metrics.get("GA-Base");
            double[] gaf = metrics.get("GA-FP+Div");

            String best = bestName(new double[]{rnd[0], add[0], gab[0], gaf[0]},
                    new String[]{"Random", "Additional", "GA-Base", "GA-FP+Div"});

            System.out.printf("%-25s | %-8.4f | %-8.4f | %-8.4f | %-8.4f | %s%n",
                    project, rnd[0], add[0], gab[0], gaf[0], best);

            allResults.add(new ResultRow(project, data.tests.size(), data.totalFaults,
                    rnd[0], rnd[1], add[0], add[1], gab[0], gab[1], gaf[0], gaf[1]));
        }
        System.out.println("-".repeat(82));
    }

    // --- Сбор метрик ---

    private static Map<String, double[]> collectMetrics(FaultMatrixData data, int gens) {
        Map<String, double[]> result = new LinkedHashMap<>();

        // Random: среднее по RANDOM_RUNS прогонам
        double sumApfd = 0, sumApfdc = 0;
        for (int r = 0; r < RANDOM_RUNS; r++) {
            List<TestCase> ordered = new RandomPrioritizer(r).prioritize(new ArrayList<>(data.tests));
            sumApfd  += ApfdCalculator.calculate(ordered, data.testToFaults, data.totalFaults);
            sumApfdc += ApfdCostCalculator.calculateWithCost(ordered, data.testToFaults, data.totalFaults);
        }
        result.put("Random", new double[]{ sumApfd / RANDOM_RUNS, sumApfdc / RANDOM_RUNS });
        orderedResults.put("Random", new RandomPrioritizer(0).prioritize(new ArrayList<>(data.tests)));

        // Additional
        List<TestCase> addOrdered = new AdditionalPrioritizer().prioritize(new ArrayList<>(data.tests));
        result.put("Additional", new double[]{
                ApfdCalculator.calculate(addOrdered, data.testToFaults, data.totalFaults),
                ApfdCostCalculator.calculateWithCost(addOrdered, data.testToFaults, data.totalFaults)
        });
        orderedResults.put("Additional", addOrdered);

        // GA-Base
        List<TestCase> gaBaseOrdered = new GeneticPrioritizer(100, gens, 0.8, 0.2,
                new GaFitnessFunction(1.0, 0.0, 0.0))
                .prioritize(new ArrayList<>(data.tests));
        result.put("GA-Base", new double[]{
                ApfdCalculator.calculate(gaBaseOrdered, data.testToFaults, data.totalFaults),
                ApfdCostCalculator.calculateWithCost(gaBaseOrdered, data.testToFaults, data.totalFaults)
        });
        orderedResults.put("GA-Base", gaBaseOrdered);

        // GA-FP+Div
        List<TestCase> gaFpOrdered = new GeneticPrioritizer(100, gens, 0.8, 0.2,
                new GaFitnessFunction(0.6, 0.4, 0.0))
                .prioritize(new ArrayList<>(data.tests));
        result.put("GA-FP+Div", new double[]{
                ApfdCalculator.calculate(gaFpOrdered, data.testToFaults, data.totalFaults),
                ApfdCostCalculator.calculateWithCost(gaFpOrdered, data.testToFaults, data.totalFaults)
        });
        orderedResults.put("GA-FP+Div", gaFpOrdered);

        return result;
    }

    // хранит упорядоченные списки тестов для вывода топ-3
    private static final Map<String, List<TestCase>> orderedResults = new LinkedHashMap<>();

    private static void printResults(Map<String, double[]> metrics) {
        System.out.println("\n--- РЕЗУЛЬТАТЫ ---");
        System.out.printf("%-20s | %-8s | %-8s%n", "Алгоритм", "APFD", "APFDc");
        System.out.println("-".repeat(42));

        String bestName = null; double bestApfd = -1;
        for (Map.Entry<String, double[]> e : metrics.entrySet()) {
            System.out.printf("%-20s | %-8.4f | %-8.4f%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            if (e.getValue()[0] > bestApfd) { bestApfd = e.getValue()[0]; bestName = e.getKey(); }
        }
        System.out.println("-".repeat(42));
        System.out.printf("Лучший: %s (APFD = %.4f)%n", bestName, bestApfd);

        // Топ-3 тестов для каждого алгоритма
        System.out.println("\n--- ТОП-3 ТЕСТОВ ---");
        for (Map.Entry<String, List<TestCase>> e : orderedResults.entrySet()) {
            System.out.printf("%n%s:%n", e.getKey());
            System.out.printf("  %-12s | %-8s | %-10s%n", "Тест", "Risk", "Coverage");
            System.out.println("  " + "-".repeat(36));
            List<TestCase> ordered = e.getValue();
            for (int i = 0; i < Math.min(3, ordered.size()); i++) {
                TestCase t = ordered.get(i);
                System.out.printf("  %-12s | %-8.3f | %d%n",
                        t.getId(), t.getFaultProneness(),
                        t.getCoveredMethods().size());
            }
        }
        orderedResults.clear();
    }

    // --- Сохранение результатов ---

    private static void saveResults() {
        if (allResults.isEmpty()) return;
        try {
            new File(RESULTS_DIR).mkdirs();
            saveCsv();
            saveJson();
            System.out.println("\nРезультаты сохранены:");
            System.out.println("  CSV:  " + CSV_PATH);
            System.out.println("  JSON: " + JSON_PATH);
        } catch (Exception e) {
            System.err.println("Ошибка сохранения: " + e.getMessage());
        }
    }

    private static void saveCsv() throws Exception {
        // Используем ; как разделитель — Excel в русской локали ожидает именно его
        // sep= в первой строке подсказывает Excel какой разделитель использован
        boolean exists = new File(CSV_PATH).exists();
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(CSV_PATH, true), "UTF-8"))) {
            if (!exists) {
                pw.println("sep=;");  // директива для Excel — автоматически выберет разделитель
                pw.println("timestamp;dataset;tests;faults;" +
                        "random_apfd;random_apfdc;" +
                        "additional_apfd;additional_apfdc;" +
                        "gabase_apfd;gabase_apfdc;" +
                        "gafpdiv_apfd;gafpdiv_apfdc");
            }
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            for (ResultRow r : allResults)
                pw.printf("%s;%s;%d;%d;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f;%.4f%n",
                        ts, r.dataset, r.tests, r.faults,
                        r.randomApfd, r.randomApfdc,
                        r.addApfd, r.addApfdc,
                        r.gaBaseApfd, r.gaBaseApfdc,
                        r.gaFpApfd, r.gaFpApfdc);
        }
    }

    private static void saveJson() throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < allResults.size(); i++) {
            ResultRow r = allResults.get(i);
            sb.append("  {\n");
            sb.append(String.format("    \"timestamp\": \"%s\",\n", ts));
            sb.append(String.format("    \"dataset\": \"%s\",\n", r.dataset));
            sb.append(String.format("    \"tests\": %d,\n", r.tests));
            sb.append(String.format("    \"faults\": %d,\n", r.faults));
            sb.append(String.format("    \"Random\":     {\"apfd\": %.4f, \"apfdc\": %.4f},\n", r.randomApfd, r.randomApfdc));
            sb.append(String.format("    \"Additional\": {\"apfd\": %.4f, \"apfdc\": %.4f},\n", r.addApfd, r.addApfdc));
            sb.append(String.format("    \"GA-Base\":    {\"apfd\": %.4f, \"apfdc\": %.4f},\n", r.gaBaseApfd, r.gaBaseApfdc));
            sb.append(String.format("    \"GA-FP+Div\": {\"apfd\": %.4f, \"apfdc\": %.4f}\n", r.gaFpApfd, r.gaFpApfdc));
            sb.append(i < allResults.size() - 1 ? "  },\n" : "  }\n");
        }
        sb.append("]");
        try (PrintWriter pw = new PrintWriter(new FileWriter(JSON_PATH))) {
            pw.print(sb);
        }
    }

    // --- Вспомогательные методы ---

    private static int adaptiveGens(int n) {
        return Math.max(50, (int)(Math.log(n) / Math.log(2)) * 10);
    }

    private static String bestName(double[] apfds, String[] names) {
        int best = 0;
        for (int i = 1; i < apfds.length; i++) if (apfds[i] > apfds[best]) best = i;
        return names[best];
    }

    private static void initResultsDir() {
        new File(RESULTS_DIR).mkdirs();
    }

    private static FaultMatrixData loadOrNull(ThrowingSupplier<FaultMatrixData> supplier) {
        try { return supplier.get(); }
        catch (Exception e) { System.err.println("Ошибка загрузки: " + e.getMessage()); return null; }
    }

    private static int readInt(Scanner sc, String prompt, int defaultVal) {
        System.out.print(prompt);
        String s = sc.nextLine().trim();
        return s.isEmpty() ? defaultVal : Integer.parseInt(s);
    }

    private static double readDouble(Scanner sc, String prompt, double defaultVal) {
        System.out.print(prompt);
        String s = sc.nextLine().trim();
        return s.isEmpty() ? defaultVal : Double.parseDouble(s);
    }


    /**
     * Анализ чувствительности весов фитнесс-функции.
     * Перебирает конфигурации весов (wc, wr, wd) с шагом 0.2
     * и выводит средний APFD по всем проектам Defects4J.
     */
    private static void runWeightSensitivity(Scanner sc) {
        System.out.print("Базовый путь к данным (например data/raw): ");
        String base = sc.nextLine().trim();

        // Загружаем данные всех проектов заранее
        List<FaultMatrixData> datasets = new ArrayList<>();
        for (String project : DEFAULT_PROJECTS) {
            FaultMatrixData data = loadOrNull(() -> DataLoader.loadDefects4JData(base + "/" + project));
            if (data != null && !data.tests.isEmpty() && data.totalFaults > 0)
                datasets.add(data);
        }
        if (datasets.isEmpty()) { System.err.println("Нет данных."); return; }

        System.out.println("\n=== АНАЛИЗ ЧУВСТВИТЕЛЬНОСТИ ВЕСОВ ===");
        System.out.printf("%-8s %-8s %-8s | %-8s%n", "wc", "wr", "wd", "APFD avg");
        System.out.println("-".repeat(40));

        double bestAvg = -1;
        double bestWc = 0, bestWr = 0, bestWd = 0;

        // Перебираем веса с шагом 0.2, сумма = 1.0
        for (int ic = 0; ic <= 10; ic++) {
            double wc = ic / 10.0;
            for (int ir = 0; ir <= 10 - ic; ir++) {
                double wr = ir / 10.0;
                double wd = Math.round((1.0 - wc - wr) * 10.0) / 10.0;
                if (wd < 0) continue;
                // Шаг 0.2
                if (ic % 2 != 0 || ir % 2 != 0) continue;

                GaFitnessFunction ff = new GaFitnessFunction(wc, wr, wd);
                double sumApfd = 0;
                for (FaultMatrixData data : datasets) {
                    int gens = adaptiveGens(data.tests.size());
                    List<TestCase> ordered = new GeneticPrioritizer(100, gens, 0.8, 0.2, ff)
                            .prioritize(new ArrayList<>(data.tests));
                    sumApfd += ApfdCalculator.calculate(ordered, data.testToFaults, data.totalFaults);
                }
                double avg = sumApfd / datasets.size();
                System.out.printf("%-8.1f %-8.1f %-8.1f | %-8.4f%n", wc, wr, wd, avg);

                if (avg > bestAvg) { bestAvg = avg; bestWc = wc; bestWr = wr; bestWd = wd; }
            }
        }

        System.out.println("-".repeat(40));
        System.out.printf("Лучшая конфигурация: wc=%.1f, wr=%.1f, wd=%.1f (APFD=%.4f)%n",
                bestWc, bestWr, bestWd, bestAvg);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }

    // --- Модель строки результата ---

    private static class ResultRow {
        final String dataset;
        final int tests, faults;
        final double randomApfd, randomApfdc;
        final double addApfd, addApfdc;
        final double gaBaseApfd, gaBaseApfdc;
        final double gaFpApfd, gaFpApfdc;

        ResultRow(String dataset, int tests, int faults,
                  double randomApfd, double randomApfdc,
                  double addApfd, double addApfdc,
                  double gaBaseApfd, double gaBaseApfdc,
                  double gaFpApfd, double gaFpApfdc) {
            this.dataset = dataset; this.tests = tests; this.faults = faults;
            this.randomApfd = randomApfd; this.randomApfdc = randomApfdc;
            this.addApfd = addApfd; this.addApfdc = addApfdc;
            this.gaBaseApfd = gaBaseApfd; this.gaBaseApfdc = gaBaseApfdc;
            this.gaFpApfd = gaFpApfd; this.gaFpApfdc = gaFpApfdc;
        }
    }
}