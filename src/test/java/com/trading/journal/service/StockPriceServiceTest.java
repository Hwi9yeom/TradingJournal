package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.trading.journal.entity.HistoricalPrice;
import com.trading.journal.exception.PriceDataException;
import com.trading.journal.repository.HistoricalPriceRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;
import yahoofinance.quotes.stock.StockQuote;

@ExtendWith(MockitoExtension.class)
class StockPriceServiceTest {

    @Mock private HistoricalPriceRepository historicalPriceRepository;

    @InjectMocks private StockPriceService stockPriceService;

    private Stock mockStock;
    private StockQuote mockQuote;

    @BeforeEach
    void setUp() {
        mockStock = mock(Stock.class);
        mockQuote = mock(StockQuote.class);
    }

    @Test
    @DisplayName("현재 주가 조회 - 성공")
    void getCurrentPrice_Success() throws IOException {
        // Given
        BigDecimal expectedPrice = new BigDecimal("150.50");
        when(mockQuote.getPrice()).thenReturn(expectedPrice);
        when(mockStock.getQuote()).thenReturn(mockQuote);

        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock.when(() -> YahooFinance.get("AAPL")).thenReturn(mockStock);

            // When
            BigDecimal result = stockPriceService.getCurrentPrice("AAPL");

            // Then
            assertThat(result).isEqualByComparingTo(expectedPrice);
        }
    }

    @Test
    @DisplayName("현재 주가 조회 - 실패")
    void getCurrentPrice_Failure() throws IOException {
        // Given
        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock
                    .when(() -> YahooFinance.get("INVALID"))
                    .thenThrow(new IOException("Failed to fetch"));

            // When & Then
            assertThatThrownBy(() -> stockPriceService.getCurrentPrice("INVALID"))
                    .isInstanceOf(PriceDataException.class)
                    .hasMessageContaining("현재가 조회 실패");
        }
    }

    @Test
    @DisplayName("주식 정보 조회 - 성공")
    void getStockInfo_Success() throws IOException {
        // Given
        when(mockStock.getName()).thenReturn("Apple Inc.");
        when(mockStock.getSymbol()).thenReturn("AAPL");
        when(mockStock.getStockExchange()).thenReturn("NASDAQ");

        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock.when(() -> YahooFinance.get("AAPL")).thenReturn(mockStock);

            // When
            Stock result = stockPriceService.getStockInfo("AAPL");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Apple Inc.");
            assertThat(result.getSymbol()).isEqualTo("AAPL");
            assertThat(result.getStockExchange()).isEqualTo("NASDAQ");
        }
    }

    @Test
    @DisplayName("전일 종가 조회 - 성공")
    void getPreviousClose_Success() throws IOException {
        // Given
        BigDecimal expectedPreviousClose = new BigDecimal("148.00");
        when(mockQuote.getPreviousClose()).thenReturn(expectedPreviousClose);
        when(mockStock.getQuote()).thenReturn(mockQuote);

        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock.when(() -> YahooFinance.get("AAPL")).thenReturn(mockStock);

            // When
            BigDecimal result = stockPriceService.getPreviousClose("AAPL");

            // Then
            assertThat(result).isEqualByComparingTo(expectedPreviousClose);
        }
    }

    @Test
    @DisplayName("과거 시세 조회 - 성공")
    void getHistoricalQuotes_Success() throws IOException {
        // Given
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        HistoricalQuote quote1 = mock(HistoricalQuote.class);
        HistoricalQuote quote2 = mock(HistoricalQuote.class);
        List<HistoricalQuote> expectedQuotes = Arrays.asList(quote1, quote2);

        when(historicalPriceRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                        eq("AAPL"), any(), any()))
                .thenReturn(Collections.emptyList());

        when(mockStock.getHistory(any(Calendar.class), any(Calendar.class), eq(Interval.DAILY)))
                .thenReturn(expectedQuotes);

        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock.when(() -> YahooFinance.get("AAPL")).thenReturn(mockStock);

            // When
            List<HistoricalQuote> result = stockPriceService.getHistoricalQuotes("AAPL", from, to);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(expectedQuotes);
        }
    }

    @Test
    @DisplayName("과거 시세 조회 - 실패")
    void getHistoricalQuotes_Failure() throws IOException {
        // Given
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

        when(historicalPriceRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                        eq("INVALID"), any(), any()))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<YahooFinance> yahooFinanceMock = mockStatic(YahooFinance.class)) {
            yahooFinanceMock
                    .when(() -> YahooFinance.get("INVALID"))
                    .thenThrow(new IOException("Failed to fetch"));

            // When & Then
            assertThatThrownBy(() -> stockPriceService.getHistoricalQuotes("INVALID", from, to))
                    .isInstanceOf(PriceDataException.class)
                    .hasMessageContaining("과거 가격 데이터 조회 실패");
        }
    }

    @Test
    @DisplayName("과거 시세 조회 - 캐시 히트 (80% 이상 커버리지)")
    void getHistoricalQuotes_CacheHit() {
        // Given - 5일 범위 조회 (주말 제외 시 약 3.5일 예상, 4개 데이터면 100% 이상 커버리지)
        LocalDate from = LocalDate.now().minusDays(5);
        LocalDate to = LocalDate.now();

        List<HistoricalPrice> cachedPrices =
                Arrays.asList(
                        HistoricalPrice.builder()
                                .symbol("AAPL")
                                .priceDate(LocalDate.now().minusDays(4))
                                .closePrice(new BigDecimal("150.00"))
                                .build(),
                        HistoricalPrice.builder()
                                .symbol("AAPL")
                                .priceDate(LocalDate.now().minusDays(3))
                                .closePrice(new BigDecimal("151.00"))
                                .build(),
                        HistoricalPrice.builder()
                                .symbol("AAPL")
                                .priceDate(LocalDate.now().minusDays(2))
                                .closePrice(new BigDecimal("152.00"))
                                .build(),
                        HistoricalPrice.builder()
                                .symbol("AAPL")
                                .priceDate(LocalDate.now().minusDays(1))
                                .closePrice(new BigDecimal("153.00"))
                                .build());

        when(historicalPriceRepository.findBySymbolAndPriceDateBetweenOrderByPriceDateAsc(
                        eq("AAPL"), any(), any()))
                .thenReturn(cachedPrices);

        // When
        List<HistoricalQuote> result = stockPriceService.getHistoricalQuotes("AAPL", from, to);

        // Then - Yahoo Finance API 호출 없이 캐시된 데이터 반환
        assertThat(result).hasSize(4);
        assertThat(result.get(0).getClose()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.get(3).getClose()).isEqualByComparingTo(new BigDecimal("153.00"));
    }
}
