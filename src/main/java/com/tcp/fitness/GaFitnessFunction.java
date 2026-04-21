package com.tcp.fitness;

import com.tcp.model.TestCase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Фитнесс-функция для генетического алгоритма.
 *
 * Оценивает качество порядка тестов по трём компонентам, каждая в [0, 1]:
 *   coverage -- доля покрытых уникальных методов от всех в пуле
 *   faultProneness -- средний риск первых TOP_K тестов
 *   diversity -- среднее расстояние Жаккара между соседними парами в топ-TOP_K
 *
 * Итоговый фитнесс = взвешенная сумма трёх компонент (веса задаются в конструкторе).
 */
public class GaFitnessFunction {

    // число тестов в начале последовательности, учитываемых для risk и diversity
    private static final int TOP_K = 10;

    private final double weightCoverage;
    private final double weightFaultProneness;
    private final double weightDiversity;

    public GaFitnessFunction(double weightCoverage, double weightFaultProneness, double weightDiversity) {
        this.weightCoverage = weightCoverage;
        this.weightFaultProneness = weightFaultProneness;
        this.weightDiversity = weightDiversity;
    }

    // Вычисляет фитнесс последовательности.
    // totalMethods вычисляется каждый раз из пула - безопасно при смене проектов.
    public double calculateFitness(List<TestCase> seq) {
        if (seq.isEmpty()) return 0.0;

        Set<String> all = new HashSet<>();
        for (TestCase t : seq) all.addAll(t.getCoveredMethods());
        int totalMethods = Math.max(all.size(), 1);

        return weightCoverage      * coverage(seq, totalMethods)
                + weightFaultProneness * faultProneness(seq)
                + weightDiversity      * diversity(seq);
    }

    // Доля уникальных покрытых методов от totalMethods
    private double coverage(List<TestCase> seq, int totalMethods) {
        Set<String> covered = new HashSet<>();
        for (TestCase t : seq) covered.addAll(t.getCoveredMethods());
        return (double) covered.size() / totalMethods;
    }

    // Средняя fault-proneness первых TOP_K тестов
    private double faultProneness(List<TestCase> seq) {
        int k = Math.min(seq.size(), TOP_K);
        double sum = 0.0;
        for (int i = 0; i < k; i++) sum += seq.get(i).getFaultProneness();
        return sum / k;
    }

    // Среднее расстояние Жаккара между соседними парами в топ-TOP_K
    private double diversity(List<TestCase> seq) {
        int k = Math.min(seq.size(), TOP_K);
        if (k < 2) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < k - 1; i++)
            sum += jaccard(seq.get(i).getCoveredMethods(), seq.get(i + 1).getCoveredMethods());
        return sum / (k - 1);
    }

    // Расстояние Жаккара: 0 = одинаковые, 1 = полностью разные множества
    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0.0;
        if (a.isEmpty() || b.isEmpty()) return 1.0;

        // считаем пересечение без создания нового множества
        int intersection = 0;
        for (String s : a) if (b.contains(s)) intersection++;
        int union = a.size() + b.size() - intersection;

        return 1.0 - (double) intersection / union;
    }
}