package com.tcp.data;

import com.tcp.model.TestCase;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class DataLoader {

    public static class FaultMatrixData {
        public final List<TestCase> tests;
        public final Map<String, Set<String>> testToFaults;
        public final int totalFaults;

        public FaultMatrixData(List<TestCase> tests, Map<String, Set<String>> testToFaults, int totalFaults) {
            this.tests = tests;
            this.testToFaults = testToFaults;
            this.totalFaults = totalFaults;
        }
    }

    // --- BigFaultMatrix: testId,0,1,0,... ---

    public static FaultMatrixData loadFaultMatrix(String filePath) throws Exception {
        List<TestCase> tests = new ArrayList<>();
        Map<String, Set<String>> testToFaults = new HashMap<>();
        int totalFaults = -1;

        for (String line : readLines(filePath)) {
            String[] parts = line.split(",");
            if (parts.length < 2) continue;
            if (totalFaults == -1) totalFaults = parts.length - 1;

            String testId = parts[0].trim();
            Set<String> killed = new HashSet<>();
            for (int i = 1; i < parts.length; i++)
                if ("1".equals(parts[i].trim())) killed.add("F" + (i - 1));

            double fp = totalFaults > 0 ? (double) killed.size() / totalFaults : 0.0;
            tests.add(new TestCase(testId, killed, fp, 100.0));
            testToFaults.put(testId, killed);
        }

        return new FaultMatrixData(tests, testToFaults, Math.max(totalFaults, 0));
    }

    public static FaultMatrixData createVariation(FaultMatrixData original, double flipRatio, long seed) {
        Random rng = new Random(seed);
        List<TestCase> newTests = new ArrayList<>();
        Map<String, Set<String>> newMap = new HashMap<>();

        for (TestCase t : original.tests) {
            Set<String> faults = new HashSet<>(original.testToFaults.get(t.getId()));
            for (int f = 0; f < original.totalFaults; f++) {
                if (rng.nextDouble() < flipRatio) {
                    String id = "F" + f;
                    if (!faults.add(id)) faults.remove(id);
                }
            }
            double fp = original.totalFaults > 0 ? (double) faults.size() / original.totalFaults : 0.0;
            newTests.add(new TestCase(t.getId(), faults, fp, 100.0));
            newMap.put(t.getId(), faults);
        }

        return new FaultMatrixData(newTests, newMap, original.totalFaults);
    }

    // --- Defects4J / tcp-methods ---
    // Struktura papki projectPath:
    //   coverage/matrix.csv          -- zagolovok: test,method1,...; stroki: testId,0,1,...
    //   mutants/kill.csv             -- MutantNo,[FAIL|TIME|EXC|LIVE|UNCOV]
    //   mutants/testMap.csv          -- TestNo,TestName,Runtime
    //   mutants/covMap.csv           -- TestNo,MutantNo
    //   cia/impact_probabilities.csv -- owner,method,descriptor,roc,fwd,impact

    public static FaultMatrixData loadDefects4JData(String projectPath) throws Exception {
        List<TestCase> tests = loadCoverage(projectPath + "/coverage/matrix.csv");
        Map<String, Set<String>> testToFaults = loadKills(
                projectPath + "/mutants/kill.csv",
                projectPath + "/mutants/testMap.csv",
                projectPath + "/mutants/covMap.csv",
                tests);
        tests = applyImpact(tests, projectPath + "/cia/impact_probabilities.csv");

        int totalFaults = (int) testToFaults.values().stream().flatMap(Set::stream).distinct().count();
        System.out.printf("  Tests: %d, mutants: %d%n", tests.size(), totalFaults);
        return new FaultMatrixData(tests, testToFaults, totalFaults);
    }

    private static List<TestCase> loadCoverage(String path) throws Exception {
        List<TestCase> tests = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = splitCsv(line);
                if (header) {
                    // первая строка - заголовок с именами методов
                    for (int i = 1; i < parts.length; i++)
                        methods.add(parts[i].replace("\"", "").trim());
                    header = false;
                    continue;
                }
                Set<String> covered = new HashSet<>();
                for (int i = 1; i < parts.length && i - 1 < methods.size(); i++)
                    if ("1".equals(parts[i].trim())) covered.add(methods.get(i - 1));
                tests.add(new TestCase(parts[0].trim(), covered, 0.0, 100.0));
            }
        }
        return tests;
    }

    private static Map<String, Set<String>> loadKills(String killPath, String testMapPath,
                                                      String covMapPath, List<TestCase> tests) throws Exception {
        Map<String, Set<String>> result = new HashMap<>();
        for (TestCase t : tests) result.put(t.getId(), new HashSet<>());

        // kill.csv: MutantNo -> status (FAIL/TIME/EXC = killed)
        Set<String> killed = new HashSet<>();
        for (String line : readLines(killPath)) {
            String[] p = line.split(",");
            if (p.length < 2) continue;
            String status = p[1].trim();
            if (status.equals("FAIL") || status.equals("TIME") || status.equals("EXC"))
                killed.add(p[0].trim());
        }

        // testMap.csv: TestNo -> TestName (className only, no method)
        Map<String, String> testNoToName = new HashMap<>();
        for (String line : readLines(testMapPath)) {
            String[] p = line.split(",");
            if (p.length < 2) continue;
            testNoToName.put(p[0].trim(), p[1].trim());
        }

        // coverage keys look like "org.jsoup.FormElementTest#methodName"
        // testMap has only "org.jsoup.FormElementTest" (class name, no method)
        // build index: className -> list of full testIds from coverage
        Map<String, List<String>> classToIds = new HashMap<>();
        for (String testId : result.keySet()) {
            String cls = testId.contains("#") ? testId.substring(0, testId.indexOf('#')) : testId;
            classToIds.computeIfAbsent(cls, k -> new ArrayList<>()).add(testId);
        }

        // covMap.csv: TestNo,MutantNo - assign killed mutants to tests
        for (String line : readLines(covMapPath)) {
            String[] p = line.split(",");
            if (p.length < 2) continue;
            String testNo = p[0].trim(), mutantNo = p[1].trim();
            if (!killed.contains(mutantNo)) continue;
            String className = testNoToName.get(testNo);
            if (className == null) continue;
            for (String testId : classToIds.getOrDefault(className, Collections.emptyList()))
                result.get(testId).add("M" + mutantNo);
        }

        return result;
    }

    private static List<TestCase> applyImpact(List<TestCase> tests, String path) throws Exception {
        if (!new File(path).exists()) return tests;

        // Нормализуем ключи CIA: org/jsoup/nodes/FormElement,<init>,... -> org.jsoup.nodes.FormElement#init
        Map<String, Double> scores = new HashMap<>();
        for (String line : readLines(path)) {
            String[] p = splitCsv(line);
            if (p.length < 6) continue;
            try {
                String className = p[0].trim().replace('/', '.');
                // убираем угловые скобки: <init> -> init, <clinit> -> clinit
                String methodName = p[1].trim().replace("<", "").replace(">", "");
                String key = className + "#" + methodName;
                double score = Double.parseDouble(p[5].trim());
                scores.merge(key, score, Math::max);
            } catch (NumberFormatException ignored) {}
        }

        // Нормализуем ключи coverage до того же формата:
        // "org.jsoup.parser$Parser#Parser(org.jsoup.parser.TreeBuilder):24" -> "org.jsoup.parser.Parser#Parser"
        return tests.stream().map(t -> {
            double sum = 0.0; int count = 0;
            for (String m : t.getCoveredMethods()) {
                String key = normalizeCoverageKey(m);
                if (scores.containsKey(key)) { sum += scores.get(key); count++; }
            }
            return new TestCase(t.getId(), t.getCoveredMethods(),
                    count > 0 ? sum / count : 0.0, t.getExecutionTimeMs());
        }).collect(Collectors.toList());
    }

    // "org.jsoup.parser$Parser#Parser(org.jsoup.parser.TreeBuilder):24" -> "org.jsoup.parser.Parser#Parser"
    private static String normalizeCoverageKey(String raw) {
        String s = raw.replaceAll(":\\d+$", "");   // убираем :24
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren); // убираем параметры
        return s.replace('$', '.');                 // $ -> . для вложенных классов
    }


    // --- Synthetic data ---

    /**
     * Генерирует синтетические данные с независимым риском и покрытием.
     *
     * Тесты делятся на три группы чтобы GA-FP+Div имел реальное преимущество:
     *   (30%): большое покрытие, мало фолтов -- жадный выбирает их первыми.
     *   (20%): малое покрытие, высокий риск и много ключевых фолтов.
     *   Обычные (50%): покрытие и фолты пропорциональны density.
     */
    public static FaultMatrixData generateSynthetic(int numTests, int numFaults, double density) {
        List<TestCase> tests = new ArrayList<>();
        Map<String, Set<String>> map = new HashMap<>();
        Random rng = new Random(42);

        // первые 40% фолтов считаются "рискованными"
        int riskFaults = (int)(numFaults * 0.4);

        for (int t = 0; t < numTests; t++) {
            Set<String> faults = new HashSet<>();
            double fp;
            int execTime = 50 + rng.nextInt(150);
            double roll = rng.nextDouble();

            if (roll < 0.30) {
                // "Ловушка": много обычных фолтов, но не рискованных -- низкий риск
                for (int f = riskFaults; f < numFaults; f++)
                    if (rng.nextDouble() < density * 2.5) faults.add("F" + f);
                fp = 0.05 + rng.nextDouble() * 0.05;

            } else if (roll < 0.50) {
                // "Жемчужина": мало покрытия, но попадает в рискованные фолты
                for (int f = 0; f < riskFaults; f++)
                    if (rng.nextDouble() < density * 1.5) faults.add("F" + f);
                fp = 0.6 + rng.nextDouble() * 0.4;

            } else {
                // Обычный тест
                for (int f = 0; f < numFaults; f++)
                    if (rng.nextDouble() < density) faults.add("F" + f);
                fp = numFaults > 0 ? (double) faults.size() / numFaults : 0.0;
            }

            TestCase tc = new TestCase("T" + t, faults, fp, execTime);
            tests.add(tc);
            map.put(tc.getId(), new HashSet<>(faults));
        }

        return new FaultMatrixData(tests, map, numFaults);
    }

    // --- Utils ---

    // Read file line by line, skip empty lines and header (first line)
    private static List<String> readLines(String path) throws Exception {
        List<String> lines = new ArrayList<>();
        File f = new File(path);
        if (!f.exists()) return lines;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (header) { header = false; continue; }
                lines.add(line);
            }
        }
        return lines;
    }

    // Split CSV line respecting commas inside quotes
    private static String[] splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) { tokens.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}