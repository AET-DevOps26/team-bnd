package com.alexandria.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WriteEndpointLoggingAspectTest {

    private final WriteEndpointLoggingAspect aspect = new WriteEndpointLoggingAspect();
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setup() {
        joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("createDocument");
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @Test
    void unit_common_returnsResultAndProceeds() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.logAroundWrite(joinPoint)).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void unit_common_rethrowsFailures() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.logAroundWrite(joinPoint)).isInstanceOf(IllegalStateException.class);
    }
}
