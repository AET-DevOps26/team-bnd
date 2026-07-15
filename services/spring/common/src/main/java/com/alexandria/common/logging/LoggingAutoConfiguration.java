package com.alexandria.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Wires the shared logging support (MDC request-id filter and the write-endpoint logging aspect)
 * into every service that puts the common module on its classpath. Registered as a Spring Boot
 * auto-configuration.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
public class LoggingAutoConfiguration {

    @Bean
    public MdcLoggingFilter mdcLoggingFilter() {
        return new MdcLoggingFilter();
    }

    @Bean
    public WriteEndpointLoggingAspect writeEndpointLoggingAspect() {
        return new WriteEndpointLoggingAspect();
    }
}
