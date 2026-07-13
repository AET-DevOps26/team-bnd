package com.alexandria.common.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class ApplicationInfoMetricsAutoConfiguration {

    @Bean
    public MeterBinder applicationInfoMetric(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        String application = environment.getProperty("spring.application.name", "unknown");
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null && build.getVersion() != null ? build.getVersion() : "unknown";
        return registry -> Gauge.builder("application_version", () -> 1).description("Running application build info; the value is always 1, the version is carried as a label").tag("application", application).tag("version", version).register(registry);
    }
}
