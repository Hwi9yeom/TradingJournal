package com.trading.journal.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 성능 측정을 위한 어노테이션. 이 어노테이션이 붙은 메서드는 실행 시간과 함께 별도 로깅됩니다.
 *
 * <p>기본적으로 모든 서비스/컨트롤러/리포지토리 메서드는 PerformanceLoggingAspect에서 자동 로깅되지만, 이 어노테이션을 사용하면 추가 설명과 함께 더
 * 명확하게 로깅할 수 있습니다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasurePerformance {
    /** 작업 설명 (로그에 표시됨) */
    String value() default "";

    /** 느린 실행 임계값 (밀리초). 이 값 초과 시 WARN 레벨로 로깅 */
    long slowThresholdMs() default 1000;
}
