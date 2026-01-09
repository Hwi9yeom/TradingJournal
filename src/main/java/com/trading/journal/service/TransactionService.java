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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                // 리스크 관리 필드
                .stopLossPrice(dto.getStopLossPrice())
                .takeProfitPrice(dto.getTakeProfitPrice())
                .build();

        // 리스크 관련 계산 (BUY 거래에서 손절가가 설정된 경우)
        if (dto.getType() == TransactionType.BUY && dto.getStopLossPrice() != null) {
            calculateAndSetRiskFields(transaction, dto.getPrice(), dto.getStopLossPrice(),
                    dto.getTakeProfitPrice(), dto.getQuantity());
        }

        transaction = transactionRepository.save(transaction);

        // 매도인 경우 FIFO 계산 및 R-multiple 계산
        if (dto.getType() == TransactionType.SELL) {
            FifoResult fifoResult = fifoCalculationService.calculateFifoProfit(transaction);
            fifoCalculationService.applyFifoResult(transaction, fifoResult);

            // R-multiple 계산 (관련 BUY 거래의 초기 리스크 기반)
            calculateRMultipleForSell(transaction);
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

        // 리스크 필드 업데이트
        transaction.setStopLossPrice(dto.getStopLossPrice());
        transaction.setTakeProfitPrice(dto.getTakeProfitPrice());

        // BUY 거래이고 손절가가 설정된 경우 리스크 계산
        if (transaction.getType() == TransactionType.BUY && dto.getStopLossPrice() != null) {
            calculateAndSetRiskFields(transaction, dto.getPrice(), dto.getStopLossPrice(),
                    dto.getTakeProfitPrice(), dto.getQuantity());
        }

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
                // 리스크 관리 필드
                .stopLossPrice(transaction.getStopLossPrice())
                .takeProfitPrice(transaction.getTakeProfitPrice())
                .initialRiskAmount(transaction.getInitialRiskAmount())
                .riskRewardRatio(transaction.getRiskRewardRatio())
                .rMultiple(transaction.getRMultiple())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt());

        // 손절/익절 % 계산
        if (transaction.getPrice() != null && transaction.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            if (transaction.getStopLossPrice() != null) {
                BigDecimal stopLossPercent = transaction.getPrice().subtract(transaction.getStopLossPrice())
                        .divide(transaction.getPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                builder.stopLossPercent(stopLossPercent);
            }
            if (transaction.getTakeProfitPrice() != null) {
                BigDecimal takeProfitPercent = transaction.getTakeProfitPrice().subtract(transaction.getPrice())
                        .divide(transaction.getPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                builder.takeProfitPercent(takeProfitPercent);
            }
        }

        // Account 정보 추가
        if (transaction.getAccount() != null) {
            builder.accountId(transaction.getAccount().getId())
                    .accountName(transaction.getAccount().getName());
        }

        return builder.build();
    }

    /**
     * 리스크 관련 필드 계산 및 설정
     */
    private void calculateAndSetRiskFields(Transaction transaction, BigDecimal entryPrice,
                                           BigDecimal stopLossPrice, BigDecimal takeProfitPrice,
                                           BigDecimal quantity) {
        // 초기 리스크 금액 = (진입가 - 손절가) × 수량
        BigDecimal riskPerShare = entryPrice.subtract(stopLossPrice).abs();
        BigDecimal initialRiskAmount = riskPerShare.multiply(quantity);
        transaction.setInitialRiskAmount(initialRiskAmount);

        // 리스크/리워드 비율 = (익절가 - 진입가) / (진입가 - 손절가)
        if (takeProfitPrice != null && riskPerShare.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rewardPerShare = takeProfitPrice.subtract(entryPrice).abs();
            BigDecimal riskRewardRatio = rewardPerShare.divide(riskPerShare, 4, RoundingMode.HALF_UP);
            transaction.setRiskRewardRatio(riskRewardRatio);
        }
    }

    /**
     * SELL 거래에 대한 R-multiple 계산
     * R-multiple = 실현손익 / 초기리스크
     */
    private void calculateRMultipleForSell(Transaction sellTransaction) {
        if (sellTransaction.getRealizedPnl() == null) {
            return;
        }

        // 관련 BUY 거래들의 평균 초기 리스크 계산
        Long accountId = sellTransaction.getAccount() != null ? sellTransaction.getAccount().getId() : null;
        Long stockId = sellTransaction.getStock().getId();

        List<Transaction> buyTransactions = transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                accountId, stockId, TransactionType.BUY);

        // 초기 리스크가 설정된 BUY 거래들의 평균 리스크 계산
        BigDecimal totalInitialRisk = BigDecimal.ZERO;
        int count = 0;
        for (Transaction buy : buyTransactions) {
            if (buy.getInitialRiskAmount() != null && buy.getInitialRiskAmount().compareTo(BigDecimal.ZERO) > 0) {
                // 주당 리스크 기준으로 계산
                BigDecimal riskPerShare = buy.getInitialRiskAmount().divide(buy.getQuantity(), 4, RoundingMode.HALF_UP);
                totalInitialRisk = totalInitialRisk.add(riskPerShare);
                count++;
            }
        }

        if (count > 0) {
            BigDecimal avgRiskPerShare = totalInitialRisk.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
            BigDecimal sellInitialRisk = avgRiskPerShare.multiply(sellTransaction.getQuantity());

            if (sellInitialRisk.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rMultiple = sellTransaction.getRealizedPnl()
                        .divide(sellInitialRisk, 4, RoundingMode.HALF_UP);
                sellTransaction.setRMultiple(rMultiple);
                sellTransaction.setInitialRiskAmount(sellInitialRisk);
                transactionRepository.save(sellTransaction);
            }
        }
    }

    /**
     * 초기 리스크 계산
     */
    public BigDecimal calculateInitialRisk(BigDecimal entryPrice, BigDecimal stopLossPrice, BigDecimal quantity) {
        if (entryPrice == null || stopLossPrice == null || quantity == null) {
            return null;
        }
        return entryPrice.subtract(stopLossPrice).abs().multiply(quantity);
    }

    /**
     * 리스크/리워드 비율 계산
     */
    public BigDecimal calculateRiskRewardRatio(BigDecimal entryPrice, BigDecimal stopLossPrice,
                                               BigDecimal takeProfitPrice) {
        if (entryPrice == null || stopLossPrice == null || takeProfitPrice == null) {
            return null;
        }
        BigDecimal risk = entryPrice.subtract(stopLossPrice).abs();
        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal reward = takeProfitPrice.subtract(entryPrice).abs();
        return reward.divide(risk, 4, RoundingMode.HALF_UP);
    }

    /**
     * R-multiple 계산
     */
    public BigDecimal calculateRMultiple(BigDecimal realizedPnl, BigDecimal initialRisk) {
        if (realizedPnl == null || initialRisk == null || initialRisk.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return realizedPnl.divide(initialRisk, 4, RoundingMode.HALF_UP);
    }
}
