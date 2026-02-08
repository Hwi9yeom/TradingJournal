package com.trading.journal.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    // 캐시 이름 상수
    private static final String CACHE_PORTFOLIO = "portfolio";
    private static final String CACHE_STOCK_PRICE = "stockPrice";
    private static final String CACHE_STOCK_INFO = "stockInfo";
    private static final String CACHE_PORTFOLIO_ANALYSIS = "portfolioAnalysis";
    private static final String CACHE_ANALYSIS = "analysis";
    private static final String CACHE_DIVIDEND = "dividend";
    private static final String CACHE_DISCLOSURE = "disclosure";
    private static final String CACHE_HISTORICAL_QUOTES = "historicalQuotes";
    private static final String CACHE_BACKTEST_COMPARISON = "backtest_comparison";
    private static final String CACHE_BACKTEST_RESULT = "backtest_result";
    private static final String CACHE_TRADING_STATS = "tradingStats";
    private static final String CACHE_GOALS = "goals";
    private static final String CACHE_TRADING_PSYCHOLOGY = "tradingPsychology";
    private static final String CACHE_TILT_STATUS = "tiltStatus";
    private static final String CACHE_PSYCHOLOGICAL_SCORE = "psychologicalScore";
    private static final String CACHE_COMPOSITE_DASHBOARD = "composite_dashboard";
    private static final String CACHE_BENCHMARK_COMPARISON = "benchmarkComparison";
    private static final String CACHE_REBALANCING = "rebalancing";
    private static final String CACHE_SECTOR_ANALYSIS = "sectorAnalysis";
    private static final String CACHE_RISK = "risk";
    private static final String CACHE_RISK_METRICS = "riskMetrics";
    private static final String CACHE_TREEMAP = "treemap";

    // TTL 상수
    private static final Duration TTL_REALTIME = Duration.ofMinutes(1);
    private static final Duration TTL_SHORT = Duration.ofMinutes(5);
    private static final Duration TTL_DEFAULT = Duration.ofMinutes(10);
    private static final Duration TTL_ANALYSIS = Duration.ofMinutes(30);
    private static final Duration TTL_HISTORICAL = Duration.ofHours(24);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000}")
    private int timeout;

    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisHost, redisPort);
        factory.setTimeout(timeout);
        return factory;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());

        // JSON 직렬화 설정
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key 직렬화
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 직렬화
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /** Redis가 비활성화되었을 때 사용되는 기본 CacheManager */
    @Bean("cacheManager")
    @Primary
    @ConditionalOnProperty(
            name = "spring.data.redis.enabled",
            havingValue = "false",
            matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        log.info("Configuring Simple (In-Memory) Cache Manager");
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(
                Arrays.asList(
                        new ConcurrentMapCache(CACHE_PORTFOLIO),
                        new ConcurrentMapCache(CACHE_STOCK_PRICE),
                        new ConcurrentMapCache(CACHE_STOCK_INFO),
                        new ConcurrentMapCache(CACHE_PORTFOLIO_ANALYSIS),
                        new ConcurrentMapCache(CACHE_ANALYSIS),
                        new ConcurrentMapCache(CACHE_DIVIDEND),
                        new ConcurrentMapCache(CACHE_DISCLOSURE),
                        new ConcurrentMapCache(CACHE_HISTORICAL_QUOTES),
                        new ConcurrentMapCache(CACHE_BACKTEST_COMPARISON),
                        new ConcurrentMapCache(CACHE_BACKTEST_RESULT),
                        new ConcurrentMapCache(CACHE_TRADING_STATS),
                        new ConcurrentMapCache(CACHE_GOALS),
                        new ConcurrentMapCache(CACHE_TRADING_PSYCHOLOGY),
                        new ConcurrentMapCache(CACHE_TILT_STATUS),
                        new ConcurrentMapCache(CACHE_PSYCHOLOGICAL_SCORE),
                        new ConcurrentMapCache(CACHE_COMPOSITE_DASHBOARD),
                        new ConcurrentMapCache(CACHE_BENCHMARK_COMPARISON),
                        new ConcurrentMapCache(CACHE_REBALANCING),
                        new ConcurrentMapCache(CACHE_SECTOR_ANALYSIS),
                        new ConcurrentMapCache(CACHE_RISK),
                        new ConcurrentMapCache(CACHE_RISK_METRICS),
                        new ConcurrentMapCache(CACHE_TREEMAP)));
        return cacheManager;
    }

    @Bean("cacheManager")
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("Configuring Redis Cache Manager");

        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(TTL_DEFAULT)
                        .serializeKeysWith(
                                org.springframework.data.redis.serializer.RedisSerializationContext
                                        .SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                org.springframework.data.redis.serializer.RedisSerializationContext
                                        .SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer()));

        // 캐시별 커스텀 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 포트폴리오 데이터 - 5분 캐시
        cacheConfigurations.put(CACHE_PORTFOLIO, defaultConfig.entryTtl(TTL_SHORT));
        cacheConfigurations.put(CACHE_PORTFOLIO_ANALYSIS, defaultConfig.entryTtl(TTL_SHORT));

        // 주식 가격 데이터 - 1분 캐시 (실시간성이 중요)
        cacheConfigurations.put(CACHE_STOCK_PRICE, defaultConfig.entryTtl(TTL_REALTIME));
        cacheConfigurations.put(CACHE_STOCK_INFO, defaultConfig.entryTtl(TTL_DEFAULT));

        // 거래 분석 데이터 - 30분 캐시
        cacheConfigurations.put(CACHE_ANALYSIS, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 배당금 데이터 - 10분 캐시
        cacheConfigurations.put(CACHE_DIVIDEND, defaultConfig.entryTtl(TTL_DEFAULT));

        // 공시 정보 - 30분 캐시
        cacheConfigurations.put(CACHE_DISCLOSURE, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 과거 가격 데이터 - 24시간 캐시 (과거 데이터는 변경되지 않음)
        cacheConfigurations.put(CACHE_HISTORICAL_QUOTES, defaultConfig.entryTtl(TTL_HISTORICAL));

        // 백테스트 비교 결과 - 10분 캐시
        cacheConfigurations.put(CACHE_BACKTEST_COMPARISON, defaultConfig.entryTtl(TTL_DEFAULT));

        // 백테스트 결과 - 5분 캐시
        cacheConfigurations.put(CACHE_BACKTEST_RESULT, defaultConfig.entryTtl(TTL_SHORT));

        // 거래 통계 - 30분 캐시
        cacheConfigurations.put(CACHE_TRADING_STATS, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 목표 - 10분 캐시
        cacheConfigurations.put(CACHE_GOALS, defaultConfig.entryTtl(TTL_DEFAULT));

        // 트레이딩 심리 분석 - 30분 캐시
        cacheConfigurations.put(CACHE_TRADING_PSYCHOLOGY, defaultConfig.entryTtl(TTL_ANALYSIS));
        cacheConfigurations.put(CACHE_TILT_STATUS, defaultConfig.entryTtl(TTL_DEFAULT));
        cacheConfigurations.put(CACHE_PSYCHOLOGICAL_SCORE, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 복합 대시보드 - 5분 캐시
        cacheConfigurations.put(CACHE_COMPOSITE_DASHBOARD, defaultConfig.entryTtl(TTL_SHORT));

        // 벤치마크 비교 - 30분 캐시
        cacheConfigurations.put(CACHE_BENCHMARK_COMPARISON, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 리밸런싱 - 10분 캐시
        cacheConfigurations.put(CACHE_REBALANCING, defaultConfig.entryTtl(TTL_DEFAULT));

        // 섹터 분석 - 30분 캐시
        cacheConfigurations.put(CACHE_SECTOR_ANALYSIS, defaultConfig.entryTtl(TTL_ANALYSIS));

        // 리스크 대시보드 - 10분 캐시
        cacheConfigurations.put(CACHE_RISK, defaultConfig.entryTtl(TTL_DEFAULT));
        cacheConfigurations.put(CACHE_RISK_METRICS, defaultConfig.entryTtl(TTL_DEFAULT));

        // 트리맵 - 5분 캐시
        cacheConfigurations.put(CACHE_TREEMAP, defaultConfig.entryTtl(TTL_SHORT));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
