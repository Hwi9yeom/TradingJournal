package com.trading.journal.service;

import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;
    private final PortfolioService portfolioService;
    private final StockPriceService stockPriceService;
    
    public TransactionDto createTransaction(TransactionDto dto) {
        Stock stock = stockRepository.findBySymbol(dto.getStockSymbol())
                .orElseGet(() -> createNewStock(dto.getStockSymbol()));
        
        Transaction transaction = Transaction.builder()
                .stock(stock)
                .type(dto.getType())
                .quantity(dto.getQuantity())
                .price(dto.getPrice())
                .commission(dto.getCommission())
                .transactionDate(dto.getTransactionDate())
                .notes(dto.getNotes())
                .build();
        
        transaction = transactionRepository.save(transaction);
        portfolioService.updatePortfolio(transaction);
        
        return convertToDto(transaction);
    }
    
    @Transactional(readOnly = true)
    public Page<TransactionDto> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(this::convertToDto);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDto> getAllTransactions() {
        return transactionRepository.findAllWithStock().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsBySymbol(String symbol) {
        return transactionRepository.findBySymbolWithStock(symbol).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByDateRange(startDate, endDate).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
        return convertToDto(transaction);
    }
    
    public TransactionDto updateTransaction(Long id, TransactionDto dto) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
        
        transaction.setQuantity(dto.getQuantity());
        transaction.setPrice(dto.getPrice());
        transaction.setCommission(dto.getCommission());
        transaction.setTransactionDate(dto.getTransactionDate());
        transaction.setNotes(dto.getNotes());
        
        transaction = transactionRepository.save(transaction);
        portfolioService.recalculatePortfolio(transaction.getStock().getId());
        
        return convertToDto(transaction);
    }
    
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
        
        Long stockId = transaction.getStock().getId();
        transactionRepository.delete(transaction);
        portfolioService.recalculatePortfolio(stockId);
    }
    
    private Stock createNewStock(String symbol) {
        try {
            yahoofinance.Stock yahooStock = stockPriceService.getStockInfo(symbol);
            Stock.StockBuilder stockBuilder = Stock.builder()
                    .symbol(symbol.toUpperCase());
            
            if (yahooStock != null) {
                if (yahooStock.getName() != null) {
                    stockBuilder.name(yahooStock.getName());
                } else {
                    stockBuilder.name(symbol.toUpperCase());
                }
                
                if (yahooStock.getStockExchange() != null) {
                    stockBuilder.exchange(yahooStock.getStockExchange());
                }
            } else {
                stockBuilder.name(symbol.toUpperCase());
            }
            
            return stockRepository.save(stockBuilder.build());
        } catch (Exception e) {
            log.error("Failed to fetch stock info for symbol: {}", symbol, e);
            Stock stock = Stock.builder()
                    .symbol(symbol.toUpperCase())
                    .name(symbol.toUpperCase())
                    .build();
            return stockRepository.save(stock);
        }
    }
    
    private TransactionDto convertToDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .stockSymbol(transaction.getStock().getSymbol())
                .stockName(transaction.getStock().getName())
                .type(transaction.getType())
                .quantity(transaction.getQuantity())
                .price(transaction.getPrice())
                .commission(transaction.getCommission())
                .transactionDate(transaction.getTransactionDate())
                .notes(transaction.getNotes())
                .totalAmount(transaction.getTotalAmount())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}