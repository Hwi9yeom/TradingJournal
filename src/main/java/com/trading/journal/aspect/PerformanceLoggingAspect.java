package com.trading.journal.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class PerformanceLoggingAspect {

    private static final Logger performanceLogger = LoggerFactory.getLogger("PERFORMANCE");
    private static final long SLOW_THRESHOLD_MS = 1000; // 1초 이상 걸리는 메서드는 slow로 분류

    @Around("execution(* com.trading.journal.service.*.*(..))")
    public Object logServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "SERVICE");
    }

    @Around("execution(* com.trading.journal.controller.*.*(..))")
    public Object logControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "CONTROLLER");
    }

    @Around("execution(* com.trading.journal.repository.*.*(..))")
    public Object logRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logPerformance(joinPoint, "REPOSITORY");
    }

    private Object logPerformance(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // 성능 로그 기록
            if (exception != null) {
                performanceLogger.error(
                        "[{}] {} FAILED - {}ms - Exception: {}",
                        layer,
                        fullMethodName,
                        executionTime,
                        exception.getMessage());
            } else if (executionTime > SLOW_THRESHOLD_MS) {
                performanceLogger.warn("[{}] {} SLOW - {}ms", layer, fullMethodName, executionTime);
            } else {
                performanceLogger.info(
                        "[{}] {} SUCCESS - {}ms", layer, fullMethodName, executionTime);
            }

            // 메모리 사용량 로그 (디버그 모드에서만)
            if (log.isDebugEnabled()) {
                Runtime runtime = Runtime.getRuntime();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                performanceLogger.debug(
                        "[MEMORY] Total: {}MB, Used: {}MB, Free: {}MB",
                        totalMemory / 1024 / 1024,
                        usedMemory / 1024 / 1024,
                        freeMemory / 1024 / 1024);
            }
        }
    }
}
