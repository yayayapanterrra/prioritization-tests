package com.tcp.algorithm;

import com.tcp.fitness.GaFitnessFunction;
import com.tcp.model.TestCase;
import io.jenetics.*;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.Limits;
import io.jenetics.util.ISeq;

import io.jenetics.util.RandomRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
 *   coverage (0.0) + fault-proneness (0.8) + Jaccard diversity (0.2)
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

        ISeq<TestCase> alleles = ISeq.of(testPool);

        Engine<EnumGene<TestCase>, Double> engine = Engine
                .builder(
                        gt -> fitnessFunction.calculateFitness(toList(gt)),
                        PermutationChromosome.of(alleles)
                )
                .populationSize(populationSize)
                .survivorsSelector(new TournamentSelector<>(3))
                .offspringSelector(new TournamentSelector<>(3))
                .alterers(
                        new PartiallyMatchedCrossover<>(crossoverRate),
                        new SwapMutator<>(mutationRate)
                )
                .offspringFraction(1.0 - 1.0 / populationSize)
                .executor(Runnable::run)
                .build();

        Genotype<EnumGene<TestCase>> best = engine
                .stream(ISeq.of(engine.genotypeFactory().instances()
                        .limit(populationSize)
                        .collect(ISeq.toISeq())))
                .limit(Limits.byFixedGeneration(generations))
                .collect(EvolutionResult.toBestGenotype());

        return toList(best);

    }

    /**
     * Конвертирует генотип Jenetics в упорядоченный список TestCase.
     * PermutationChromosome хранит возможные значения генов как EnumGene<TestCase>.
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