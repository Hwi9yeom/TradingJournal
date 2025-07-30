package com.trading.journal.service;

import com.trading.journal.dto.DividendDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @Mock
    private DividendRepository dividendRepository;

    @Mock
    private StockRepository stockRepository;

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
                .taxAmount(new BigDecimal("3.75"))
                .netAmount(new BigDecimal("21.25"))
                .memo("분기 배당")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mockDividendDto = DividendDto.builder()
                .stockSymbol("AAPL")
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.25"))
                .quantity(new BigDecimal("100"))
                .totalAmount(new BigDecimal("25.00"))
                .taxAmount(new BigDecimal("3.75"))
                .netAmount(new BigDecimal("21.25"))
                .memo("분기 배당")
                .build();
    }

    @Test
    @DisplayName("배당금 기록 생성 - 기존 종목")
    void createDividend_ExistingStock() {
        // Given
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Optional.of(mockStock));
        when(dividendRepository.save(any(Dividend.class))).thenReturn(mockDividend);

        // When
        DividendDto result = dividendService.createDividend(mockDividendDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        assertThat(result.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("0.25"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.getNetAmount()).isEqualByComparingTo(new BigDecimal("21.25"));

        verify(stockRepository).findBySymbol("AAPL");
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("배당금 기록 생성 - 새로운 종목")
    void createDividend_NewStock() {
        // Given
        DividendDto newStockDividendDto = DividendDto.builder()
                .stockSymbol("MSFT")
                .exDividendDate(LocalDate.now().minusDays(30))
                .paymentDate(LocalDate.now())
                .dividendPerShare(new BigDecimal("0.68"))
                .quantity(new BigDecimal("50"))
                .totalAmount(new BigDecimal("34.00"))
                .taxAmount(new BigDecimal("5.10"))
                .netAmount(new BigDecimal("28.90"))
                .build();

        Stock newStock = Stock.builder()
                .id(2L)
                .symbol("MSFT")
                .name("Microsoft Corporation")
                .build();

        Dividend newDividend = Dividend.builder()
                .id(2L)
                .stock(newStock)
                .exDividendDate(newStockDividendDto.getExDividendDate())
                .paymentDate(newStockDividendDto.getPaymentDate())
                .dividendPerShare(newStockDividendDto.getDividendPerShare())
                .quantity(newStockDividendDto.getQuantity())
                .totalAmount(newStockDividendDto.getTotalAmount())
                .taxAmount(newStockDividendDto.getTaxAmount())
                .netAmount(newStockDividendDto.getNetAmount())
                .build();

        when(stockRepository.findBySymbol("MSFT")).thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenReturn(newStock);
        when(dividendRepository.save(any(Dividend.class))).thenReturn(newDividend);

        // When
        DividendDto result = dividendService.createDividend(newStockDividendDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("MSFT");
        assertThat(result.getDividendPerShare()).isEqualByComparingTo(new BigDecimal("0.68"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("34.00"));

        verify(stockRepository).findBySymbol("MSFT");
        verify(stockRepository).save(any(Stock.class));
        verify(dividendRepository).save(any(Dividend.class));
    }

    @Test
    @DisplayName("모든 배당금 조회 - 페이지네이션")
    void getAllDividends_WithPagination() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<Dividend> dividends = Arrays.asList(mockDividend);
        Page<Dividend> dividendPage = new PageImpl<>(dividends, pageable, 1);
        
        when(dividendRepository.findAllWithStock(pageable)).thenReturn(dividendPage);

        // When
        Page<DividendDto> result = dividendService.getAllDividends(pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(dividendRepository).findAllWithStock(pageable);
    }

    @Test
    @DisplayName("종목별 배당금 조회")
    void getDividendsBySymbol() {
        // Given
        List<Dividend> dividends = Arrays.asList(mockDividend);
        when(dividendRepository.findByStockSymbolOrderByPaymentDateDesc("AAPL")).thenReturn(dividends);

        // When
        List<DividendDto> result = dividendService.getDividendsBySymbol("AAPL");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(dividendRepository).findByStockSymbolOrderByPaymentDateDesc("AAPL");
    }

    @Test
    @DisplayName("연도별 배당금 조회")
    void getDividendsByYear() {
        // Given
        int year = 2024;
        List<Dividend> dividends = Arrays.asList(mockDividend);
        when(dividendRepository.findByPaymentYear(year)).thenReturn(dividends);

        // When
        List<DividendDto> result = dividendService.getDividendsByYear(year);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStockSymbol()).isEqualTo("AAPL");
        verify(dividendRepository).findByPaymentYear(year);
    }

    @Test
    @DisplayName("배당금 ID로 조회 - 성공")
    void getDividendById_Found() {
        // Given
        when(dividendRepository.findById(1L)).thenReturn(Optional.of(mockDividend));

        // When
        DividendDto result = dividendService.getDividendById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStockSymbol()).isEqualTo("AAPL");
        verify(dividendRepository).findById(1L);
    }

    @Test
    @DisplayName("배당금 ID로 조회 - 실패")
    void getDividendById_NotFound() {
        // Given
        when(dividendRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> dividendService.getDividendById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Dividend not found with id: 999");
    }

    @Test
    @DisplayName("배당금 수정")
    void updateDividend() {
        // Given
        DividendDto updateDto = DividendDto.builder()
                .dividendPerShare(new BigDecimal("0.30"))
                .quantity(new BigDecimal("120"))
                .totalAmount(new BigDecimal("36.00"))
                .taxAmount(new BigDecimal("5.40"))
                .netAmount(new BigDecimal("30.60"))
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
    @DisplayName("배당금 삭제")
    void deleteDividend() {
        // Given
        when(dividendRepository.findById(1L)).thenReturn(Optional.of(mockDividend));

        // When
        dividendService.deleteDividend(1L);

        // Then
        verify(dividendRepository).findById(1L);
        verify(dividendRepository).delete(mockDividend);
    }

    @Test
    @DisplayName("연간 배당금 통계")
    void getYearlyDividendStats() {
        // Given
        int year = 2024;
        BigDecimal totalGross = new BigDecimal("1000.00");
        BigDecimal totalTax = new BigDecimal("150.00");
        BigDecimal totalNet = new BigDecimal("850.00");
        
        when(dividendRepository.getTotalAmountByYear(year)).thenReturn(totalGross);
        when(dividendRepository.getTotalTaxByYear(year)).thenReturn(totalTax);
        when(dividendRepository.getTotalNetAmountByYear(year)).thenReturn(totalNet);

        // When
        var result = dividendService.getYearlyDividendStats(year);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("year")).isEqualTo(year);
        assertThat(result.get("totalGross")).isEqualTo(totalGross);
        assertThat(result.get("totalTax")).isEqualTo(totalTax);
        assertThat(result.get("totalNet")).isEqualTo(totalNet);
        
        verify(dividendRepository).getTotalAmountByYear(year);
        verify(dividendRepository).getTotalTaxByYear(year);
        verify(dividendRepository).getTotalNetAmountByYear(year);
    }

    @Test
    @DisplayName("세금 계산 - 15% 세율")
    void calculateTax_15Percent() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");

        // When
        BigDecimal result = dividendService.calculateTax(totalAmount);

        // Then
        assertThat(result).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("실수령액 계산")
    void calculateNetAmount() {
        // Given
        BigDecimal totalAmount = new BigDecimal("100.00");
        BigDecimal taxAmount = new BigDecimal("15.00");

        // When
        BigDecimal result = dividendService.calculateNetAmount(totalAmount, taxAmount);

        // Then
        assertThat(result).isEqualByComparingTo(new BigDecimal("85.00"));
    }
}