package com.trading.journal.service;

import com.trading.journal.dto.DividendDto;
import com.trading.journal.dto.DividendSummaryDto;
import com.trading.journal.entity.Dividend;
import com.trading.journal.entity.Stock;
import com.trading.journal.repository.DividendRepository;
import com.trading.journal.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DividendService {
    
    private final DividendRepository dividendRepository;
    private final StockRepository stockRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    
    @CacheEvict(value = "dividend", allEntries = true)
    public DividendDto createDividend(DividendDto dto) {
        Stock stock = stockRepository.findById(dto.getStockId())
                .orElseThrow(() -> new RuntimeException("Stock not found: " + dto.getStockId()));
        
        // 세금 계산 (기본 세율 15.4%)
        BigDecimal taxRate = dto.getTaxRate() != null ? dto.getTaxRate() : new BigDecimal("15.4");
        BigDecimal totalAmount = dto.getDividendPerShare().multiply(dto.getQuantity());
        BigDecimal taxAmount = totalAmount.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = totalAmount.subtract(taxAmount);
        
        Dividend dividend = Dividend.builder()
                .stock(stock)
                .exDividendDate(dto.getExDividendDate())
                .paymentDate(dto.getPaymentDate())
                .dividendPerShare(dto.getDividendPerShare())
                .quantity(dto.getQuantity())
                .totalAmount(totalAmount)
                .taxAmount(taxAmount)
                .netAmount(netAmount)
                .memo(dto.getMemo())
                .build();
        
        dividend = dividendRepository.save(dividend);
        log.info("Created dividend: {} for stock: {}", dividend.getId(), stock.getSymbol());
        
        return convertToDto(dividend);
    }
    
    @CacheEvict(value = "dividend", allEntries = true)
    public DividendDto updateDividend(Long id, DividendDto dto) {
        Dividend dividend = dividendRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dividend not found: " + id));
        
        if (dto.getStockId() != null && !dto.getStockId().equals(dividend.getStock().getId())) {
            Stock stock = stockRepository.findById(dto.getStockId())
                    .orElseThrow(() -> new RuntimeException("Stock not found: " + dto.getStockId()));
            dividend.setStock(stock);
        }
        
        if (dto.getExDividendDate() != null) dividend.setExDividendDate(dto.getExDividendDate());
        if (dto.getPaymentDate() != null) dividend.setPaymentDate(dto.getPaymentDate());
        if (dto.getDividendPerShare() != null) dividend.setDividendPerShare(dto.getDividendPerShare());
        if (dto.getQuantity() != null) dividend.setQuantity(dto.getQuantity());
        
        // 재계산
        BigDecimal taxRate = dto.getTaxRate() != null ? dto.getTaxRate() : new BigDecimal("15.4");
        BigDecimal totalAmount = dividend.getDividendPerShare().multiply(dividend.getQuantity());
        BigDecimal taxAmount = totalAmount.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal netAmount = totalAmount.subtract(taxAmount);
        
        dividend.setTotalAmount(totalAmount);
        dividend.setTaxAmount(taxAmount);
        dividend.setNetAmount(netAmount);
        if (dto.getMemo() != null) dividend.setMemo(dto.getMemo());
        
        dividend = dividendRepository.save(dividend);
        log.info("Updated dividend: {}", dividend.getId());
        
        return convertToDto(dividend);
    }
    
    @CacheEvict(value = "dividend", allEntries = true)
    public void deleteDividend(Long id) {
        dividendRepository.deleteById(id);
        log.info("Deleted dividend: {}", id);
    }
    
    @Transactional(readOnly = true)
    public DividendDto getDividend(Long id) {
        Dividend dividend = dividendRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dividend not found: " + id));
        return convertToDto(dividend);
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "dividend", key = "#symbol")
    public List<DividendDto> getDividendsByStock(String symbol) {
        return dividendRepository.findByStockSymbol(symbol).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<DividendDto> getDividendsByPeriod(LocalDate startDate, LocalDate endDate) {
        return dividendRepository.findByPaymentDateBetweenOrderByPaymentDateDesc(startDate, endDate).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    @Cacheable(value = "dividend", key = "'summary'")
    public DividendSummaryDto getDividendSummary() {
        LocalDate now = LocalDate.now();
        LocalDate yearStart = LocalDate.of(now.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(now.getYear(), 12, 31);
        
        // 전체 배당금
        List<Dividend> allDividends = dividendRepository.findAll();
        BigDecimal totalDividends = allDividends.stream()
                .map(Dividend::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalTax = allDividends.stream()
                .map(Dividend::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 올해 배당금
        BigDecimal yearlyDividends = dividendRepository.getTotalDividendsByPeriod(yearStart, yearEnd);
        if (yearlyDividends == null) yearlyDividends = BigDecimal.ZERO;
        
        // 월평균 배당금 (최근 12개월)
        LocalDate twelveMonthsAgo = now.minusMonths(12);
        BigDecimal last12MonthsDividends = dividendRepository.getTotalDividendsByPeriod(twelveMonthsAgo, now);
        BigDecimal monthlyAverage = last12MonthsDividends != null 
                ? last12MonthsDividends.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        // 배당 수익률 계산 (연간 배당금 / 현재 포트폴리오 가치)
        BigDecimal currentPortfolioValue = portfolioAnalysisService.getPortfolioSummary().getTotalCurrentValue();
        BigDecimal dividendYield = currentPortfolioValue.compareTo(BigDecimal.ZERO) > 0
                ? yearlyDividends.divide(currentPortfolioValue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        
        // TOP 배당 종목
        List<DividendSummaryDto.StockDividendDto> topStocks = getTopDividendStocks(yearStart, yearEnd);
        
        // 월별 배당금
        List<DividendSummaryDto.MonthlyDividendDto> monthlyDividends = getMonthlyDividends();
        
        return DividendSummaryDto.builder()
                .totalDividends(totalDividends)
                .totalTax(totalTax)
                .yearlyDividends(yearlyDividends)
                .monthlyAverage(monthlyAverage)
                .dividendYield(dividendYield)
                .topDividendStocks(topStocks)
                .monthlyDividends(monthlyDividends)
                .build();
    }
    
    private List<DividendSummaryDto.StockDividendDto> getTopDividendStocks(LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = dividendRepository.getTopDividendStocks(startDate, endDate);
        List<DividendSummaryDto.StockDividendDto> topStocks = new ArrayList<>();
        
        for (Object[] result : results) {
            if (topStocks.size() >= 5) break; // TOP 5만
            
            Stock stock = (Stock) result[0];
            BigDecimal totalDividend = (BigDecimal) result[1];
            
            // 해당 종목의 배당 횟수
            List<Dividend> stockDividends = dividendRepository.findByStockOrderByPaymentDateDesc(stock);
            int paymentCount = stockDividends.size();
            
            topStocks.add(DividendSummaryDto.StockDividendDto.builder()
                    .stockSymbol(stock.getSymbol())
                    .stockName(stock.getName())
                    .totalDividend(totalDividend)
                    .paymentCount(paymentCount)
                    .build());
        }
        
        return topStocks;
    }
    
    private List<DividendSummaryDto.MonthlyDividendDto> getMonthlyDividends() {
        List<Object[]> results = dividendRepository.getMonthlyDividends();
        List<DividendSummaryDto.MonthlyDividendDto> monthlyDividends = new ArrayList<>();
        
        // 최근 12개월만
        int count = 0;
        for (Object[] result : results) {
            if (count++ >= 12) break;
            
            Integer year = (Integer) result[0];
            Integer month = (Integer) result[1];
            BigDecimal amount = (BigDecimal) result[2];
            
            monthlyDividends.add(DividendSummaryDto.MonthlyDividendDto.builder()
                    .year(year)
                    .month(month)
                    .amount(amount)
                    .build());
        }
        
        return monthlyDividends;
    }
    
    private DividendDto convertToDto(Dividend dividend) {
        BigDecimal taxRate = dividend.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                ? dividend.getTaxAmount().divide(dividend.getTotalAmount(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;
        
        return DividendDto.builder()
                .id(dividend.getId())
                .stockId(dividend.getStock().getId())
                .stockSymbol(dividend.getStock().getSymbol())
                .stockName(dividend.getStock().getName())
                .exDividendDate(dividend.getExDividendDate())
                .paymentDate(dividend.getPaymentDate())
                .dividendPerShare(dividend.getDividendPerShare())
                .quantity(dividend.getQuantity())
                .totalAmount(dividend.getTotalAmount())
                .taxAmount(dividend.getTaxAmount())
                .netAmount(dividend.getNetAmount())
                .taxRate(taxRate)
                .memo(dividend.getMemo())
                .build();
    }
}