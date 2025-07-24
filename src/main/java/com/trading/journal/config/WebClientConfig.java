package com.trading.journal.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.xml.Jaxb2XmlDecoder;
import org.springframework.http.codec.xml.Jaxb2XmlEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableCaching
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {
        // 대용량 응답 처리를 위한 버퍼 크기 증가
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        
        return WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
    
    @Bean
    public WebClient dartWebClient() {
        return WebClient.builder()
                .baseUrl("https://opendart.fss.or.kr/api")
                .defaultHeader("Accept", "application/json")
                .build();
    }
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("stockInfo", "stockPrice", "portfolioAnalysis");
    }
}