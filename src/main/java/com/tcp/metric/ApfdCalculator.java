package com.tcp.metric;

import com.tcp.model.TestCase;

import java.util.*;

/**
 * Метрика APFD (Average Percentage of Faults Detected).
 * Оценивает скорость обнаружения дефектов: чем ближе к 1, тем раньше находятся мутанты.
 *
 * Формула: APFD = 1 - sumTF / (n * m) + 1 / (2n)
 *   n -- число тестов
 *   m -- число мутантов
 *   sumTF -- сумма позиций первых тестов, обнаруживших каждый мутант
 */
public class ApfdCalculator {

    public static double calculate(List<TestCase> orderedTests,
                                   Map<String, Set<String>> testToMutants,
                                   int totalMutants) {
        int n = orderedTests.size();
        int m = totalMutants;
        if (n == 0 || m == 0) return 0.0;

        Set<String> detected = new HashSet<>();
        double sumTF = 0.0;

        for (int i = 0; i < n; i++) {
            for (String mutant : testToMutants.getOrDefault(orderedTests.get(i).getId(), Collections.emptySet())) {
                if (detected.add(mutant)) sumTF += (i + 1);
            }
        }

        return 1.0 - sumTF / ((double) n * m) + 1.0 / (2.0 * n);
    }
}