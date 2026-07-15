package com.alexandria.common.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs start, finish and failure around every write (POST/PUT/PATCH/DELETE) controller
 * method, so state-changing requests leave an audit trail without each controller having
 * to log by hand. Read endpoints are intentionally left out to keep the logs quiet.
 */
@Aspect
public class WriteEndpointLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(WriteEndpointLoggingAspect.class);

    @Pointcut("within(com.alexandria..*Controller)")
    void inController() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.PutMapping) || @annotation(org.springframework.web.bind.annotation.PatchMapping) || @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    void writeMapping() {
    }

    @Around("inController() && writeMapping()")
    public Object logAroundWrite(ProceedingJoinPoint joinPoint) throws Throwable {
        String endpoint = joinPoint.getSignature().getName();
        log.info("write endpoint {} started", endpoint);
        try {
            Object result = joinPoint.proceed();
            log.info("write endpoint {} finished", endpoint);
            return result;
        } catch (Throwable e) {
            log.warn("write endpoint {} failed: {}", endpoint, e.getMessage());
            throw e;
        }
    }
}
