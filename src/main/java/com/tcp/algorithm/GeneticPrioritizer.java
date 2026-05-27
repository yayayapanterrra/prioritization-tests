package com.tcp.algorithm;

import com.tcp.fitness.GaFitnessFunction;
import com.tcp.model.TestCase;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.Limits;
import io.jenetics.util.ISeq;

import java.util.ArrayList;
import java.util.List;

/**
 * Генетический алгоритм для приоритизации тестов на базе библиотеки Jenetics.
 *
 * Хромосома: PermutationChromosome<TestCase> — каждый индивид представляет
 * собой валидную перестановку тестов без дубликатов.
 *
 * Операторы (из Jenetics):
 *   - Селекция:  TournamentSelector (размер турнира задаётся параметром)
 *   - Кроссовер: PartiallyMatchedCrossover — аналог OX, сохраняет
 *                валидную перестановку
 *   - Мутация:   SwapMutator — swap двух случайных позиций
 *   - Элитизм:   встроенный механизм Jenetics (eliteCount через селектор)
 *
 * Фитнесс-функция: GaFitnessFunction с тремя нормализованными компонентами:
 *   coverage (0.6) + fault-proneness (0.4) + Jaccard diversity (0.0)
 *
 * Режим остановки: фиксированное число поколений (Limits.byFixedGeneration).
 */
public class GeneticPrioritizer implements Prioritizer {

    private final int populationSize;
    private final int generations;
    private final double crossoverRate;
    private final double mutationRate;
    private final GaFitnessFunction fitnessFunction;

    public GeneticPrioritizer(int populationSize, int generations,
                              double crossoverRate, double mutationRate,
                              GaFitnessFunction fitnessFunction) {
        this.populationSize = populationSize;
        this.generations    = generations;
        this.crossoverRate  = crossoverRate;
        this.mutationRate   = mutationRate;
        this.fitnessFunction = fitnessFunction;
    }

    @Override
    public List<TestCase> prioritize(List<TestCase> testPool) {
        if (testPool.isEmpty()) return new ArrayList<>();

        // ISeq — неизменяемая последовательность Jenetics, основа PermutationChromosome
        ISeq<TestCase> alleles = ISeq.of(testPool);

        // Фабрика генотипов: одна хромосома = перестановка всех тестов
        Engine<EnumGene<TestCase>, Double> engine = Engine
                .builder(
                        gt -> fitnessFunction.calculateFitness(toList(gt)),
                        PermutationChromosome.of(alleles)
                )
                .populationSize(populationSize)
                // Селекция выживших: турнир размером 3
                .survivorsSelector(new TournamentSelector<>(3))
                // Селекция родителей: турнир размером 3
                .offspringSelector(new TournamentSelector<>(3))
                .alterers(
                        // PMX - гарантирует валидную перестановку
                        new PartiallyMatchedCrossover<>(crossoverRate),
                        // Swap-мутация двух случайных позиций
                        new SwapMutator<>(mutationRate)
                )
                // Элитизм: 1 лучший индивид переходит без изменений
                .offspringFraction(1.0 - 1.0 / populationSize)
                .build();

        // Запуск эволюции на фиксированное число поколений
        Genotype<EnumGene<TestCase>> best = engine.stream()
                .limit(Limits.byFixedGeneration(generations))
                .collect(EvolutionResult.toBestGenotype());

        return toList(best);
    }

    /**
     * Конвертирует генотип Jenetics в упорядоченный список TestCase.
     * PermutationChromosome хранит аллели как EnumGene<TestCase>.
     */
    private List<TestCase> toList(Genotype<EnumGene<TestCase>> gt) {
        List<TestCase> result = new ArrayList<>();
        // Первая (и единственная) хромосома содержит всю перестановку
        for (EnumGene<TestCase> gene : gt.chromosome()) {
            result.add(gene.allele());
        }
        return result;
    }

    @Override
    public String getName() {
        return "GeneticPrioritizer";
    }
}