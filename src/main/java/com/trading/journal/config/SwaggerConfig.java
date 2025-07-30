package com.trading.journal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("투자 기록 서비스 API")
                        .description("개인 투자 기록을 관리하는 REST API입니다. 거래 내역, 포트폴리오, 배당금, 분석 기능을 제공합니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("투자 기록 서비스")
                                .email("support@trading-journal.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.trading-journal.com")
                                .description("운영 서버")
                ));
    }
}