package com.trading.journal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.BacktestRequestDto;
import com.trading.journal.dto.BacktestResultDto;
import com.trading.journal.entity.BacktestResult;
import com.trading.journal.entity.BacktestTrade;
import com.trading.journal.repository.BacktestResultRepository;
import com.trading.journal.strategy.TradingStrategy;
import com.trading.journal.strategy.TradingStrategy.PriceData;
import com.trading.journal.strategy.TradingStrategy.Signal;
import com.trading.journal.strategy.impl.BollingerBandStrategy;
import com.trading.journal.strategy.impl.MomentumStrategy;
import com.trading.journal.strategy.impl.MovingAverageCrossStrategy;
import com.trading.journal.strategy.impl.RSIStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 백테스트 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    private final BacktestResultRepository backtestResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * 백테스트 실행
     */
    @Transactional
    public BacktestResultDto runBacktest(BacktestRequestDto request) {
        long startTime = System.currentTimeMillis();

        // 1. 전략 생성
        TradingStrategy strategy = createStrategy(request);

        // 2. 가격 데이터 생성 (실제로는 외부 API나 DB에서 가져옴)
        List<PriceData> prices = generateSamplePriceData(request.getSymbol(),
                request.getStartDate(), request.getEndDate());

        // 3. 백테스트 시뮬레이션 실행
        BacktestResult result = executeBacktest(request, strategy, prices);

        // 4. 실행 시간 기록
        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        // 5. 결과 저장
        BacktestResult saved = backtestResultRepository.save(result);

        // 6. DTO 변환 및 반환
        return convertToDto(saved, prices);
    }

    /**
     * 전략 생성
     */
    private TradingStrategy createStrategy(BacktestRequestDto request) {
        Map<String, Object> params = request.getStrategyParams();
        if (params == null) {
            params = new HashMap<>();
        }

        switch (request.getStrategyType()) {
            case MOVING_AVERAGE:
                return MovingAverageCrossStrategy.builder()
                        .shortPeriod(getIntParam(params, "shortPeriod", 20))
                        .longPeriod(getIntParam(params, "longPeriod", 60))
                        .maType(getMAType(params, "maType", MovingAverageCrossStrategy.MAType.SMA))
                        .build();

            case RSI:
                return RSIStrategy.builder()
                        .period(getIntParam(params, "period", 14))
                        .overboughtLevel(getIntParam(params, "overboughtLevel", 70))
                        .oversoldLevel(getIntParam(params, "oversoldLevel", 30))
                        .build();

            case BOLLINGER_BAND:
                return BollingerBandStrategy.builder()
                        .period(getIntParam(params, "period", 20))
                        .stdDevMultiplier(getDoubleParam(params, "stdDevMultiplier", 2.0))
                        .build();

            case MOMENTUM:
                return MomentumStrategy.builder()
                        .period(getIntParam(params, "period", 20))
                        .entryThreshold(getDoubleParam(params, "entryThreshold", 0.0))
                        .exitThreshold(getDoubleParam(params, "exitThreshold", 0.0))
                        .build();

            default:
                throw new IllegalArgumentException("지원하지 않는 전략 유형: " + request.getStrategyType());
        }
    }

    /**
     * 백테스트 시뮬레이션 실행
     */
    private BacktestResult executeBacktest(BacktestRequestDto request,
                                           TradingStrategy strategy,
                                           List<PriceData> prices) {
        BigDecimal capital = request.getInitialCapital();
        BigDecimal position = BigDecimal.ZERO;  // 현재 보유 수량
        BigDecimal entryPrice = BigDecimal.ZERO;
        LocalDate entryDate = null;
        String entrySignal = null;

        List<BacktestTrade> trades = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        BigDecimal peakEquity = capital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        int tradeNumber = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        BigDecimal totalWinAmount = BigDecimal.ZERO;
        BigDecimal totalLossAmount = BigDecimal.ZERO;
        int currentWinStreak = 0;
        int currentLossStreak = 0;
        int maxWinStreak = 0;
        int maxLossStreak = 0;

        BigDecimal commissionRate = request.getCommissionRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal slippage = request.getSlippage().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        for (int i = 0; i < prices.size(); i++) {
            PriceData price = prices.get(i);
            BigDecimal currentPrice = price.getClose();

            // 포트폴리오 가치 계산
            BigDecimal portfolioValue = capital.add(position.multiply(currentPrice));
            equityCurve.add(portfolioValue);

            // 최대 낙폭 계산
            if (portfolioValue.compareTo(peakEquity) > 0) {
                peakEquity = portfolioValue;
            }
            BigDecimal drawdown = peakEquity.subtract(portfolioValue)
                    .divide(peakEquity, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }

            // 시그널 생성
            Signal signal = strategy.generateSignal(prices, i);

            // 손절/익절 체크 (보유 중일 때)
            if (position.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentReturn = currentPrice.subtract(entryPrice)
                        .divide(entryPrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                // 손절
                if (request.getStopLossPercent() != null &&
                    currentReturn.compareTo(request.getStopLossPercent().negate()) <= 0) {
                    signal = Signal.SELL;
                }

                // 익절
                if (request.getTakeProfitPercent() != null &&
                    currentReturn.compareTo(request.getTakeProfitPercent()) >= 0) {
                    signal = Signal.SELL;
                }
            }

            // 매수 시그널 (포지션 없을 때)
            if (signal == Signal.BUY && position.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal investAmount = capital.multiply(
                        request.getPositionSizePercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));

                // 슬리피지 적용
                BigDecimal buyPrice = currentPrice.multiply(BigDecimal.ONE.add(slippage));

                // 수수료 차감
                BigDecimal commission = investAmount.multiply(commissionRate);
                BigDecimal netAmount = investAmount.subtract(commission);

                position = netAmount.divide(buyPrice, 4, RoundingMode.DOWN);
                capital = capital.subtract(investAmount);
                entryPrice = buyPrice;
                entryDate = price.getDate();
                entrySignal = strategy.getName();
            }

            // 매도 시그널 (포지션 있을 때)
            else if (signal == Signal.SELL && position.compareTo(BigDecimal.ZERO) > 0) {
                // 슬리피지 적용
                BigDecimal sellPrice = currentPrice.multiply(BigDecimal.ONE.subtract(slippage));

                BigDecimal grossAmount = position.multiply(sellPrice);
                BigDecimal commission = grossAmount.multiply(commissionRate);
                BigDecimal netAmount = grossAmount.subtract(commission);

                // 손익 계산
                BigDecimal investedAmount = position.multiply(entryPrice);
                BigDecimal profit = netAmount.subtract(investedAmount);
                BigDecimal profitPercent = profit.divide(investedAmount, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                // 거래 기록
                tradeNumber++;
                BacktestTrade trade = BacktestTrade.builder()
                        .tradeNumber(tradeNumber)
                        .symbol(request.getSymbol())
                        .entryDate(entryDate)
                        .exitDate(price.getDate())
                        .entryPrice(entryPrice)
                        .exitPrice(sellPrice)
                        .quantity(position)
                        .profit(profit)
                        .profitPercent(profitPercent)
                        .entrySignal(entrySignal)
                        .exitSignal(strategy.getName())
                        .holdingDays((int) ChronoUnit.DAYS.between(entryDate, price.getDate()))
                        .portfolioValueAtEntry(investedAmount.add(capital))
                        .portfolioValueAtExit(capital.add(netAmount))
                        .build();
                trades.add(trade);

                // 통계 업데이트
                if (profit.compareTo(BigDecimal.ZERO) > 0) {
                    winningTrades++;
                    totalWinAmount = totalWinAmount.add(profit);
                    currentWinStreak++;
                    currentLossStreak = 0;
                    maxWinStreak = Math.max(maxWinStreak, currentWinStreak);
                } else {
                    losingTrades++;
                    totalLossAmount = totalLossAmount.add(profit.abs());
                    currentLossStreak++;
                    currentWinStreak = 0;
                    maxLossStreak = Math.max(maxLossStreak, currentLossStreak);
                }

                // 포지션 정리
                capital = capital.add(netAmount);
                position = BigDecimal.ZERO;
                entryPrice = BigDecimal.ZERO;
                entryDate = null;
            }
        }

        // 마지막에 포지션이 남아있으면 청산
        if (position.compareTo(BigDecimal.ZERO) > 0) {
            PriceData lastPrice = prices.get(prices.size() - 1);
            BigDecimal sellPrice = lastPrice.getClose();
            BigDecimal grossAmount = position.multiply(sellPrice);
            capital = capital.add(grossAmount);
            position = BigDecimal.ZERO;
        }

        // 최종 결과 계산
        BigDecimal finalCapital = capital;
        BigDecimal totalReturn = finalCapital.subtract(request.getInitialCapital())
                .divide(request.getInitialCapital(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // CAGR 계산
        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate());
        double years = days / 365.0;
        double cagr = years > 0 ?
                (Math.pow(finalCapital.divide(request.getInitialCapital(), 6, RoundingMode.HALF_UP).doubleValue(),
                        1.0 / years) - 1) * 100 : 0;

        // 승률 계산
        int totalTrades = winningTrades + losingTrades;
        BigDecimal winRate = totalTrades > 0 ?
                BigDecimal.valueOf(winningTrades).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;

        // 평균 손익 계산
        BigDecimal avgWin = winningTrades > 0 ?
                totalWinAmount.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgLoss = losingTrades > 0 ?
                totalLossAmount.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Profit Factor 계산
        BigDecimal profitFactor = totalLossAmount.compareTo(BigDecimal.ZERO) > 0 ?
                totalWinAmount.divide(totalLossAmount, 4, RoundingMode.HALF_UP) : BigDecimal.valueOf(999.99);

        // 평균 보유 기간 계산
        BigDecimal avgHoldingDays = trades.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(trades.stream().mapToInt(BacktestTrade::getHoldingDays).average().orElse(0));

        // 샤프 비율 계산 (간단한 버전)
        BigDecimal sharpeRatio = calculateSharpeRatio(trades, totalReturn, days);

        // 소르티노 비율 계산
        BigDecimal sortinoRatio = calculateSortinoRatio(trades, totalReturn, days);

        // 결과 엔티티 생성
        BacktestResult result = BacktestResult.builder()
                .strategyName(strategy.getName())
                .strategyConfig(toJson(strategy.getParameters()))
                .symbol(request.getSymbol())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .initialCapital(request.getInitialCapital())
                .finalCapital(finalCapital)
                .totalReturn(totalReturn)
                .cagr(BigDecimal.valueOf(cagr))
                .maxDrawdown(maxDrawdown)
                .sharpeRatio(sharpeRatio)
                .sortinoRatio(sortinoRatio)
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .winRate(winRate)
                .avgWin(avgWin)
                .avgLoss(avgLoss)
                .profitFactor(profitFactor)
                .maxWinStreak(maxWinStreak)
                .maxLossStreak(maxLossStreak)
                .avgHoldingDays(avgHoldingDays)
                .trades(new ArrayList<>())
                .build();

        // 거래 연결
        for (BacktestTrade trade : trades) {
            trade.setBacktestResult(result);
            result.getTrades().add(trade);
        }

        return result;
    }

    /**
     * 샤프 비율 계산
     */
    private BigDecimal calculateSharpeRatio(List<BacktestTrade> trades, BigDecimal totalReturn, long days) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> returns = trades.stream()
                .map(BacktestTrade::getProfitPercent)
                .collect(Collectors.toList());

        BigDecimal avgReturn = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);

        BigDecimal sumSquaredDiff = returns.stream()
                .map(r -> r.subtract(avgReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal stdDev = BigDecimal.valueOf(
                Math.sqrt(sumSquaredDiff.divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP).doubleValue()));

        if (stdDev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // 연환산 (무위험 수익률 3% 가정)
        BigDecimal riskFreeRate = BigDecimal.valueOf(3);
        BigDecimal annualizedReturn = totalReturn.multiply(BigDecimal.valueOf(365.0 / days));
        BigDecimal annualizedStdDev = stdDev.multiply(BigDecimal.valueOf(Math.sqrt(252.0 / trades.size())));

        return annualizedReturn.subtract(riskFreeRate)
                .divide(annualizedStdDev, 4, RoundingMode.HALF_UP);
    }

    /**
     * 소르티노 비율 계산
     */
    private BigDecimal calculateSortinoRatio(List<BacktestTrade> trades, BigDecimal totalReturn, long days) {
        if (trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> negativeReturns = trades.stream()
                .map(BacktestTrade::getProfitPercent)
                .filter(r -> r.compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());

        if (negativeReturns.isEmpty()) {
            return BigDecimal.valueOf(999.99);  // 손실이 없으면 매우 높은 값
        }

        BigDecimal downside = negativeReturns.stream()
                .map(r -> r.pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(trades.size()), 6, RoundingMode.HALF_UP);

        BigDecimal downsideDeviation = BigDecimal.valueOf(Math.sqrt(downside.doubleValue()));

        if (downsideDeviation.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(999.99);
        }

        BigDecimal riskFreeRate = BigDecimal.valueOf(3);
        BigDecimal annualizedReturn = totalReturn.multiply(BigDecimal.valueOf(365.0 / days));
        BigDecimal annualizedDownside = downsideDeviation.multiply(BigDecimal.valueOf(Math.sqrt(252.0 / trades.size())));

        return annualizedReturn.subtract(riskFreeRate)
                .divide(annualizedDownside, 4, RoundingMode.HALF_UP);
    }

    /**
     * 샘플 가격 데이터 생성 (테스트용)
     */
    private List<PriceData> generateSamplePriceData(String symbol, LocalDate startDate, LocalDate endDate) {
        List<PriceData> prices = new ArrayList<>();
        Random random = new Random(symbol.hashCode());

        BigDecimal price = BigDecimal.valueOf(100);
        LocalDate date = startDate;

        while (!date.isAfter(endDate)) {
            // 주말 제외
            if (date.getDayOfWeek().getValue() <= 5) {
                // 랜덤 변동 (-3% ~ +3%)
                double change = (random.nextDouble() - 0.48) * 0.06;
                price = price.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(change)));

                BigDecimal high = price.multiply(BigDecimal.valueOf(1 + random.nextDouble() * 0.02));
                BigDecimal low = price.multiply(BigDecimal.valueOf(1 - random.nextDouble() * 0.02));
                BigDecimal open = price.multiply(BigDecimal.valueOf(1 + (random.nextDouble() - 0.5) * 0.01));

                prices.add(PriceData.builder()
                        .date(date)
                        .open(open.setScale(2, RoundingMode.HALF_UP))
                        .high(high.setScale(2, RoundingMode.HALF_UP))
                        .low(low.setScale(2, RoundingMode.HALF_UP))
                        .close(price.setScale(2, RoundingMode.HALF_UP))
                        .volume(100000L + random.nextInt(100000))
                        .build());
            }
            date = date.plusDays(1);
        }

        return prices;
    }

    /**
     * 결과를 DTO로 변환
     */
    private BacktestResultDto convertToDto(BacktestResult result, List<PriceData> prices) {
        List<BacktestResultDto.TradeDto> tradeDtos = result.getTrades().stream()
                .map(trade -> BacktestResultDto.TradeDto.builder()
                        .tradeNumber(trade.getTradeNumber())
                        .symbol(trade.getSymbol())
                        .entryDate(trade.getEntryDate())
                        .exitDate(trade.getExitDate())
                        .entryPrice(trade.getEntryPrice())
                        .exitPrice(trade.getExitPrice())
                        .quantity(trade.getQuantity())
                        .profit(trade.getProfit())
                        .profitPercent(trade.getProfitPercent())
                        .holdingDays(trade.getHoldingDays())
                        .entrySignal(trade.getEntrySignal())
                        .exitSignal(trade.getExitSignal())
                        .portfolioValueAtEntry(trade.getPortfolioValueAtEntry())
                        .portfolioValueAtExit(trade.getPortfolioValueAtExit())
                        .build())
                .collect(Collectors.toList());

        // 월별 성과 계산
        Map<String, BacktestResultDto.MonthlyPerformance> monthlyMap = new LinkedHashMap<>();
        for (BacktestTrade trade : result.getTrades()) {
            String month = trade.getExitDate().toString().substring(0, 7);
            BacktestResultDto.MonthlyPerformance mp = monthlyMap.computeIfAbsent(month,
                    m -> BacktestResultDto.MonthlyPerformance.builder()
                            .month(m)
                            .returnPct(BigDecimal.ZERO)
                            .tradeCount(0)
                            .profit(BigDecimal.ZERO)
                            .build());
            mp.setTradeCount(mp.getTradeCount() + 1);
            mp.setProfit(mp.getProfit().add(trade.getProfit()));
            mp.setReturnPct(mp.getReturnPct().add(trade.getProfitPercent()));
        }

        // Equity curve 및 drawdown 계산
        List<String> equityLabels = new ArrayList<>();
        List<BigDecimal> equityCurve = new ArrayList<>();
        List<BigDecimal> drawdownCurve = new ArrayList<>();
        List<BigDecimal> benchmarkCurve = new ArrayList<>();

        BigDecimal peakEquity = result.getInitialCapital();
        BigDecimal currentEquity = result.getInitialCapital();
        BigDecimal benchmarkStart = prices.isEmpty() ? BigDecimal.valueOf(100) : prices.get(0).getClose();

        for (int i = 0; i < prices.size(); i += 5) {  // 주 단위 샘플링
            PriceData price = prices.get(i);
            equityLabels.add(price.getDate().toString());

            // 간단한 equity 계산 (실제로는 더 정교하게)
            double progress = (double) i / prices.size();
            currentEquity = result.getInitialCapital().add(
                    result.getFinalCapital().subtract(result.getInitialCapital())
                            .multiply(BigDecimal.valueOf(progress)));
            equityCurve.add(currentEquity);

            // Drawdown
            if (currentEquity.compareTo(peakEquity) > 0) {
                peakEquity = currentEquity;
            }
            BigDecimal drawdown = peakEquity.subtract(currentEquity)
                    .divide(peakEquity, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(-100));
            drawdownCurve.add(drawdown);

            // Benchmark (Buy & Hold)
            BigDecimal benchmarkValue = result.getInitialCapital()
                    .multiply(price.getClose())
                    .divide(benchmarkStart, 2, RoundingMode.HALF_UP);
            benchmarkCurve.add(benchmarkValue);
        }

        // Calmar Ratio
        BigDecimal calmarRatio = result.getMaxDrawdown().compareTo(BigDecimal.ZERO) > 0 ?
                result.getCagr().divide(result.getMaxDrawdown(), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return BacktestResultDto.builder()
                .id(result.getId())
                .strategyName(result.getStrategyName())
                .strategyType(extractStrategyType(result.getStrategyName()))
                .strategyConfig(fromJson(result.getStrategyConfig()))
                .symbol(result.getSymbol())
                .startDate(result.getStartDate())
                .endDate(result.getEndDate())
                .initialCapital(result.getInitialCapital())
                .finalCapital(result.getFinalCapital())
                .totalProfit(result.getFinalCapital().subtract(result.getInitialCapital()))
                .totalReturn(result.getTotalReturn())
                .cagr(result.getCagr())
                .maxDrawdown(result.getMaxDrawdown())
                .sharpeRatio(result.getSharpeRatio())
                .sortinoRatio(result.getSortinoRatio())
                .calmarRatio(calmarRatio)
                .totalTrades(result.getTotalTrades())
                .winningTrades(result.getWinningTrades())
                .losingTrades(result.getLosingTrades())
                .winRate(result.getWinRate())
                .avgWin(result.getAvgWin())
                .avgLoss(result.getAvgLoss())
                .profitFactor(result.getProfitFactor())
                .maxWinStreak(result.getMaxWinStreak())
                .maxLossStreak(result.getMaxLossStreak())
                .avgHoldingDays(result.getAvgHoldingDays())
                .trades(tradeDtos)
                .equityLabels(equityLabels)
                .equityCurve(equityCurve)
                .drawdownCurve(drawdownCurve)
                .benchmarkCurve(benchmarkCurve)
                .monthlyPerformance(new ArrayList<>(monthlyMap.values()))
                .executedAt(result.getExecutedAt())
                .executionTimeMs(result.getExecutionTimeMs())
                .build();
    }

    /**
     * 백테스트 히스토리 조회
     */
    @Transactional(readOnly = true)
    public List<BacktestResultDto> getHistory() {
        return backtestResultRepository.findTop20ByOrderByExecutedAtDesc().stream()
                .map(result -> convertToDto(result, new ArrayList<>()))
                .collect(Collectors.toList());
    }

    /**
     * 백테스트 결과 상세 조회
     */
    @Transactional(readOnly = true)
    public BacktestResultDto getResult(Long id) {
        BacktestResult result = backtestResultRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("백테스트 결과를 찾을 수 없습니다: " + id));

        // 가격 데이터 재생성 (차트용)
        List<PriceData> prices = generateSamplePriceData(result.getSymbol(),
                result.getStartDate(), result.getEndDate());

        return convertToDto(result, prices);
    }

    /**
     * 사용 가능한 전략 목록
     */
    public List<Map<String, Object>> getAvailableStrategies() {
        return Arrays.stream(TradingStrategy.StrategyType.values())
                .map(type -> {
                    Map<String, Object> strategy = new HashMap<>();
                    strategy.put("type", type.name());
                    strategy.put("label", type.getLabel());
                    strategy.put("description", type.getDescription());
                    strategy.put("parameters", getDefaultParameters(type));
                    return strategy;
                })
                .collect(Collectors.toList());
    }

    /**
     * 전략별 기본 파라미터
     */
    private Map<String, Object> getDefaultParameters(TradingStrategy.StrategyType type) {
        Map<String, Object> params = new HashMap<>();
        switch (type) {
            case MOVING_AVERAGE:
                params.put("shortPeriod", 20);
                params.put("longPeriod", 60);
                params.put("maType", "SMA");
                break;
            case RSI:
                params.put("period", 14);
                params.put("overboughtLevel", 70);
                params.put("oversoldLevel", 30);
                break;
            case BOLLINGER_BAND:
                params.put("period", 20);
                params.put("stdDevMultiplier", 2.0);
                break;
            case MOMENTUM:
                params.put("period", 20);
                params.put("entryThreshold", 0.0);
                params.put("exitThreshold", 0.0);
                break;
            default:
                break;
        }
        return params;
    }

    // === Helper methods ===

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private MovingAverageCrossStrategy.MAType getMAType(Map<String, Object> params, String key,
                                                         MovingAverageCrossStrategy.MAType defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        return MovingAverageCrossStrategy.MAType.valueOf(value.toString());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String extractStrategyType(String strategyName) {
        if (strategyName.contains("MA")) return "MOVING_AVERAGE";
        if (strategyName.contains("RSI")) return "RSI";
        if (strategyName.contains("Bollinger")) return "BOLLINGER_BAND";
        if (strategyName.contains("Momentum")) return "MOMENTUM";
        return "CUSTOM";
    }
}
