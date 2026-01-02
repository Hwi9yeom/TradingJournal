package com.trading.journal.service;

import com.trading.journal.dto.FifoResult;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.Account;
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
    private final AccountService accountService;
    private final FifoCalculationService fifoCalculationService;

    public TransactionDto createTransaction(TransactionDto dto) {
        // Account 처리: accountId가 없으면 기본 계좌 사용
        Account account;
        if (dto.getAccountId() != null) {
            account = accountService.getAccountEntity(dto.getAccountId());
        } else {
            account = accountService.getDefaultAccount();
        }

        Stock stock = stockRepository.findBySymbol(dto.getStockSymbol())
                .orElseGet(() -> createNewStock(dto.getStockSymbol()));

        Transaction transaction = Transaction.builder()
                .account(account)
                .stock(stock)
                .type(dto.getType())
                .quantity(dto.getQuantity())
                .price(dto.getPrice())
                .commission(dto.getCommission())
                .transactionDate(dto.getTransactionDate())
                .notes(dto.getNotes())
                // 매수인 경우 remainingQuantity 초기화
                .remainingQuantity(dto.getType() == TransactionType.BUY ? dto.getQuantity() : null)
                .build();

        transaction = transactionRepository.save(transaction);

        // 매도인 경우 FIFO 계산
        if (dto.getType() == TransactionType.SELL) {
            FifoResult fifoResult = fifoCalculationService.calculateFifoProfit(transaction);
            fifoCalculationService.applyFifoResult(transaction, fifoResult);
        }

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
    public Page<TransactionDto> getTransactionsByAccount(Long accountId, Pageable pageable) {
        return transactionRepository.findByAccountId(accountId, pageable)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByAccount(Long accountId) {
        return transactionRepository.findByAccountIdWithStock(accountId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsByAccountAndSymbol(Long accountId, String symbol) {
        return transactionRepository.findByAccountIdAndSymbol(accountId, symbol).stream()
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
    public List<TransactionDto> getTransactionsByAccountAndDateRange(Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByAccountIdAndDateRange(accountId, startDate, endDate).stream()
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

        // Account 변경 시
        if (dto.getAccountId() != null && !dto.getAccountId().equals(
                transaction.getAccount() != null ? transaction.getAccount().getId() : null)) {
            Account newAccount = accountService.getAccountEntity(dto.getAccountId());
            transaction.setAccount(newAccount);
        }

        transaction.setQuantity(dto.getQuantity());
        transaction.setPrice(dto.getPrice());
        transaction.setCommission(dto.getCommission());
        transaction.setTransactionDate(dto.getTransactionDate());
        transaction.setNotes(dto.getNotes());

        transaction = transactionRepository.save(transaction);

        // Account 기반으로 포트폴리오 및 FIFO 재계산
        Long accountId = transaction.getAccount() != null ? transaction.getAccount().getId() : null;
        Long stockId = transaction.getStock().getId();

        fifoCalculationService.recalculateFifoForAccountStock(accountId, stockId);
        portfolioService.recalculatePortfolio(accountId, stockId);

        return convertToDto(transaction);
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));

        Long stockId = transaction.getStock().getId();
        Long accountId = transaction.getAccount() != null ? transaction.getAccount().getId() : null;

        transactionRepository.delete(transaction);

        // FIFO 및 포트폴리오 재계산
        fifoCalculationService.recalculateFifoForAccountStock(accountId, stockId);
        portfolioService.recalculatePortfolio(accountId, stockId);
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
        TransactionDto.TransactionDtoBuilder builder = TransactionDto.builder()
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
                .realizedPnl(transaction.getRealizedPnl())
                .costBasis(transaction.getCostBasis())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt());

        // Account 정보 추가
        if (transaction.getAccount() != null) {
            builder.accountId(transaction.getAccount().getId())
                    .accountName(transaction.getAccount().getName());
        }

        return builder.build();
    }
}
