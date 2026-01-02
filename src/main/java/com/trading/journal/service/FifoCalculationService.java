package com.trading.journal.service;

import com.trading.journal.dto.FifoResult;
import com.trading.journal.dto.FifoResult.BuyConsumption;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * FIFO(선입선출) 방식 실현 손익 계산 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FifoCalculationService {

    private final TransactionRepository transactionRepository;

    /**
     * FIFO 방식으로 매도 거래의 실현 손익 계산
     *
     * @param sellTransaction 매도 거래
     * @return FifoResult (realizedPnl, costBasis, 소진된 매수 거래 목록)
     */
    @Transactional(readOnly = true)
    public FifoResult calculateFifoProfit(Transaction sellTransaction) {
        if (sellTransaction.getType() != TransactionType.SELL) {
            throw new IllegalArgumentException("FIFO 계산은 매도 거래에만 적용됩니다");
        }

        Long accountId = sellTransaction.getAccount() != null ?
                sellTransaction.getAccount().getId() : null;
        Long stockId = sellTransaction.getStock().getId();
        LocalDateTime sellDate = sellTransaction.getTransactionDate();

        // 1. 해당 계좌/종목의 매수 거래 중 잔여 수량이 있는 것들을 날짜순 조회
        List<Transaction> availableBuys = transactionRepository
                .findAvailableBuyTransactionsForFifo(accountId, stockId, sellDate);

        // 2. FIFO로 매수 거래 소진
        BigDecimal remainingToSell = sellTransaction.getQuantity();
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        List<BuyConsumption> consumptions = new ArrayList<>();

        for (Transaction buyTx : availableBuys) {
            if (remainingToSell.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal available = buyTx.getRemainingQuantity();
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal consumed = available.min(remainingToSell);

            // 해당 매수의 단가 (수수료 포함)
            BigDecimal buyUnitPrice = calculateUnitPrice(buyTx);
            BigDecimal consumedCost = buyUnitPrice.multiply(consumed);

            totalCostBasis = totalCostBasis.add(consumedCost);
            remainingToSell = remainingToSell.subtract(consumed);

            consumptions.add(BuyConsumption.builder()
                    .buyTransaction(buyTx)
                    .consumedQuantity(consumed)
                    .consumedCost(consumedCost)
                    .build());
        }

        // 잔여 매도 수량이 있으면 경고 (매수 기록 없이 매도한 경우)
        if (remainingToSell.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("매도 수량({})이 매수 잔여 수량보다 큽니다. Stock: {}, Account: {}",
                    remainingToSell, stockId, accountId);
        }

        // 3. 실현 손익 계산
        BigDecimal sellAmount = sellTransaction.getTotalAmount();
        BigDecimal realizedPnl = sellAmount.subtract(totalCostBasis);

        return FifoResult.builder()
                .realizedPnl(realizedPnl)
                .costBasis(totalCostBasis)
                .consumptions(consumptions)
                .build();
    }

    /**
     * FIFO 결과 적용 (매수 거래의 잔여 수량 차감 및 매도 거래에 손익 저장)
     */
    @Transactional
    public void applyFifoResult(Transaction sellTransaction, FifoResult result) {
        // 매도 거래에 실현손익 저장
        sellTransaction.setRealizedPnl(result.getRealizedPnl());
        sellTransaction.setCostBasis(result.getCostBasis());
        transactionRepository.save(sellTransaction);

        // 매수 거래들의 잔여 수량 차감
        for (BuyConsumption consumption : result.getConsumptions()) {
            Transaction buyTx = consumption.getBuyTransaction();
            BigDecimal newRemaining = buyTx.getRemainingQuantity()
                    .subtract(consumption.getConsumedQuantity());
            buyTx.setRemainingQuantity(newRemaining);
            transactionRepository.save(buyTx);
        }

        log.debug("FIFO 적용 완료 - 매도 ID: {}, 실현손익: {}, 원가: {}",
                sellTransaction.getId(), result.getRealizedPnl(), result.getCostBasis());
    }

    /**
     * 특정 계좌/종목의 FIFO 재계산
     * 거래 수정/삭제 시 호출
     */
    @Transactional
    public void recalculateFifoForAccountStock(Long accountId, Long stockId) {
        log.info("FIFO 재계산 시작 - Account: {}, Stock: {}", accountId, stockId);

        List<Transaction> allTransactions = transactionRepository
                .findByAccountIdAndStockIdOrderByTransactionDateAsc(accountId, stockId);

        // 모든 거래의 FIFO 필드 초기화
        for (Transaction tx : allTransactions) {
            if (tx.getType() == TransactionType.BUY) {
                tx.setRemainingQuantity(tx.getQuantity());
                tx.setRealizedPnl(null);
                tx.setCostBasis(null);
            } else {
                tx.setRemainingQuantity(null);
                tx.setRealizedPnl(null);
                tx.setCostBasis(null);
            }
        }
        transactionRepository.saveAll(allTransactions);

        // 매도 거래를 날짜 순서대로 FIFO 적용
        allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .forEach(sellTx -> {
                    FifoResult result = calculateFifoProfit(sellTx);
                    applyFifoResult(sellTx, result);
                });

        log.info("FIFO 재계산 완료 - Account: {}, Stock: {}", accountId, stockId);
    }

    /**
     * 기존 모든 매도 거래에 대한 realizedPnl 마이그레이션
     */
    @Transactional
    public void migrateAllExistingSellTransactions() {
        log.info("전체 FIFO 마이그레이션 시작...");

        // 계좌별, 종목별 쌍 조회
        List<Object[]> accountStockPairs = transactionRepository.findDistinctAccountStockPairs();

        int totalPairs = accountStockPairs.size();
        int processed = 0;

        for (Object[] pair : accountStockPairs) {
            Long accountId = (Long) pair[0];
            Long stockId = (Long) pair[1];

            recalculateFifoForAccountStock(accountId, stockId);

            processed++;
            if (processed % 10 == 0) {
                log.info("마이그레이션 진행: {}/{}", processed, totalPairs);
            }
        }

        log.info("전체 FIFO 마이그레이션 완료 - 총 {} 계좌/종목 쌍 처리", totalPairs);
    }

    /**
     * 매수 거래의 단위당 가격 계산 (수수료 포함)
     */
    private BigDecimal calculateUnitPrice(Transaction buyTransaction) {
        BigDecimal totalAmount = buyTransaction.getTotalAmount();
        BigDecimal quantity = buyTransaction.getQuantity();

        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalAmount.divide(quantity, 6, RoundingMode.HALF_UP);
    }
}
