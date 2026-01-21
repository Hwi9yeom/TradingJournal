package com.trading.journal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // 대용량 응답 처리를 위한 버퍼 크기 증가
        ExchangeStrategies exchangeStrategies =
                ExchangeStrategies.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(10 * 1024 * 1024)) // 10MB
                        .build();

        return WebClient.builder().exchangeStrategies(exchangeStrategies).build();
    }

    @Bean
    public WebClient dartWebClient() {
        return WebClient.builder()
                .baseUrl("https://opendart.fss.or.kr/api")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public WebClient finnhubWebClient() {
        return WebClient.builder()
                .baseUrl("https://finnhub.io/api/v1")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
