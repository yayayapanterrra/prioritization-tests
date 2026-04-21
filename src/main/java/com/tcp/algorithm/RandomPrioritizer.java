package com.tcp.algorithm;

import com.tcp.model.TestCase;

import java.util.*;

/**
 * Random Search -- случайная перестановка тестов.
 * Используется как baseline для сравнения с оптимизационными алгоритмами.
 *
 * Для воспроизводимости результатов используется фиксированный seed.
 * APFD усредняется по нескольким прогонам в Main.
 */
public class RandomPrioritizer implements Prioritizer {

    private final Random rng;

    // seed для воспроизводимости
    public RandomPrioritizer(long seed) {
        this.rng = new Random(seed);
    }

    @Override
    public List<TestCase> prioritize(List<TestCase> testPool) {
        List<TestCase> result = new ArrayList<>(testPool);
        Collections.shuffle(result, rng);
        return result;
    }

    @Override
    public String getName() { return "Random"; }
}