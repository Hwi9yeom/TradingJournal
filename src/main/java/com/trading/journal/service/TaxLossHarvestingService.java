package com.trading.journal.service;

import com.trading.journal.dto.HarvestingOpportunityDto;
import com.trading.journal.dto.TaxLossHarvestingDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.exception.AccountNotFoundException;
import com.trading.journal.exception.UnauthorizedAccessException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxLossHarvestingService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final StockPriceService stockPriceService;
    private final SecurityContextService securityContextService;

    // 한국 주식 양도소득세율 (TaxCalculationService와 동일)
    private static final BigDecimal BASIC_DEDUCTION = new BigDecimal("2500000"); // 기본공제 250만원
    private static final BigDecimal TAX_RATE = new BigDecimal("0.22"); // 세율 22% (지방소득세 포함)

    // 손실 최소 금액 (너무 작은 손실은 필터링)
    private static final BigDecimal MINIMUM_LOSS_THRESHOLD = new BigDecimal("10000"); // 1만원

    // Wash Sale 기간 (한국: 30일)
    private static final int WASH_SALE_DAYS = 30;

    /**
     * 현재 사용자의 전체 계좌에 대한 Tax-Loss Harvesting 기회 분석
     *
     * @return 전체 계좌 통합 세금 절감 기회 리스트
     */
    public TaxLossHarvestingDto analyzeAllHarvestingOpportunitiesForCurrentUser() {
        Long currentUserId = getRequiredCurrentUserId();
        List<Account> accounts =
                accountRepository.findByUserIdOrderByIsDefaultDescCreatedAtAsc(currentUserId);

        if (accounts.isEmpty()) {
            log.info("No accounts found for current user: {}", currentUserId);
            return buildEmptyResult(null, "계좌 정보가 없습니다.");
        }

        List<HarvestingOpportunityDto> allOpportunities = new ArrayList<>();
        for (Account account : accounts) {
            TaxLossHarvestingDto accountResult =
                    analyzeTaxLossHarvestingOpportunities(account.getId());
            if (accountResult.getOpportunities() != null) {
                allOpportunities.addAll(accountResult.getOpportunities());
            }
        }

        return buildResult(
                null, allOpportunities, String.format("총 %d개 계좌 기준 분석 결과입니다.", accounts.size()));
    }

    /**
     * 특정 계좌의 Tax-Loss Harvesting 기회 분석
     *
     * @param accountId 계좌 ID
     * @return 세금 절감 기회 리스트
     */
    public TaxLossHarvestingDto analyzeTaxLossHarvestingOpportunities(Long accountId) {
        validateAccountOwnership(accountId);
        log.info("Analyzing tax-loss harvesting opportunities for account: {}", accountId);

        // 1. 계좌의 모든 포지션 조회
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(accountId);

        if (portfolios.isEmpty()) {
            log.info("No positions found for account: {}", accountId);
            return buildEmptyResult(accountId, "포지션 정보가 없습니다.");
        }

        // 2. 각 포지션에 대해 손실 여부 확인 및 기회 계산
        List<HarvestingOpportunityDto> opportunities = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            try {
                HarvestingOpportunityDto opportunity = analyzePosition(accountId, portfolio);

                if (opportunity != null) {
                    opportunities.add(opportunity);
                }
            } catch (Exception e) {
                log.error(
                        "Failed to analyze position for stock: {}",
                        portfolio.getStock().getSymbol(),
                        e);
                // 개별 종목 분석 실패는 전체 프로세스를 중단하지 않음
            }
        }

        return buildResult(accountId, opportunities, null);
    }

    /**
     * 개별 포지션 분석
     *
     * @param accountId 계좌 ID
     * @param portfolio 포지션 정보
     * @return 손실이 있는 경우 HarvestingOpportunityDto, 없으면 null
     */
    private HarvestingOpportunityDto analyzePosition(Long accountId, Portfolio portfolio) {
        String symbol = portfolio.getStock().getSymbol();

        // 현재가 조회
        BigDecimal currentPrice;
        try {
            currentPrice = stockPriceService.getCurrentPrice(symbol);
        } catch (Exception e) {
            log.warn("Failed to get current price for {}, skipping position", symbol, e);
            return null;
        }

        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid current price for {}: {}", symbol, currentPrice);
            return null;
        }

        // 평균 단가 (cost basis)
        BigDecimal costBasis = portfolio.getAveragePrice();
        BigDecimal quantity = portfolio.getQuantity();

        // 현재 시장 가치
        BigDecimal marketValue = currentPrice.multiply(quantity);

        // 총 투자 금액
        BigDecimal totalInvestment = portfolio.getTotalInvestment();

        // 미실현 손익 계산
        BigDecimal unrealizedPnl = marketValue.subtract(totalInvestment);

        // 손실이 아니면 null 반환 (수익 중이거나 본절)
        if (unrealizedPnl.compareTo(BigDecimal.ZERO) >= 0) {
            return null;
        }

        // 미실현 손실
        BigDecimal unrealizedLoss = unrealizedPnl.abs();

        // 최소 손실 금액보다 작으면 무시
        if (unrealizedLoss.compareTo(MINIMUM_LOSS_THRESHOLD) < 0) {
            return null;
        }

        // 손실 비율 계산
        BigDecimal unrealizedLossPercent =
                unrealizedLoss
                        .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

        // 잠재적 세금 절감액 계산
        BigDecimal potentialTaxSavings =
                unrealizedLoss.multiply(TAX_RATE).setScale(0, RoundingMode.HALF_UP);

        // Wash Sale 위험 체크
        WashSaleInfo washSaleInfo = checkWashSaleRisk(accountId, portfolio.getStock().getId());

        // 보유 기간 계산
        HoldingPeriodInfo holdingInfo =
                calculateHoldingPeriod(accountId, portfolio.getStock().getId());

        return HarvestingOpportunityDto.builder()
                .stockId(portfolio.getStock().getId())
                .symbol(symbol)
                .stockName(portfolio.getStock().getName())
                .sector(
                        portfolio.getStock().getSector() != null
                                ? portfolio.getStock().getSector().getLabel()
                                : null)
                .quantity(quantity.intValue())
                .costBasis(costBasis)
                .currentPrice(currentPrice)
                .marketValue(marketValue)
                .unrealizedLoss(unrealizedLoss)
                .unrealizedLossPercent(unrealizedLossPercent)
                .potentialTaxSavings(potentialTaxSavings)
                .effectiveTaxRate(TAX_RATE.multiply(new BigDecimal("100")))
                .washSaleRisk(washSaleInfo.hasRisk)
                .lastSaleDate(washSaleInfo.lastSaleDate)
                .daysUntilWashSaleClears(washSaleInfo.daysUntilClears)
                .purchaseDate(holdingInfo.purchaseDate)
                .holdingDays(holdingInfo.holdingDays)
                .isLongTerm(holdingInfo.isLongTerm)
                .build();
    }

    /** Wash Sale 위험 체크 지난 30일 이내에 같은 종목을 매도한 적이 있는지 확인 */
    private WashSaleInfo checkWashSaleRisk(Long accountId, Long stockId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(WASH_SALE_DAYS);

        // 최근 30일 이내의 매도 거래 조회
        List<Transaction> recentSales =
                transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                        accountId, stockId, TransactionType.SELL);

        List<Transaction> salesInWashPeriod =
                recentSales.stream()
                        .filter(t -> t.getTransactionDate().isAfter(thirtyDaysAgo))
                        .collect(Collectors.toList());

        if (salesInWashPeriod.isEmpty()) {
            return new WashSaleInfo(false, null, null);
        }

        // 가장 최근 매도일
        Transaction lastSale =
                salesInWashPeriod.stream()
                        .max(Comparator.comparing(Transaction::getTransactionDate))
                        .orElse(null);

        if (lastSale == null) {
            return new WashSaleInfo(false, null, null);
        }

        LocalDate lastSaleDate = lastSale.getTransactionDate().toLocalDate();
        long daysSinceLastSale = ChronoUnit.DAYS.between(lastSaleDate, LocalDate.now());
        int daysUntilClears = (int) (WASH_SALE_DAYS - daysSinceLastSale);

        return new WashSaleInfo(true, lastSaleDate, daysUntilClears > 0 ? daysUntilClears : 0);
    }

    /** 보유 기간 계산 */
    private HoldingPeriodInfo calculateHoldingPeriod(Long accountId, Long stockId) {
        // 가장 오래된 매수 거래 찾기
        List<Transaction> buyTransactions =
                transactionRepository.findByAccountIdAndStockIdAndTypeOrderByTransactionDateAsc(
                        accountId, stockId, TransactionType.BUY);

        if (buyTransactions.isEmpty()) {
            return new HoldingPeriodInfo(LocalDate.now(), 0, false);
        }

        Transaction firstBuy = buyTransactions.get(0);
        LocalDate purchaseDate = firstBuy.getTransactionDate().toLocalDate();
        long holdingDays = ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        boolean isLongTerm = holdingDays >= 365;

        return new HoldingPeriodInfo(purchaseDate, (int) holdingDays, isLongTerm);
    }

    /** 추천 메시지 생성 */
    private String generateRecommendation(
            List<HarvestingOpportunityDto> opportunities, BigDecimal totalPotentialSavings) {

        if (opportunities.isEmpty()) {
            return "현재 손실 중인 포지션이 없습니다. 세금 절감 기회가 없습니다.";
        }

        if (totalPotentialSavings.compareTo(new BigDecimal("100000")) < 0) {
            return "잠재적 세금 절감액이 크지 않습니다. 거래 수수료를 고려하여 신중히 결정하세요.";
        }

        long washSaleCount =
                opportunities.stream().filter(HarvestingOpportunityDto::getWashSaleRisk).count();

        if (washSaleCount > 0) {
            return String.format(
                    "총 %d개의 Tax-Loss Harvesting 기회가 있으며, 잠재적 세금 절감액은 %s원입니다. "
                            + "단, %d개 포지션은 Wash Sale 규정에 주의가 필요합니다.",
                    opportunities.size(), totalPotentialSavings.toPlainString(), washSaleCount);
        }

        return String.format(
                "총 %d개의 Tax-Loss Harvesting 기회가 있으며, 잠재적 세금 절감액은 %s원입니다. " + "손실 실현을 고려해보세요.",
                opportunities.size(), totalPotentialSavings.toPlainString());
    }

    private TaxLossHarvestingDto buildResult(
            Long accountId,
            List<HarvestingOpportunityDto> opportunities,
            String recommendationPrefix) {
        // 잠재적 세금 절감액 기준으로 정렬 (높은 순)
        opportunities.sort(
                Comparator.comparing(HarvestingOpportunityDto::getPotentialTaxSavings).reversed());

        List<HarvestingOpportunityDto> washSaleRiskPositions =
                opportunities.stream()
                        .filter(HarvestingOpportunityDto::getWashSaleRisk)
                        .collect(Collectors.toList());

        BigDecimal totalUnrealizedLoss =
                opportunities.stream()
                        .map(HarvestingOpportunityDto::getUnrealizedLoss)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPotentialTaxSavings =
                opportunities.stream()
                        .map(HarvestingOpportunityDto::getPotentialTaxSavings)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        String recommendation = generateRecommendation(opportunities, totalPotentialTaxSavings);
        if (recommendationPrefix != null && !recommendationPrefix.isBlank()) {
            recommendation = recommendationPrefix + " " + recommendation;
        }

        boolean hasSignificantOpportunities =
                totalPotentialTaxSavings.compareTo(new BigDecimal("100000")) > 0; // 10만원 이상

        return TaxLossHarvestingDto.builder()
                .accountId(accountId)
                .generatedAt(LocalDateTime.now())
                .totalOpportunities(opportunities.size())
                .totalUnrealizedLoss(totalUnrealizedLoss)
                .totalPotentialTaxSavings(totalPotentialTaxSavings)
                .minimumLossThreshold(MINIMUM_LOSS_THRESHOLD)
                .basicDeduction(BASIC_DEDUCTION)
                .taxRate(TAX_RATE.multiply(new BigDecimal("100"))) // 퍼센트로 변환
                .opportunities(opportunities)
                .washSaleRiskCount(washSaleRiskPositions.size())
                .washSaleRiskPositions(washSaleRiskPositions)
                .recommendation(recommendation)
                .hasSignificantOpportunities(hasSignificantOpportunities)
                .build();
    }

    /** 빈 결과 생성 (포지션이 없거나 분석 실패 시) */
    private TaxLossHarvestingDto buildEmptyResult(Long accountId, String message) {
        return TaxLossHarvestingDto.builder()
                .accountId(accountId)
                .generatedAt(LocalDateTime.now())
                .totalOpportunities(0)
                .totalUnrealizedLoss(BigDecimal.ZERO)
                .totalPotentialTaxSavings(BigDecimal.ZERO)
                .minimumLossThreshold(MINIMUM_LOSS_THRESHOLD)
                .basicDeduction(BASIC_DEDUCTION)
                .taxRate(TAX_RATE.multiply(new BigDecimal("100")))
                .opportunities(new ArrayList<>())
                .washSaleRiskCount(0)
                .washSaleRiskPositions(new ArrayList<>())
                .recommendation(message)
                .hasSignificantOpportunities(false)
                .build();
    }

    private Long getRequiredCurrentUserId() {
        return securityContextService
                .getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedAccessException("로그인이 필요합니다."));
    }

    private void validateAccountOwnership(Long accountId) {
        Long currentUserId = getRequiredCurrentUserId();
        String currentUsername = securityContextService.getCurrentUsername().orElse("anonymous");

        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getUserId() == null || !account.getUserId().equals(currentUserId)) {
            throw new UnauthorizedAccessException("Account", accountId, currentUsername);
        }
    }

    // Inner classes for holding intermediate data

    private static class WashSaleInfo {
        boolean hasRisk;
        LocalDate lastSaleDate;
        Integer daysUntilClears;

        WashSaleInfo(boolean hasRisk, LocalDate lastSaleDate, Integer daysUntilClears) {
            this.hasRisk = hasRisk;
            this.lastSaleDate = lastSaleDate;
            this.daysUntilClears = daysUntilClears;
        }
    }

    private static class HoldingPeriodInfo {
        LocalDate purchaseDate;
        int holdingDays;
        boolean isLongTerm;

        HoldingPeriodInfo(LocalDate purchaseDate, int holdingDays, boolean isLongTerm) {
            this.purchaseDate = purchaseDate;
            this.holdingDays = holdingDays;
            this.isLongTerm = isLongTerm;
        }
    }
}
