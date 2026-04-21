package com.tcp.metric;

import com.tcp.model.TestCase;

import java.util.*;

/**
 * Метрика APFDc (APFD with Cost) - APFD с учётом времени выполнения тестов.
 *
 * В отличие от APFD, взвешивает обнаружение мутантов по кумулятивному времени:
 * мутант, найденный быстрым тестом в начале, ценится выше чем найденный медленным в конце.
 *
 * Формула: APFDc = sum(totalTime - cumTime_i + execTime_i / 2) / (totalTime * m)
 *   totalTime -- суммарное время всех тестов
 *   cumTime_i -- кумулятивное время до конца теста i
 *   m -- число мутантов
 */
public class ApfdCostCalculator {

    public static double calculateWithCost(List<TestCase> orderedTests,
                                           Map<String, Set<String>> testToMutants,
                                           int totalMutants) {
        if (orderedTests.isEmpty() || totalMutants == 0) return 0.0;

        double totalTime = orderedTests.stream().mapToDouble(TestCase::getExecutionTimeMs).sum();
        Set<String> detected = new HashSet<>();
        double weightedSum = 0.0;
        double cumTime = 0.0;

        for (TestCase test : orderedTests) {
            cumTime += test.getExecutionTimeMs();
            for (String mutant : testToMutants.getOrDefault(test.getId(), Collections.emptySet())) {
                if (detected.add(mutant))
                    weightedSum += totalTime - cumTime + test.getExecutionTimeMs() / 2.0;
            }
        }

        return weightedSum / (totalTime * totalMutants);
    }
}