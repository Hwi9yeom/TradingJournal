package com.trading.journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.entity.EconomicEvent;
import com.trading.journal.entity.EconomicEventType;
import com.trading.journal.entity.EventImportance;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Finnhub API 연동 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinnhubApiService {

    private final WebClient finnhubWebClient;
    private final ObjectMapper objectMapper;

    @Value("${finnhub.api.key:}")
    private String apiKey;

    private static final String SOURCE = "FINNHUB";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 경제 캘린더 조회
     *
     * @param from 시작일
     * @param to 종료일
     * @return 경제 이벤트 목록
     */
    public Mono<List<EconomicEvent>> getEconomicCalendar(LocalDate from, LocalDate to) {
        return finnhubWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/calendar/economic")
                                        .queryParam("token", apiKey)
                                        .queryParam("from", from.format(DATE_FORMATTER))
                                        .queryParam("to", to.format(DATE_FORMATTER))
                                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseEconomicCalendar)
                .doOnError(error -> log.error("Finnhub 경제 캘린더 조회 실패: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
    }

    /**
     * 실적발표 캘린더 조회
     *
     * @param from 시작일
     * @param to 종료일
     * @return 실적발표 이벤트 목록
     */
    public Mono<List<EconomicEvent>> getEarningsCalendar(LocalDate from, LocalDate to) {
        return finnhubWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/calendar/earnings")
                                        .queryParam("token", apiKey)
                                        .queryParam("from", from.format(DATE_FORMATTER))
                                        .queryParam("to", to.format(DATE_FORMATTER))
                                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseEarningsCalendar)
                .doOnError(error -> log.error("Finnhub 실적 캘린더 조회 실패: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
    }

    /**
     * 특정 종목 실적발표 조회
     *
     * @param symbol 종목 심볼
     * @return 실적발표 이벤트 목록
     */
    public Mono<List<EconomicEvent>> getEarningsBySymbol(String symbol) {
        return finnhubWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/stock/earnings")
                                        .queryParam("token", apiKey)
                                        .queryParam("symbol", symbol)
                                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseSymbolEarnings(response, symbol))
                .doOnError(
                        error -> log.error("Finnhub {} 실적 조회 실패: {}", symbol, error.getMessage()))
                .onErrorReturn(new ArrayList<>());
    }

    /**
     * IPO 캘린더 조회
     *
     * @param from 시작일
     * @param to 종료일
     * @return IPO 이벤트 목록
     */
    public Mono<List<EconomicEvent>> getIpoCalendar(LocalDate from, LocalDate to) {
        return finnhubWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/calendar/ipo")
                                        .queryParam("token", apiKey)
                                        .queryParam("from", from.format(DATE_FORMATTER))
                                        .queryParam("to", to.format(DATE_FORMATTER))
                                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseIpoCalendar)
                .doOnError(error -> log.error("Finnhub IPO 캘린더 조회 실패: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
    }

    /**
     * API 상태 확인
     *
     * @return API 연결 가능 여부
     */
    public Mono<Boolean> healthCheck() {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.just(false);
        }

        return finnhubWebClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/stock/symbol")
                                        .queryParam("token", apiKey)
                                        .queryParam("exchange", "US")
                                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> true)
                .onErrorReturn(false);
    }

    private List<EconomicEvent> parseEconomicCalendar(String jsonResponse) {
        List<EconomicEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode economicCalendar = root.path("economicCalendar");

            if (economicCalendar.isArray()) {
                for (JsonNode item : economicCalendar) {
                    EconomicEvent event = parseEconomicEvent(item);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }
        } catch (Exception e) {
            log.error("경제 캘린더 파싱 실패: {}", e.getMessage());
        }

        return events;
    }

    private EconomicEvent parseEconomicEvent(JsonNode item) {
        try {
            String timeStr = item.path("time").asText();
            LocalDateTime eventTime = parseEventTime(timeStr);

            String country = item.path("country").asText("US");
            String eventName = item.path("event").asText();
            String impact = item.path("impact").asText("low");

            return EconomicEvent.builder()
                    .eventTime(eventTime)
                    .country(country)
                    .eventName(eventName)
                    .eventType(determineEventType(eventName))
                    .importance(mapImpactToImportance(impact))
                    .actual(getTextOrNull(item, "actual"))
                    .forecast(getTextOrNull(item, "estimate"))
                    .previous(getTextOrNull(item, "prev"))
                    .unit(getTextOrNull(item, "unit"))
                    .currency(mapCountryToCurrency(country))
                    .externalId(generateExternalId(eventTime, eventName, country))
                    .source(SOURCE)
                    .build();
        } catch (Exception e) {
            log.warn("경제 이벤트 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private List<EconomicEvent> parseEarningsCalendar(String jsonResponse) {
        List<EconomicEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode earningsCalendar = root.path("earningsCalendar");

            if (earningsCalendar.isArray()) {
                for (JsonNode item : earningsCalendar) {
                    EconomicEvent event = parseEarningsEvent(item);
                    if (event != null) {
                        events.add(event);
                    }
                }
            }
        } catch (Exception e) {
            log.error("실적 캘린더 파싱 실패: {}", e.getMessage());
        }

        return events;
    }

    private EconomicEvent parseEarningsEvent(JsonNode item) {
        try {
            String dateStr = item.path("date").asText();
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            LocalDateTime eventTime = date.atTime(16, 0); // 기본 장 마감 후

            String symbol = item.path("symbol").asText();
            Double epsEstimate = getDoubleOrNull(item, "epsEstimate");
            Double epsActual = getDoubleOrNull(item, "epsActual");
            Double revenueEstimate = getDoubleOrNull(item, "revenueEstimate");
            Double revenueActual = getDoubleOrNull(item, "revenueActual");

            // 발표 시점 정보가 있으면 시간 조정
            String hour = item.path("hour").asText("");
            if ("bmo".equalsIgnoreCase(hour)) {
                eventTime = date.atTime(8, 0); // 장 시작 전
            } else if ("amc".equalsIgnoreCase(hour)) {
                eventTime = date.atTime(16, 30); // 장 마감 후
            }

            return EconomicEvent.builder()
                    .eventTime(eventTime)
                    .country("US")
                    .eventName(symbol + " Earnings")
                    .eventType(EconomicEventType.EARNINGS)
                    .importance(EventImportance.MEDIUM)
                    .symbol(symbol)
                    .epsEstimate(epsEstimate)
                    .epsActual(epsActual)
                    .revenueEstimate(revenueEstimate)
                    .revenueActual(revenueActual)
                    .currency("USD")
                    .externalId(generateExternalId(eventTime, symbol + "_EARNINGS", "US"))
                    .source(SOURCE)
                    .build();
        } catch (Exception e) {
            log.warn("실적 이벤트 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private List<EconomicEvent> parseSymbolEarnings(String jsonResponse, String symbol) {
        List<EconomicEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            if (root.isArray()) {
                for (JsonNode item : root) {
                    String period = item.path("period").asText();
                    LocalDate date = LocalDate.parse(period, DATE_FORMATTER);
                    LocalDateTime eventTime = date.atTime(16, 0);

                    Double epsActual = getDoubleOrNull(item, "actual");
                    Double epsEstimate = getDoubleOrNull(item, "estimate");

                    EconomicEvent event =
                            EconomicEvent.builder()
                                    .eventTime(eventTime)
                                    .country("US")
                                    .eventName(
                                            symbol + " Earnings Q" + item.path("quarter").asText())
                                    .eventType(EconomicEventType.EARNINGS)
                                    .importance(EventImportance.MEDIUM)
                                    .symbol(symbol)
                                    .epsEstimate(epsEstimate)
                                    .epsActual(epsActual)
                                    .currency("USD")
                                    .externalId(
                                            generateExternalId(
                                                    eventTime, symbol + "_Q" + period, "US"))
                                    .source(SOURCE)
                                    .build();

                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.error("{} 실적 파싱 실패: {}", symbol, e.getMessage());
        }

        return events;
    }

    private List<EconomicEvent> parseIpoCalendar(String jsonResponse) {
        List<EconomicEvent> events = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode ipoCalendar = root.path("ipoCalendar");

            if (ipoCalendar.isArray()) {
                for (JsonNode item : ipoCalendar) {
                    String dateStr = item.path("date").asText();
                    LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                    LocalDateTime eventTime = date.atTime(9, 30);

                    String symbol = item.path("symbol").asText("");
                    String name = item.path("name").asText("");

                    EconomicEvent event =
                            EconomicEvent.builder()
                                    .eventTime(eventTime)
                                    .country("US")
                                    .eventName(
                                            name.isEmpty()
                                                    ? symbol + " IPO"
                                                    : name + " (" + symbol + ") IPO")
                                    .eventType(EconomicEventType.IPO)
                                    .importance(EventImportance.MEDIUM)
                                    .symbol(symbol)
                                    .currency("USD")
                                    .externalId(
                                            generateExternalId(eventTime, symbol + "_IPO", "US"))
                                    .source(SOURCE)
                                    .build();

                    events.add(event);
                }
            }
        } catch (Exception e) {
            log.error("IPO 캘린더 파싱 실패: {}", e.getMessage());
        }

        return events;
    }

    private LocalDateTime parseEventTime(String timeStr) {
        try {
            // Unix timestamp 형식 처리
            if (timeStr.matches("\\d+")) {
                long timestamp = Long.parseLong(timeStr);
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of("UTC"));
            }
            // ISO 형식 또는 기타
            return LocalDateTime.parse(timeStr);
        } catch (Exception e) {
            // yyyy-MM-dd 형식 시도
            try {
                return LocalDate.parse(timeStr, DATE_FORMATTER).atStartOfDay();
            } catch (Exception ex) {
                return LocalDateTime.now();
            }
        }
    }

    private EventImportance mapImpactToImportance(String impact) {
        if (impact == null) {
            return EventImportance.LOW;
        }
        return switch (impact.toLowerCase()) {
            case "high", "3" -> EventImportance.HIGH;
            case "medium", "2" -> EventImportance.MEDIUM;
            default -> EventImportance.LOW;
        };
    }

    private EconomicEventType determineEventType(String eventName) {
        String lowerName = eventName.toLowerCase();

        if (lowerName.contains("fed")
                || lowerName.contains("fomc")
                || lowerName.contains("rate decision")
                || lowerName.contains("central bank")
                || lowerName.contains("ecb")
                || lowerName.contains("boj")) {
            return EconomicEventType.CENTRAL_BANK;
        }

        return EconomicEventType.ECONOMIC_INDICATOR;
    }

    private String mapCountryToCurrency(String country) {
        return switch (country.toUpperCase()) {
            case "US" -> "USD";
            case "EU", "DE", "FR", "IT", "ES" -> "EUR";
            case "GB", "UK" -> "GBP";
            case "JP" -> "JPY";
            case "CN" -> "CNY";
            case "KR" -> "KRW";
            case "AU" -> "AUD";
            case "CA" -> "CAD";
            case "CH" -> "CHF";
            default -> "USD";
        };
    }

    private String generateExternalId(LocalDateTime eventTime, String eventName, String country) {
        String dateStr = eventTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return SOURCE + "_" + country + "_" + dateStr + "_" + eventName.hashCode();
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        String text = fieldNode.asText();
        return text.isEmpty() ? null : text;
    }

    private Double getDoubleOrNull(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asDouble();
    }
}
