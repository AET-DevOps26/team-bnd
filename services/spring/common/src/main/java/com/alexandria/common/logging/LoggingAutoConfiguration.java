package com.alexandria.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

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
