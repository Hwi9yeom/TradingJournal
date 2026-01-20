package com.trading.journal.service;

import com.trading.journal.dto.AccountRiskSettingsDto;
import com.trading.journal.dto.PositionSizingRequestDto;
import com.trading.journal.dto.PositionSizingResultDto;
import com.trading.journal.dto.PositionSizingResultDto.PositionScenario;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PositionSizingService {

    private final AccountRiskSettingsService riskSettingsService;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;

    /** 포지션 사이징 계산 */
    public PositionSizingResultDto calculatePositionSize(PositionSizingRequestDto request) {
        // 계정 설정 조회
        Long accountId = request.getAccountId();
        if (accountId == null) {
            accountId = accountService.getDefaultAccount().getId();
        }

        AccountRiskSettingsDto settings = riskSettingsService.getRiskSettings(accountId);

        // 자본금 결정 (요청 > 설정 > 기본값)
        BigDecimal capital = request.getAccountCapital();
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            capital = settings.getAccountCapital();
        }
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Account capital must be set for position sizing");
        }

        // 리스크 % 결정
        BigDecimal riskPercent = request.getRiskPercent();
        if (riskPercent == null) {
            riskPercent = settings.getMaxRiskPerTradePercent();
        }

        // 주당 리스크 계산
        BigDecimal riskPerShare =
                request.getEntryPrice().subtract(request.getStopLossPrice()).abs();
        if (riskPerShare.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Risk per share cannot be zero (entry = stop loss)");
        }

        // 최대 리스크 금액
        BigDecimal maxRiskAmount =
                capital.multiply(riskPercent)
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        // 추천 수량 계산
        BigDecimal recommendedQuantity = maxRiskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);

        // 포지션 가치
        BigDecimal positionValue = recommendedQuantity.multiply(request.getEntryPrice());
        BigDecimal positionPercent =
                positionValue
                        .divide(capital, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

        // 한도 체크
        boolean exceedsMaxPosition =
                positionPercent.compareTo(settings.getMaxPositionSizePercent()) > 0;
        boolean exceedsMaxConcentration =
                positionPercent.compareTo(settings.getMaxStockConcentrationPercent()) > 0;

        // 한도 내 최대 수량
        BigDecimal maxPositionValue =
                capital.multiply(settings.getMaxPositionSizePercent())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal maxAllowedQuantity =
                maxPositionValue.divide(request.getEntryPrice(), 0, RoundingMode.DOWN);

        // 실제 추천 수량 (한도 적용)
        if (exceedsMaxPosition) {
            recommendedQuantity = maxAllowedQuantity;
            positionValue = recommendedQuantity.multiply(request.getEntryPrice());
            positionPercent =
                    positionValue
                            .divide(capital, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
        }

        // R:R 계산
        BigDecimal riskRewardRatio = null;
        BigDecimal potentialProfit = null;
        if (request.getTakeProfitPrice() != null) {
            BigDecimal rewardPerShare =
                    request.getTakeProfitPrice().subtract(request.getEntryPrice()).abs();
            riskRewardRatio = rewardPerShare.divide(riskPerShare, 4, RoundingMode.HALF_UP);
            potentialProfit = rewardPerShare.multiply(recommendedQuantity);
        }

        BigDecimal potentialLoss = riskPerShare.multiply(recommendedQuantity);

        // Kelly Criterion 계산
        BigDecimal kellyPercentage =
                calculateKellyPercentage(
                        accountId, LocalDate.now().minusMonths(6), LocalDate.now());
        BigDecimal kellyFraction =
                settings.getKellyFraction() != null
                        ? settings.getKellyFraction()
                        : new BigDecimal("0.50");

        BigDecimal fullKellyQuantity = null;
        BigDecimal halfKellyQuantity = null;
        BigDecimal quarterKellyQuantity = null;

        if (kellyPercentage != null && kellyPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal kellyRiskAmount =
                    capital.multiply(kellyPercentage)
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            fullKellyQuantity = kellyRiskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
            halfKellyQuantity =
                    fullKellyQuantity.divide(BigDecimal.valueOf(2), 0, RoundingMode.DOWN);
            quarterKellyQuantity =
                    fullKellyQuantity.divide(BigDecimal.valueOf(4), 0, RoundingMode.DOWN);
        }

        // 시나리오 생성
        List<PositionScenario> scenarios =
                generateScenarios(
                        capital,
                        request.getEntryPrice(),
                        riskPerShare,
                        request.getTakeProfitPrice());

        // 경고 메시지 생성
        String warningMessage = null;
        if (exceedsMaxPosition) {
            warningMessage =
                    String.format(
                            "Position size reduced to %.0f shares due to max position limit (%.1f%%)",
                            maxAllowedQuantity, settings.getMaxPositionSizePercent());
        }

        return PositionSizingResultDto.builder()
                .accountCapital(capital)
                .entryPrice(request.getEntryPrice())
                .stopLossPrice(request.getStopLossPrice())
                .takeProfitPrice(request.getTakeProfitPrice())
                .method(request.getMethod())
                .riskPerShare(riskPerShare)
                .riskPercent(riskPercent)
                .maxRiskAmount(maxRiskAmount)
                .recommendedQuantity(recommendedQuantity)
                .recommendedPositionValue(positionValue)
                .recommendedPositionPercent(positionPercent)
                .kellyPercentage(kellyPercentage)
                .fullKellyQuantity(fullKellyQuantity)
                .halfKellyQuantity(halfKellyQuantity)
                .quarterKellyQuantity(quarterKellyQuantity)
                .riskRewardRatio(riskRewardRatio)
                .potentialLoss(potentialLoss)
                .potentialProfit(potentialProfit)
                .exceedsMaxPositionSize(exceedsMaxPosition)
                .exceedsMaxStockConcentration(exceedsMaxConcentration)
                .maxAllowedQuantity(maxAllowedQuantity)
                .warningMessage(warningMessage)
                .scenarios(scenarios)
                .build();
    }

    /** Kelly Criterion 계산 Kelly % = W - (1-W)/R W: 승률, R: 평균이익/평균손실 */
    public BigDecimal calculateKellyPercentage(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        // SELL 거래 조회 (실현 손익이 있는 것)
        List<Transaction> sellTransactions =
                transactionRepository.findSellTransactionsWithRMultiple(accountId, start, end);

        if (sellTransactions.isEmpty()) {
            // 데이터가 없으면 R-multiple이 아닌 realizedPnl로 계산
            return calculateKellyFromPnl(accountId, start, end);
        }

        int wins = 0;
        BigDecimal totalWinR = BigDecimal.ZERO;
        BigDecimal totalLossR = BigDecimal.ZERO;
        int lossCount = 0;

        for (Transaction t : sellTransactions) {
            if (t.getRMultiple() != null) {
                if (t.getRMultiple().compareTo(BigDecimal.ZERO) > 0) {
                    wins++;
                    totalWinR = totalWinR.add(t.getRMultiple());
                } else if (t.getRMultiple().compareTo(BigDecimal.ZERO) < 0) {
                    lossCount++;
                    totalLossR = totalLossR.add(t.getRMultiple().abs());
                }
            }
        }

        int totalTrades = wins + lossCount;
        if (totalTrades == 0 || lossCount == 0) {
            return null;
        }

        // W: 승률
        BigDecimal winRate =
                BigDecimal.valueOf(wins)
                        .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        // R: 평균이익 / 평균손실
        BigDecimal avgWin =
                totalWinR.divide(BigDecimal.valueOf(wins > 0 ? wins : 1), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss =
                totalLossR.divide(BigDecimal.valueOf(lossCount), 4, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal R = avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);

        // Kelly = W - (1-W)/R
        BigDecimal loseRate = BigDecimal.ONE.subtract(winRate);
        BigDecimal kelly = winRate.subtract(loseRate.divide(R, 4, RoundingMode.HALF_UP));

        // 음수 Kelly는 거래하지 말라는 의미
        if (kelly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Kelly를 % 단위로 변환 (100 곱하기)
        return kelly.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 실현 손익 기반 Kelly 계산 (R-multiple 없을 때) */
    private BigDecimal calculateKellyFromPnl(
            Long accountId, LocalDateTime start, LocalDateTime end) {
        List<Transaction> sellTransactions =
                transactionRepository.findByAccountIdAndTypeAndDateRange(
                        accountId, TransactionType.SELL, start, end);

        if (sellTransactions.isEmpty()) {
            return null;
        }

        int wins = 0;
        int losses = 0;
        BigDecimal totalWin = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        for (Transaction t : sellTransactions) {
            if (t.getRealizedPnl() != null) {
                if (t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0) {
                    wins++;
                    totalWin = totalWin.add(t.getRealizedPnl());
                } else if (t.getRealizedPnl().compareTo(BigDecimal.ZERO) < 0) {
                    losses++;
                    totalLoss = totalLoss.add(t.getRealizedPnl().abs());
                }
            }
        }

        int totalTrades = wins + losses;
        if (totalTrades == 0 || losses == 0 || wins == 0) {
            return null;
        }

        BigDecimal winRate =
                BigDecimal.valueOf(wins)
                        .divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP);

        BigDecimal avgWin = totalWin.divide(BigDecimal.valueOf(wins), 4, RoundingMode.HALF_UP);
        BigDecimal avgLoss = totalLoss.divide(BigDecimal.valueOf(losses), 4, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal R = avgWin.divide(avgLoss, 4, RoundingMode.HALF_UP);
        BigDecimal loseRate = BigDecimal.ONE.subtract(winRate);
        BigDecimal kelly = winRate.subtract(loseRate.divide(R, 4, RoundingMode.HALF_UP));

        if (kelly.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return kelly.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 시나리오 생성 (보수적/일반/공격적) */
    private List<PositionScenario> generateScenarios(
            BigDecimal capital,
            BigDecimal entryPrice,
            BigDecimal riskPerShare,
            BigDecimal takeProfitPrice) {
        List<PositionScenario> scenarios = new ArrayList<>();

        BigDecimal rewardPerShare =
                takeProfitPrice != null ? takeProfitPrice.subtract(entryPrice).abs() : null;

        // 보수적 (1%)
        scenarios.add(
                createScenario(
                        "보수적 (1%)",
                        new BigDecimal("1.0"), capital, entryPrice, riskPerShare, rewardPerShare));

        // 일반 (2%)
        scenarios.add(
                createScenario(
                        "일반 (2%)",
                        new BigDecimal("2.0"), capital, entryPrice, riskPerShare, rewardPerShare));

        // 적극적 (3%)
        scenarios.add(
                createScenario(
                        "적극적 (3%)",
                        new BigDecimal("3.0"), capital, entryPrice, riskPerShare, rewardPerShare));

        // 공격적 (5%)
        scenarios.add(
                createScenario(
                        "공격적 (5%)",
                        new BigDecimal("5.0"), capital, entryPrice, riskPerShare, rewardPerShare));

        return scenarios;
    }

    private PositionScenario createScenario(
            String name,
            BigDecimal riskPercent,
            BigDecimal capital,
            BigDecimal entryPrice,
            BigDecimal riskPerShare,
            BigDecimal rewardPerShare) {
        BigDecimal maxRisk =
                capital.multiply(riskPercent)
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal quantity = maxRisk.divide(riskPerShare, 0, RoundingMode.DOWN);
        BigDecimal positionValue = quantity.multiply(entryPrice);
        BigDecimal potentialLoss = riskPerShare.multiply(quantity);
        BigDecimal potentialProfit =
                rewardPerShare != null ? rewardPerShare.multiply(quantity) : null;

        return PositionScenario.builder()
                .name(name)
                .riskPercent(riskPercent)
                .quantity(quantity)
                .positionValue(positionValue)
                .potentialLoss(potentialLoss)
                .potentialProfit(potentialProfit)
                .build();
    }
}
