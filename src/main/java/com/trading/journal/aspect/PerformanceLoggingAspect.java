package com.trading.journal.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 성능 로깅 AOP. 서비스, 컨트롤러, 리포지토리 레이어의 메서드 실행 시간을 로깅합니다. */
@Aspect
@Component
@Slf4j
public class PerformanceLoggingAspect {

    private static final Logger performanceLogger = LoggerFactory.getLogger("PERFORMANCE");
    private static final long SLOW_THRESHOLD_MS = 1000;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /** 서비스, 컨트롤러, 리포지토리 레이어의 모든 메서드를 대상으로 성능 로깅 */
    @Around(
            "execution(* com.trading.journal.service.*.*(..)) || "
                    + "execution(* com.trading.journal.controller.*.*(..)) || "
                    + "execution(* com.trading.journal.repository.*.*(..))")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String layer = determineLayer(joinPoint);
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;

        long startTime = System.currentTimeMillis();
        Throwable exception = null;

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            exception = e;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            logExecutionResult(layer, fullMethodName, executionTime, exception);
            logMemoryIfDebugEnabled();
        }
    }

    /** 패키지 이름에서 레이어 결정 */
    private String determineLayer(ProceedingJoinPoint joinPoint) {
        String packageName = joinPoint.getTarget().getClass().getPackageName();
        if (packageName.contains(".service")) return "SERVICE";
        if (packageName.contains(".controller")) return "CONTROLLER";
        if (packageName.contains(".repository")) return "REPOSITORY";
        return "UNKNOWN";
    }

    /** 실행 결과 로깅 */
    private void logExecutionResult(String layer, String method, long time, Throwable ex) {
        if (ex != null) {
            performanceLogger.error(
                    "[{}] {} FAILED - {}ms - Exception: {}", layer, method, time, ex.getMessage());
        } else if (time > SLOW_THRESHOLD_MS) {
            performanceLogger.warn("[{}] {} SLOW - {}ms", layer, method, time);
        } else {
            performanceLogger.info("[{}] {} SUCCESS - {}ms", layer, method, time);
        }
    }

    /** 메모리 사용량 로깅 (디버그 모드에서만) */
    private void logMemoryIfDebugEnabled() {
        if (performanceLogger.isDebugEnabled()) {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            performanceLogger.debug(
                    "[MEMORY] Total: {}MB, Used: {}MB, Free: {}MB",
                    totalMemory / BYTES_PER_MB,
                    usedMemory / BYTES_PER_MB,
                    freeMemory / BYTES_PER_MB);
        }
    }
}
