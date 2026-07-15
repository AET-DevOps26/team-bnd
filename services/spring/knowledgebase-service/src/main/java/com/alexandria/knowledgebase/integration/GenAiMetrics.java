package com.alexandria.knowledgebase.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;

/**
 * Records latency and outcome for each GenAI client call.
 *
 * <p>Emits a {@code genai.client.call.duration} timer and a {@code genai.client.calls}
 * counter, both tagged with the operation name and a success/error outcome, so
 * per-operation error rate and latency are visible in Prometheus and Grafana.
 */
class GenAiMetrics {

    private final MeterRegistry registry;

    GenAiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Runs {@code call}, timing it and recording its outcome even if it throws.
     *
     * @return the value produced by {@code call}
     */
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
