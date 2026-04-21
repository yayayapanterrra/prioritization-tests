package com.tcp.algorithm;

import com.tcp.model.TestCase;
import java.util.*;


/**
 * базовый жадный алгоритм
 * на каждом шаге выбирает тест, покрывающий наибольшее количество
 */

public class AdditionalPrioritizer implements Prioritizer {

    @Override
    public List<TestCase> prioritize(List<TestCase> testPool) {
        List<TestCase> result = new ArrayList<>();
        Set<String> coveredMethods = new HashSet<>();
        //копия пула, чтобы не модифицировать исходный список
        List<TestCase> remaining = new ArrayList<>(testPool);

        while (!remaining.isEmpty()) {
            TestCase bestTest = null;
            int maxNewCoverage = -1;

            //поиск теста с максимальным новым покрытием
            for (TestCase test : remaining) {
                int newCount = 0;
                for (String method : test.getCoveredMethods()) {
                    if (!coveredMethods.contains(method)) {
                        newCount++;
                    }
                }

                if (newCount > maxNewCoverage) {
                    maxNewCoverage = newCount;
                    bestTest = test;
                } else if (newCount == maxNewCoverage && bestTest != null) {
                    //если покрытие одинаковое берем тест с большей fault proneness
                    if (test.getFaultProneness() > bestTest.getFaultProneness()) {
                        bestTest = test;
                    }
                }
            }
            //fallback: если все оставшиеся тесты не покрывают ничего нового
            if (bestTest == null) {
                bestTest = remaining.get(0);
            }

            result.add(bestTest);
            remaining.remove(bestTest);
            coveredMethods.addAll(bestTest.getCoveredMethods());

        }
        return result;
    }

    @Override
    public String getName() {
        return "Additional";
    }

}
