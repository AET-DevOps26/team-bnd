package com.alexandria.knowledgebase.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;

class GenAiMetrics {

    private final MeterRegistry registry;

    GenAiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    <T> T record(String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(registry.timer("genai.client.call.duration", "operation", operation, "outcome", outcome));
            registry.counter("genai.client.calls", "operation", operation, "outcome", outcome).increment();
        }
    }
}
