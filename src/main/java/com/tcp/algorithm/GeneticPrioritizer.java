package com.tcp.algorithm;

import com.tcp.fitness.GaFitnessFunction;
import com.tcp.metric.ApfdCalculator;
import com.tcp.model.TestCase;

import java.util.*;

/**
 * Генетический алгоритм приоритизации тестов.
 *
 * Поддерживает два режима фитнесс-функции:
 *   1. Прямой APFD -- если переданы testToFaults и totalFaults (как в статье Paygude et al.)
 *   2. GaFitnessFunction -- взвешенная сумма coverage + faultProneness + diversity
 *
 * Операторы:
 *   инициализация: случайное перемешивание
 *   селекция: турнирная (tournament selection)
 *   кроссовер: OX (Order Crossover)
 *   мутация: адаптивное число swap = sqrt(n)
 *   элитизм: лучшая особь переходит без изменений
 */
public class GeneticPrioritizer implements Prioritizer {

    private static final int TOURNAMENT_SIZE = 3;

    private final int populationSize;
    private final int generations;
    private final double crossoverRate;
    private final double mutationRate;
    private final GaFitnessFunction fitnessFunction; // null если используется APFD-режим
    private final Map<String, Set<String>> testToFaults; // null если используется GaFitnessFunction
    private final int totalFaults;
    private final Random rng = new Random(42);

    // Конструктор для режима GaFitnessFunction (coverage + faultProneness + diversity)
    public GeneticPrioritizer(int populationSize, int generations,
                              double crossoverRate, double mutationRate,
                              GaFitnessFunction fitnessFunction) {
        this(populationSize, generations, crossoverRate, mutationRate, fitnessFunction, null, 0);
    }

    // Конструктор для режима прямого APFD (как в статье Paygude et al.)
    public GeneticPrioritizer(int populationSize, int generations,
                              double crossoverRate, double mutationRate,
                              GaFitnessFunction fitnessFunction,
                              Map<String, Set<String>> testToFaults,
                              int totalFaults) {
        this.populationSize = populationSize;
        this.generations = generations;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.fitnessFunction = fitnessFunction;
        this.testToFaults = testToFaults;
        this.totalFaults = totalFaults;
    }

    // Выбирает режим фитнесса: прямой APFD или GaFitnessFunction
    private double fitness(List<TestCase> seq) {
        if (testToFaults != null && totalFaults > 0)
            return ApfdCalculator.calculate(seq, testToFaults, totalFaults);
        return fitnessFunction.calculateFitness(seq);
    }

    @Override
    public List<TestCase> prioritize(List<TestCase> testPool) {
        if (testPool.isEmpty()) return new ArrayList<>();

        // адаптивная интенсивность мутации: sqrt(n) swap
        final int mutationIntensity = Math.max(1, (int) Math.sqrt(testPool.size()));

        List<List<TestCase>> population = initPopulation(testPool);
        double[] fit = evalAll(population);

        int bestIdx = argmax(fit);
        List<TestCase> bestEver = new ArrayList<>(population.get(bestIdx));
        double bestFitness = fit[bestIdx];

        for (int gen = 0; gen < generations; gen++) {
            List<List<TestCase>> next = new ArrayList<>(populationSize);
            double[] nextFit = new double[populationSize];

            // элитизм
            next.add(new ArrayList<>(bestEver));
            nextFit[0] = bestFitness;

            while (next.size() < populationSize) {
                List<TestCase> child1 = new ArrayList<>(tournament(population, fit));
                List<TestCase> child2 = new ArrayList<>(tournament(population, fit));

                boolean crossed = false;
                if (rng.nextDouble() < crossoverRate) {
                    oxCrossover(child1, child2);
                    crossed = true;
                }

                boolean mutated1 = false, mutated2 = false;
                if (rng.nextDouble() < mutationRate) { mutate(child1, mutationIntensity); mutated1 = true; }
                if (rng.nextDouble() < mutationRate) { mutate(child2, mutationIntensity); mutated2 = true; }

                int i = next.size();
                next.add(child1);
                nextFit[i] = (crossed || mutated1)
                        ? fitness(child1)
                        : fit[indexOf(population, child1)];

                if (next.size() < populationSize) {
                    int j = next.size();
                    next.add(child2);
                    nextFit[j] = (crossed || mutated2)
                            ? fitness(child2)
                            : fit[indexOf(population, child2)];
                }
            }

            population = next;
            fit = nextFit;

            int idx = argmax(fit);
            if (fit[idx] > bestFitness) {
                bestFitness = fit[idx];
                bestEver = new ArrayList<>(population.get(idx));
            }
        }

        return bestEver;
    }

    private List<List<TestCase>> initPopulation(List<TestCase> pool) {
        List<List<TestCase>> pop = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            List<TestCase> ind = new ArrayList<>(pool);
            Collections.shuffle(ind, rng);
            pop.add(ind);
        }
        return pop;
    }

    private double[] evalAll(List<List<TestCase>> population) {
        double[] f = new double[population.size()];
        for (int i = 0; i < population.size(); i++)
            f[i] = fitness(population.get(i));
        return f;
    }

    private List<TestCase> tournament(List<List<TestCase>> population, double[] fit) {
        int best = rng.nextInt(population.size());
        for (int i = 1; i < TOURNAMENT_SIZE; i++) {
            int candidate = rng.nextInt(population.size());
            if (fit[candidate] > fit[best]) best = candidate;
        }
        return population.get(best);
    }

    /**
     * OX (Order Crossover): сохраняет валидную перестановку.
     * Сегмент [start, end] из p1 копируется в child1,
     * остаток заполняется из p2 в порядке появления. Аналогично для child2.
     */
    private void oxCrossover(List<TestCase> p1, List<TestCase> p2) {
        int size = p1.size();
        if (size < 2) return;

        int start = rng.nextInt(size);
        int end = rng.nextInt(size);
        if (start > end) { int tmp = start; start = end; end = tmp; }
        if (start == end) end = Math.min(end + 1, size - 1);

        List<TestCase> c1 = buildOxChild(p1, p2, start, end);
        List<TestCase> c2 = buildOxChild(p2, p1, start, end);
        p1.clear(); p1.addAll(c1);
        p2.clear(); p2.addAll(c2);
    }

    private List<TestCase> buildOxChild(List<TestCase> donor, List<TestCase> filler, int start, int end) {
        int size = donor.size();
        List<TestCase> child = new ArrayList<>(Collections.nCopies(size, null));
        Set<String> inSegment = new HashSet<>();

        for (int i = start; i <= end; i++) {
            child.set(i, donor.get(i));
            inSegment.add(donor.get(i).getId());
        }

        Queue<TestCase> rest = new ArrayDeque<>();
        for (TestCase t : filler)
            if (!inSegment.contains(t.getId())) rest.add(t);

        for (int i = 0; i < size; i++)
            if (child.get(i) == null) child.set(i, rest.poll());

        return child;
    }

    private void mutate(List<TestCase> ind, int intensity) {
        for (int k = 0; k < intensity; k++)
            Collections.swap(ind, rng.nextInt(ind.size()), rng.nextInt(ind.size()));
    }

    private int argmax(double[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++)
            if (arr[i] > arr[best]) best = i;
        return best;
    }

    private int indexOf(List<List<TestCase>> population, List<TestCase> ind) {
        for (int i = 0; i < population.size(); i++)
            if (population.get(i) == ind) return i;
        return 0;
    }

    @Override
    public String getName() { return "GeneticPrioritizer"; }
}