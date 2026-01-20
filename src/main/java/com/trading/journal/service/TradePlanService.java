package com.trading.journal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.PositionSizingRequestDto;
import com.trading.journal.dto.PositionSizingResultDto;
import com.trading.journal.dto.TradePlanDto;
import com.trading.journal.dto.TradePlanDto.*;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.*;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TradePlanRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TradePlanService {

    private final TradePlanRepository planRepository;
    private final StockRepository stockRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final PositionSizingService positionSizingService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    /** 새 플랜 생성 */
    public TradePlanDto createPlan(TradePlanDto dto) {
        // 계좌 조회
        Account account =
                dto.getAccountId() != null
                        ? accountService.getAccountEntity(dto.getAccountId())
                        : accountService.getDefaultAccount();

        // 종목 조회
        Stock stock =
                stockRepository
                        .findBySymbol(dto.getStockSymbol())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "종목을 찾을 수 없습니다: " + dto.getStockSymbol()));

        TradePlan plan =
                TradePlan.builder()
                        .account(account)
                        .stock(stock)
                        .planType(dto.getPlanType())
                        .title(dto.getTitle())
                        .plannedEntryPrice(dto.getPlannedEntryPrice())
                        .plannedStopLossPrice(dto.getPlannedStopLossPrice())
                        .plannedTakeProfitPrice(dto.getPlannedTakeProfitPrice())
                        .plannedTakeProfit2Price(dto.getPlannedTakeProfit2Price())
                        .plannedQuantity(dto.getPlannedQuantity())
                        .plannedRiskPercent(dto.getPlannedRiskPercent())
                        .strategy(dto.getStrategy())
                        .entryConditions(dto.getEntryConditions())
                        .exitConditions(dto.getExitConditions())
                        .invalidationConditions(dto.getInvalidationConditions())
                        .checklist(serializeChecklist(dto.getChecklist()))
                        .notes(dto.getNotes())
                        .validUntil(dto.getValidUntil())
                        .marketContext(dto.getMarketContext())
                        .status(TradePlanStatus.PLANNED)
                        .build();

        plan.calculateDerivedFields();
        TradePlan saved = planRepository.save(plan);

        log.info(
                "새 트레이드 플랜 생성: {} - {} @ {}",
                stock.getSymbol(),
                dto.getPlanType(),
                dto.getPlannedEntryPrice());

        return convertToDto(saved);
    }

    /** 플랜 수정 */
    public TradePlanDto updatePlan(Long id, TradePlanDto dto) {
        TradePlan plan =
                planRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + id));

        if (plan.getStatus() != TradePlanStatus.PLANNED) {
            throw new IllegalStateException("계획됨 상태의 플랜만 수정할 수 있습니다");
        }

        plan.setTitle(dto.getTitle());
        plan.setPlannedEntryPrice(dto.getPlannedEntryPrice());
        plan.setPlannedStopLossPrice(dto.getPlannedStopLossPrice());
        plan.setPlannedTakeProfitPrice(dto.getPlannedTakeProfitPrice());
        plan.setPlannedTakeProfit2Price(dto.getPlannedTakeProfit2Price());
        plan.setPlannedQuantity(dto.getPlannedQuantity());
        plan.setStrategy(dto.getStrategy());
        plan.setEntryConditions(dto.getEntryConditions());
        plan.setExitConditions(dto.getExitConditions());
        plan.setInvalidationConditions(dto.getInvalidationConditions());
        plan.setChecklist(serializeChecklist(dto.getChecklist()));
        plan.setNotes(dto.getNotes());
        plan.setValidUntil(dto.getValidUntil());
        plan.setMarketContext(dto.getMarketContext());

        plan.calculateDerivedFields();
        TradePlan saved = planRepository.save(plan);

        log.info("플랜 수정됨: {}", id);
        return convertToDto(saved);
    }

    /** 플랜 실행 -> 거래로 변환 */
    public TradePlanDto executePlan(Long planId, ExecuteRequest request) {
        TradePlan plan =
                planRepository
                        .findById(planId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + planId));

        if (plan.getStatus() != TradePlanStatus.PLANNED) {
            throw new IllegalStateException("계획됨 상태의 플랜만 실행할 수 있습니다");
        }

        // 거래 생성
        TransactionDto txDto =
                TransactionDto.builder()
                        .accountId(plan.getAccount() != null ? plan.getAccount().getId() : null)
                        .stockSymbol(plan.getStock().getSymbol())
                        .type(
                                plan.getPlanType() == TradePlanType.LONG
                                        ? TransactionType.BUY
                                        : TransactionType.SELL)
                        .quantity(request.getActualQuantity())
                        .price(request.getActualEntryPrice())
                        .commission(request.getCommission())
                        .transactionDate(
                                request.getTransactionDate() != null
                                        ? request.getTransactionDate()
                                        : LocalDateTime.now())
                        .stopLossPrice(plan.getPlannedStopLossPrice())
                        .takeProfitPrice(plan.getPlannedTakeProfitPrice())
                        .notes(
                                "플랜 실행: "
                                        + (plan.getTitle() != null
                                                ? plan.getTitle()
                                                : "Plan #" + planId))
                        .build();

        TransactionDto createdTx = transactionService.createTransaction(txDto);

        // 플랜 상태 업데이트
        plan.setStatus(TradePlanStatus.EXECUTED);
        plan.setExecutedTransactionId(createdTx.getId());
        plan.setExecutedAt(LocalDateTime.now());
        plan.setActualEntryPrice(request.getActualEntryPrice());
        plan.setActualQuantity(request.getActualQuantity());
        plan.setExecutionNotes(request.getExecutionNotes());

        TradePlan saved = planRepository.save(plan);

        log.info("플랜 실행됨: {} -> Transaction #{}", planId, createdTx.getId());

        return convertToDto(saved);
    }

    /** 플랜 취소 */
    public TradePlanDto cancelPlan(Long planId, String reason) {
        TradePlan plan =
                planRepository
                        .findById(planId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + planId));

        if (plan.getStatus() != TradePlanStatus.PLANNED) {
            throw new IllegalStateException("계획됨 상태의 플랜만 취소할 수 있습니다");
        }

        plan.setStatus(TradePlanStatus.CANCELLED);
        if (reason != null && !reason.isEmpty()) {
            String existingNotes = plan.getNotes();
            plan.setNotes(
                    existingNotes != null
                            ? existingNotes + "\n[취소 사유] " + reason
                            : "[취소 사유] " + reason);
        }

        TradePlan saved = planRepository.save(plan);
        log.info("플랜 취소됨: {}", planId);

        return convertToDto(saved);
    }

    /** 플랜 결과 업데이트 (거래 청산 후) */
    public TradePlanDto updatePlanResult(
            Long planId, Long resultTransactionId, Boolean followedPlan) {
        TradePlan plan =
                planRepository
                        .findById(planId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + planId));

        if (plan.getStatus() != TradePlanStatus.EXECUTED) {
            throw new IllegalStateException("실행된 플랜만 결과를 업데이트할 수 있습니다");
        }

        Transaction resultTx =
                transactionRepository
                        .findById(resultTransactionId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "거래를 찾을 수 없습니다: " + resultTransactionId));

        plan.setResultTransactionId(resultTransactionId);
        plan.setRealizedPnl(resultTx.getRealizedPnl());
        plan.setActualRMultiple(resultTx.getRMultiple());
        plan.setFollowedPlan(followedPlan);

        TradePlan saved = planRepository.save(plan);
        log.info(
                "플랜 결과 업데이트됨: {} - PnL: {}, R: {}",
                planId,
                resultTx.getRealizedPnl(),
                resultTx.getRMultiple());

        return convertToDto(saved);
    }

    /** 포지션 사이징 계산 (플랜 생성 시 자동 수량 계산) */
    public PositionSizingResultDto calculatePositionSize(
            Long accountId,
            BigDecimal entryPrice,
            BigDecimal stopLossPrice,
            BigDecimal takeProfitPrice,
            BigDecimal riskPercent) {
        PositionSizingRequestDto positionRequest =
                PositionSizingRequestDto.builder()
                        .accountId(accountId)
                        .entryPrice(entryPrice)
                        .stopLossPrice(stopLossPrice)
                        .takeProfitPrice(takeProfitPrice)
                        .riskPercent(riskPercent)
                        .build();

        return positionSizingService.calculatePositionSize(positionRequest);
    }

    /** 플랜 조회 */
    @Transactional(readOnly = true)
    public TradePlanDto getPlan(Long id) {
        TradePlan plan =
                planRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + id));
        return convertToDto(plan);
    }

    /** 대기 중인 플랜 조회 */
    @Transactional(readOnly = true)
    public List<TradePlanDto> getPendingPlans() {
        return planRepository.findPendingPlans(LocalDateTime.now()).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** 상태별 플랜 조회 */
    @Transactional(readOnly = true)
    public List<TradePlanDto> getPlansByStatus(TradePlanStatus status) {
        return planRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** 계좌별 플랜 조회 */
    @Transactional(readOnly = true)
    public List<TradePlanDto> getPlansByAccount(Long accountId) {
        return planRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** 최근 플랜 조회 (페이징) */
    @Transactional(readOnly = true)
    public Page<TradePlanDto> getRecentPlans(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return planRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::convertToDto);
    }

    /** 플랜 삭제 */
    public void deletePlan(Long id) {
        TradePlan plan =
                planRepository
                        .findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("플랜을 찾을 수 없습니다: " + id));

        if (plan.getStatus() == TradePlanStatus.EXECUTED) {
            throw new IllegalStateException("실행된 플랜은 삭제할 수 없습니다");
        }

        planRepository.delete(plan);
        log.info("플랜 삭제됨: {}", id);
    }

    /** 플랜 통계 조회 */
    @Transactional(readOnly = true)
    public PlanStatisticsDto getStatistics() {
        long totalPlans = planRepository.count();
        long plannedCount = planRepository.countByStatus(TradePlanStatus.PLANNED);
        long executedCount = planRepository.countByStatus(TradePlanStatus.EXECUTED);
        long cancelledCount = planRepository.countByStatus(TradePlanStatus.CANCELLED);
        long expiredCount = planRepository.countByStatus(TradePlanStatus.EXPIRED);

        // 실행률
        BigDecimal executionRate =
                totalPlans > 0
                        ? BigDecimal.valueOf(executedCount * 100.0 / totalPlans)
                                .setScale(1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // 계획 준수율
        Double adherenceRate = planRepository.getPlanAdherenceRate();
        BigDecimal planAdherenceRate =
                adherenceRate != null
                        ? BigDecimal.valueOf(adherenceRate).setScale(1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // 평균 R-multiple
        Double avgR = planRepository.getAverageRMultiple();
        BigDecimal avgActualRMultiple =
                avgR != null
                        ? BigDecimal.valueOf(avgR).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // 전략별 통계
        List<StrategyPlanStats> strategyStats = new ArrayList<>();
        List<Object[]> rawStats = planRepository.getStrategyStatistics();
        for (Object[] row : rawStats) {
            TradeStrategy strategy = (TradeStrategy) row[0];
            Long count = (Long) row[1];
            Long executed = (Long) row[2];
            Double avgRMultiple = (Double) row[3];

            strategyStats.add(
                    StrategyPlanStats.builder()
                            .strategy(strategy)
                            .strategyLabel(strategy.getLabel())
                            .planCount(count.intValue())
                            .executedCount(executed.intValue())
                            .executionRate(
                                    count > 0
                                            ? BigDecimal.valueOf(executed * 100.0 / count)
                                                    .setScale(1, RoundingMode.HALF_UP)
                                            : BigDecimal.ZERO)
                            .avgRMultiple(
                                    avgRMultiple != null
                                            ? BigDecimal.valueOf(avgRMultiple)
                                                    .setScale(2, RoundingMode.HALF_UP)
                                            : null)
                            .build());
        }

        // 최근 플랜
        List<TradePlanDto> recentPlans =
                planRepository
                        .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5))
                        .map(this::convertToDto)
                        .getContent();

        // 대기 중인 플랜
        List<TradePlanDto> pendingPlans =
                getPendingPlans().stream().limit(5).collect(Collectors.toList());

        return PlanStatisticsDto.builder()
                .totalPlans((int) totalPlans)
                .plannedCount((int) plannedCount)
                .executedCount((int) executedCount)
                .cancelledCount((int) cancelledCount)
                .expiredCount((int) expiredCount)
                .executionRate(executionRate)
                .planAdherenceRate(planAdherenceRate)
                .avgActualRMultiple(avgActualRMultiple)
                .strategyStats(strategyStats)
                .recentPlans(recentPlans)
                .pendingPlans(pendingPlans)
                .build();
    }

    /** 만료된 플랜 자동 처리 (스케줄러) */
    @Scheduled(cron = "0 0 * * * *") // 매시간
    @Transactional
    public void processExpiredPlans() {
        int expired = planRepository.expirePlans(LocalDateTime.now());
        if (expired > 0) {
            log.info("만료된 플랜 {} 개 처리됨", expired);
        }
    }

    // ===== Private Helper Methods =====

    private TradePlanDto convertToDto(TradePlan plan) {
        BigDecimal stopLossPercent = null;
        BigDecimal takeProfitPercent = null;
        BigDecimal entrySlippage = null;

        if (plan.getPlannedEntryPrice() != null
                && plan.getPlannedEntryPrice().compareTo(BigDecimal.ZERO) > 0) {
            if (plan.getPlannedStopLossPrice() != null) {
                stopLossPercent =
                        plan.getPlannedEntryPrice()
                                .subtract(plan.getPlannedStopLossPrice())
                                .divide(plan.getPlannedEntryPrice(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
            }
            if (plan.getPlannedTakeProfitPrice() != null) {
                takeProfitPercent =
                        plan.getPlannedTakeProfitPrice()
                                .subtract(plan.getPlannedEntryPrice())
                                .divide(plan.getPlannedEntryPrice(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
            }
            if (plan.getActualEntryPrice() != null) {
                entrySlippage =
                        plan.getActualEntryPrice()
                                .subtract(plan.getPlannedEntryPrice())
                                .divide(plan.getPlannedEntryPrice(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
            }
        }

        Long daysUntilExpiry = null;
        Boolean isExpired = plan.isExpired();
        if (plan.getValidUntil() != null && plan.getValidUntil().isAfter(LocalDateTime.now())) {
            daysUntilExpiry = ChronoUnit.DAYS.between(LocalDateTime.now(), plan.getValidUntil());
        }

        return TradePlanDto.builder()
                .id(plan.getId())
                .accountId(plan.getAccount() != null ? plan.getAccount().getId() : null)
                .accountName(plan.getAccount() != null ? plan.getAccount().getName() : null)
                .stockId(plan.getStock().getId())
                .stockSymbol(plan.getStock().getSymbol())
                .stockName(plan.getStock().getName())
                .planType(plan.getPlanType())
                .planTypeLabel(plan.getPlanType() != null ? plan.getPlanType().getLabel() : null)
                .status(plan.getStatus())
                .statusLabel(plan.getStatus() != null ? plan.getStatus().getLabel() : null)
                .title(plan.getTitle())
                .plannedEntryPrice(plan.getPlannedEntryPrice())
                .plannedStopLossPrice(plan.getPlannedStopLossPrice())
                .plannedTakeProfitPrice(plan.getPlannedTakeProfitPrice())
                .plannedTakeProfit2Price(plan.getPlannedTakeProfit2Price())
                .plannedQuantity(plan.getPlannedQuantity())
                .plannedPositionValue(plan.getPlannedPositionValue())
                .plannedRiskAmount(plan.getPlannedRiskAmount())
                .plannedRiskPercent(plan.getPlannedRiskPercent())
                .plannedRiskRewardRatio(plan.getPlannedRiskRewardRatio())
                .stopLossPercent(stopLossPercent)
                .takeProfitPercent(takeProfitPercent)
                .strategy(plan.getStrategy())
                .strategyLabel(plan.getStrategy() != null ? plan.getStrategy().getLabel() : null)
                .entryConditions(plan.getEntryConditions())
                .exitConditions(plan.getExitConditions())
                .invalidationConditions(plan.getInvalidationConditions())
                .checklist(deserializeChecklist(plan.getChecklist()))
                .notes(plan.getNotes())
                .validUntil(plan.getValidUntil())
                .marketContext(plan.getMarketContext())
                .isExpired(isExpired)
                .daysUntilExpiry(daysUntilExpiry)
                .executedTransactionId(plan.getExecutedTransactionId())
                .executedAt(plan.getExecutedAt())
                .actualEntryPrice(plan.getActualEntryPrice())
                .actualQuantity(plan.getActualQuantity())
                .executionNotes(plan.getExecutionNotes())
                .entrySlippage(entrySlippage)
                .resultTransactionId(plan.getResultTransactionId())
                .realizedPnl(plan.getRealizedPnl())
                .actualRMultiple(plan.getActualRMultiple())
                .followedPlan(plan.getFollowedPlan())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private String serializeChecklist(List<ChecklistItem> checklist) {
        if (checklist == null || checklist.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(checklist);
        } catch (Exception e) {
            log.warn("체크리스트 직렬화 실패", e);
            return null;
        }
    }

    private List<ChecklistItem> deserializeChecklist(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChecklistItem>>() {});
        } catch (Exception e) {
            log.warn("체크리스트 역직렬화 실패", e);
            return new ArrayList<>();
        }
    }
}
