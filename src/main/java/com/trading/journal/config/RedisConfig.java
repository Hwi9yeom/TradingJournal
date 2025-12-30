package com.trading.journal.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.util.Arrays;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

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
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
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

    /**
     * Redis가 비활성화되었을 때 사용되는 기본 CacheManager
     */
    @Bean("cacheManager")
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager simpleCacheManager() {
        log.info("Configuring Simple (In-Memory) Cache Manager");
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("portfolio"),
                new ConcurrentMapCache("stockPrice"),
                new ConcurrentMapCache("stockInfo"),
                new ConcurrentMapCache("portfolioAnalysis"),
                new ConcurrentMapCache("analysis"),
                new ConcurrentMapCache("dividend"),
                new ConcurrentMapCache("disclosure")
        ));
        return cacheManager;
    }

    @Bean("cacheManager")
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        log.info("Configuring Redis Cache Manager");
        
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 기본 TTL 10분
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // 캐시별 커스텀 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 포트폴리오 데이터 - 5분 캐시
        cacheConfigurations.put("portfolio", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("portfolioAnalysis", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 주식 가격 데이터 - 1분 캐시 (실시간성이 중요)
        cacheConfigurations.put("stockPrice", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigurations.put("stockInfo", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 거래 분석 데이터 - 30분 캐시
        cacheConfigurations.put("analysis", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 배당금 데이터 - 10분 캐시
        cacheConfigurations.put("dividend", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 공시 정보 - 30분 캐시
        cacheConfigurations.put("disclosure", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}