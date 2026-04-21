package com.tcp.algorithm;

import com.tcp.model.TestCase;
import java.util.List;

/*
интерфейс для всех алгоритмов приоритизации
позволяет единообразно вызывать любые стратегии
 */
public interface Prioritizer {
    /**
     * основной метод
     * принимает неупорядоченный пул тестов и возвращает список, отсортированный по приоритету
     *
     * @param testPool  - исходный список тестов
     * @return упорядоченный список тестов
     */
    List<TestCase> prioritize(List<TestCase> testPool);

    /**
     * возвращает название алгоритма для логирования и отчетов
     * @return имя метода приоритизации
     */
    String getName();

}
