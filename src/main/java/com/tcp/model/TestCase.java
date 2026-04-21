package com.tcp.model;

import java.util.Set;

public class TestCase {
    private final String id;
    //множество методов, которые покрывает этот тест
    private final Set<String> coveredMethods;
    //оценка "рискованности" кода, который покрывает тест (0.0-1.0)
    private final double faultProneness;
    //время выполнения теста в мс
    private final double executionTimeMs;

    public TestCase(String id, Set<String> coveredMethods, double faultProneness, double executionTimeMs) {
        this.id = id;
        this.coveredMethods = coveredMethods;
        this.faultProneness = faultProneness;
        this.executionTimeMs = executionTimeMs;
    }

    public String getId() {
        return id;
    }

    public Set<String> getCoveredMethods() {
        return coveredMethods;
    }

    public double getFaultProneness() {
        return faultProneness;
    }

    public double getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        return "Test{" + id + "}";
    }

}
