package com.trading.journal.service;

import com.trading.journal.dto.DividendDto;
import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.dto.PortfolioSummaryDto;
import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Stock;
import com.trading.journal.repository.DividendRepository;
import com.trading.journal.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private StockRepository stockRepository;
    
    @Mock
    private PortfolioAnalysisService portfolioAnalysisService;

    @InjectMocks
    private DividendService dividendService;

    private Stock mockStock;
    private Dividend mockDividend;
    private DividendDto mockDividendDto;

    @BeforeEach
    void setUp() {
        mockStock = Stock.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .exchange("NASDAQ")
                .build();

        mockDividend = Dividend.builder()
                .id(1L)
                .stock(mockStock)
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.85"))
                .netAmount(new BigDecimal("21.15"))
                .memo("분기 배당")
                .build();

        mockDividendDto = DividendDto.builder()
                .stockId(1L)
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .memo("분기 배당")
                .build();
    }

    @Test
    @DisplayName("배당금 기록 생성 - 성공")
    void createDividend_Success() {
        // Given
        when(stockRepository.findById(1L)).thenReturn(Optional.of(mockStock));
        when(dividendRepository.save(any(Dividend.class))).thenReturn(mockDividend);

        // When
        DividendDto result = dividendService.createDividend(mockDividendDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        assertThat(result.getDividendPerShare()).isEqualTo(new BigDecimal("0.25"));
        assertThat(result.getQuantity()).isEqualTo(new BigDecimal("100"));
        
        verify(stockRepository).findById(1L);
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("배당금 기록 생성 - 종목 없음")
    void createDividend_StockNotFound() {
        // Given
        when(stockRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dividendService.createDividend(mockDividendDto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Stock not found");

        verify(stockRepository).findById(1L);
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("배당금 기록 수정")
    void updateDividend_Success() {
        // Given
        DividendDto updateDto = DividendDto.builder()
                .dividendPerShare(new BigDecimal("0.30"))
                .quantity(new BigDecimal("120"))
                .memo("수정된 배당")
                .build();

        when(dividendRepository.findById(1L)).thenReturn(Optional.of(mockDividend));
        when(dividendRepository.save(any(Dividend.class))).thenReturn(mockDividend);

        // When
        DividendDto result = dividendService.updateDividend(1L, updateDto);

        // Then
        assertThat(result).isNotNull();
        verify(dividendRepository).findById(1L);
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("배당금 기록 삭제")
    void deleteDividend() {
        // Given
        doNothing().when(dividendRepository).deleteById(1L);

        // When
        dividendService.deleteDividend(1L);

        // Then
        verify(dividendRepository).deleteById(1L);
    }

    @Test
    @DisplayName("배당금 조회")
    void getDividend_Success() {
        // Given
        when(dividendRepository.findById(1L)).thenReturn(Optional.of(mockDividend));

        // When
        DividendDto result = dividendService.getDividend(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        
        verify(dividendRepository).findById(1L);
    }

    @Test
    @DisplayName("배당금 조회 - 존재하지 않음")
    void getDividend_NotFound() {
        // Given
        when(dividendRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dividendService.getDividend(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Dividend not found");

        verify(dividendRepository).findById(1L);
    }

    @Test
    @DisplayName("종목별 배당금 조회")
    void getDividendsByStock() {
        // Given
        List<Dividend> dividends = Arrays.asList(mockDividend);
        when(dividendRepository.findByStockSymbol("AAPL")).thenReturn(dividends);

        // When
        List<DividendDto> result = dividendService.getDividendsByStock("AAPL");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        
        verify(dividendRepository).findByStockSymbol("AAPL");
    }

    @Test
    @DisplayName("기간별 배당금 조회")
    void getDividendsByPeriod() {
        // Given
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        List<Dividend> dividends = Arrays.asList(mockDividend);
        
        when(dividendRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(startDate, endDate))
                .thenReturn(dividends);

        // When
        List<DividendDto> result = dividendService.getDividendsByPeriod(startDate, endDate);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        
        verify(dividendRepository).findByPaymentDateBetweenOrderByPaymentDateDesc(startDate, endDate);
    }

    @Test
    @DisplayName("배당금 요약 정보 조회")
    void getDividendSummary() {
        // Given
        List<Dividend> allDividends = Arrays.asList(mockDividend);
        when(dividendRepository.findAll()).thenReturn(allDividends);
        when(dividendRepository.getTotalDividendsByPeriod(any(), any())).thenReturn(new BigDecimal("500.00"));
        when(dividendRepository.getTopDividendStocks(any(), any())).thenReturn(Arrays.asList());
        when(dividendRepository.getMonthlyDividends()).thenReturn(Arrays.asList());
        
        // PortfolioAnalysisService mock
        PortfolioSummaryDto portfolioSummary = PortfolioSummaryDto.builder()
                .totalCurrentValue(new BigDecimal("10000.00"))
                .build();
        when(portfolioAnalysisService.getPortfolioSummary()).thenReturn(portfolioSummary);

        // When
        DividendSummaryDto result = dividendService.getDividendSummary();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalDividends()).isEqualTo(new BigDecimal("21.15"));
        assertThat(result.getTotalTax()).isEqualTo(new BigDecimal("3.85"));
        
        verify(dividendRepository).findAll();
        verify(portfolioAnalysisService).getPortfolioSummary();
    }
}