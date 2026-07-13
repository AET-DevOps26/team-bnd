package com.alexandria.common.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the HMAC-based authentication that gates the /internal/** service-to-service
 * endpoints. The shared secret is injected via an environment variable.
 *
 * The clock-skew tolerance covers small drift between service pods, anything larger than
 * a few minutes is treated as a replay attempt and rejected.
 */
@ConfigurationProperties(prefix = "app.internal")
public record InternalAuthProperties(String sharedSecret, long clockSkewSeconds) {

    public InternalAuthProperties {
        if (clockSkewSeconds <= 0) {
            clockSkewSeconds = 300;
        }
    }

    public boolean hasSecret() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }
}
