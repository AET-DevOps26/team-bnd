package com.alexandria.common.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthConfig {

    @Bean
    public HmacSigner internalHmacSigner(InternalAuthProperties props) {
        if (!props.hasSecret()) {
            throw new IllegalStateException(
                    "app.internal.shared-secret (INTERNAL_SHARED_SECRET) must be set for HMAC-authenticated /internal/** endpoints");
        }
        return new HmacSigner(props.sharedSecret(), props.clockSkewSeconds());
    }

    @Bean
    public HmacRequestSigningInterceptor internalHmacRequestSigningInterceptor(HmacSigner signer) {
        return new HmacRequestSigningInterceptor(signer);
    }

    @Bean
    public HmacInternalAuthFilter internalHmacAuthFilter(HmacSigner signer, ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        return new HmacInternalAuthFilter(signer, mapper);
    }

    // Filter beans are auto-registered by Spring Boot as servlet-level filters. That
    // would make the filter run twice (once by the container, once inside Spring
    // Security via addFilterBefore). Thus we install the filter explicitly in each
    // SecurityConfig and suppress the automatic registration here
    @Bean
    public FilterRegistrationBean<HmacInternalAuthFilter> internalHmacAuthFilterRegistration(HmacInternalAuthFilter filter) {
        FilterRegistrationBean<HmacInternalAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
