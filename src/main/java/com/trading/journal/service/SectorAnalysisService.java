package com.trading.journal.service;

import com.trading.journal.dto.SectorAnalysisDto;
import com.trading.journal.dto.SectorAnalysisDto.*;
import com.trading.journal.entity.*;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SectorAnalysisService {

    private final TransactionRepository transactionRepository;
    private final StockRepository stockRepository;

    /**
     * 섹터별 종합 분석
     */
    @Cacheable(value = "sectorAnalysis", key = "#accountId + '_' + #startDate + '_' + #endDate")
    public SectorAnalysisDto analyzeSectors(Long accountId, LocalDate startDate, LocalDate endDate) {
        log.info("Analyzing sectors from {} to {} for account {}", startDate, endDate, accountId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        // 기간 내 거래 조회
        List<Transaction> transactions = transactionRepository.findByDateRange(startDateTime, endDateTime);

        if (transactions.isEmpty()) {
            return buildEmptyAnalysis(startDate, endDate);
        }

        // 종목별 포지션 계산
        Map<Stock, PositionInfo> positionMap = calculatePositions(transactions);

        // 섹터별 배분 계산
        List<SectorAllocation> allocations = calculateSectorAllocations(positionMap);

        // 섹터별 성과 계산
        List<SectorPerformance> performances = calculateSectorPerformance(transactions, positionMap);

        // 섹터 로테이션 히스토리
        List<SectorRotation> rotations = calculateSectorRotation(transactions);

        // 통계 계산
        int totalStocks = positionMap.size();
        int classifiedStocks = (int) positionMap.keySet().stream()
                .filter(s -> s.getSector() != null)
                .count();
        int unclassifiedStocks = totalStocks - classifiedStocks;

        // 최고/최저 성과 섹터
        Sector topSector = performances.stream()
                .max(Comparator.comparing(SectorPerformance::getTotalReturn))
                .map(SectorPerformance::getSector)
                .orElse(null);

        Sector worstSector = performances.stream()
                .min(Comparator.comparing(SectorPerformance::getTotalReturn))
                .map(SectorPerformance::getSector)
                .orElse(null);

        // 섹터 집중도 (HHI)
        BigDecimal hhi = calculateHHI(allocations);
        String diversificationRating = getDiversificationRating(hhi);

        return SectorAnalysisDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .currentAllocation(allocations)
                .sectorPerformance(performances)
                .rotationHistory(rotations)
                .totalStocks(totalStocks)
                .classifiedStocks(classifiedStocks)
                .unclassifiedStocks(unclassifiedStocks)
                .topPerformingSector(topSector)
                .worstPerformingSector(worstSector)
                .sectorConcentrationIndex(hhi)
                .diversificationRating(diversificationRating)
                .build();
    }

    /**
     * 종목 섹터 업데이트
     */
    @Transactional
    public Stock updateStockSector(Long stockId, Sector sector, String industry) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found: " + stockId));

        stock.setSector(sector);
        stock.setIndustry(industry);

        return stockRepository.save(stock);
    }

    /**
     * 모든 섹터 목록 조회
     */
    public List<SectorOption> getAllSectors() {
        return Arrays.stream(Sector.values())
                .map(s -> SectorOption.builder()
                        .value(s.name())
                        .label(s.getLabel())
                        .labelEn(s.getLabelEn())
                        .description(s.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    // === Private Helper Methods ===

    private Map<Stock, PositionInfo> calculatePositions(List<Transaction> transactions) {
        Map<Stock, PositionInfo> positionMap = new HashMap<>();

        for (Transaction tx : transactions) {
            Stock stock = tx.getStock();
            PositionInfo position = positionMap.computeIfAbsent(stock, k -> new PositionInfo());

            if (tx.getType() == TransactionType.BUY) {
                position.quantity = position.quantity.add(tx.getQuantity());
                position.totalCost = position.totalCost.add(tx.getTotalAmount());
            } else if (tx.getType() == TransactionType.SELL) {
                position.quantity = position.quantity.subtract(tx.getQuantity());
                position.realizedPnl = position.realizedPnl.add(
                        tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO);
            }
        }

        // 현재가 추정 (마지막 거래 가격 사용)
        for (Transaction tx : transactions) {
            Stock stock = tx.getStock();
            PositionInfo position = positionMap.get(stock);
            if (position != null && tx.getPrice() != null) {
                position.lastPrice = tx.getPrice();
            }
        }

        // 현재 가치 및 미실현 손익 계산
        for (Map.Entry<Stock, PositionInfo> entry : positionMap.entrySet()) {
            PositionInfo position = entry.getValue();
            if (position.quantity.compareTo(BigDecimal.ZERO) > 0 && position.lastPrice != null) {
                position.currentValue = position.quantity.multiply(position.lastPrice);
                BigDecimal avgCost = position.totalCost.divide(position.quantity, 4, RoundingMode.HALF_UP);
                position.unrealizedPnl = position.quantity.multiply(position.lastPrice.subtract(avgCost));
            }
        }

        return positionMap;
    }

    private List<SectorAllocation> calculateSectorAllocations(Map<Stock, PositionInfo> positionMap) {
        Map<Sector, List<StockInSector>> sectorStocks = new LinkedHashMap<>();
        Map<Sector, BigDecimal> sectorValues = new HashMap<>();

        // 섹터별 종목 그룹화
        for (Map.Entry<Stock, PositionInfo> entry : positionMap.entrySet()) {
            Stock stock = entry.getKey();
            PositionInfo position = entry.getValue();

            if (position.currentValue == null || position.currentValue.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Sector sector = stock.getSector() != null ? stock.getSector() : Sector.OTHER;

            StockInSector stockInfo = StockInSector.builder()
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
                    .currentValue(position.currentValue)
                    .profitLoss(position.unrealizedPnl)
                    .profitLossPercent(position.totalCost.compareTo(BigDecimal.ZERO) > 0
                            ? position.unrealizedPnl.divide(position.totalCost, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO)
                    .build();

            sectorStocks.computeIfAbsent(sector, k -> new ArrayList<>()).add(stockInfo);
            sectorValues.merge(sector, position.currentValue, BigDecimal::add);
        }

        // 전체 가치
        BigDecimal totalValue = sectorValues.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 배분 리스트 생성
        List<SectorAllocation> allocations = new ArrayList<>();

        for (Sector sector : Sector.values()) {
            List<StockInSector> stocks = sectorStocks.getOrDefault(sector, new ArrayList<>());
            BigDecimal value = sectorValues.getOrDefault(sector, BigDecimal.ZERO);
            BigDecimal weight = totalValue.compareTo(BigDecimal.ZERO) > 0
                    ? value.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            if (value.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(SectorAllocation.builder()
                        .sector(sector)
                        .sectorLabel(sector.getLabel())
                        .value(value.setScale(0, RoundingMode.HALF_UP))
                        .weight(weight.setScale(2, RoundingMode.HALF_UP))
                        .stockCount(stocks.size())
                        .stocks(stocks)
                        .build());
            }
        }

        // 비중순 정렬
        allocations.sort((a, b) -> b.getWeight().compareTo(a.getWeight()));

        return allocations;
    }

    private List<SectorPerformance> calculateSectorPerformance(
            List<Transaction> transactions, Map<Stock, PositionInfo> positionMap) {

        Map<Sector, SectorStats> sectorStats = new HashMap<>();

        // 매도 거래만 성과 계산
        List<Transaction> sellTransactions = transactions.stream()
                .filter(t -> t.getType() == TransactionType.SELL)
                .collect(Collectors.toList());

        for (Transaction tx : sellTransactions) {
            Sector sector = tx.getStock().getSector() != null ? tx.getStock().getSector() : Sector.OTHER;
            SectorStats stats = sectorStats.computeIfAbsent(sector, k -> new SectorStats());

            BigDecimal pnl = tx.getRealizedPnl() != null ? tx.getRealizedPnl() : BigDecimal.ZERO;
            stats.realizedPnl = stats.realizedPnl.add(pnl);
            stats.tradeCount++;

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                stats.winCount++;
                if (stats.bestTrade == null || pnl.compareTo(stats.bestTrade) > 0) {
                    stats.bestTrade = pnl;
                }
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                stats.lossCount++;
                if (stats.worstTrade == null || pnl.compareTo(stats.worstTrade) < 0) {
                    stats.worstTrade = pnl;
                }
            }
        }

        // 미실현 손익 추가
        for (Map.Entry<Stock, PositionInfo> entry : positionMap.entrySet()) {
            Sector sector = entry.getKey().getSector() != null ? entry.getKey().getSector() : Sector.OTHER;
            SectorStats stats = sectorStats.computeIfAbsent(sector, k -> new SectorStats());

            if (entry.getValue().unrealizedPnl != null) {
                stats.unrealizedPnl = stats.unrealizedPnl.add(entry.getValue().unrealizedPnl);
            }
        }

        // 전체 실현손익
        BigDecimal totalRealizedPnl = sectorStats.values().stream()
                .map(s -> s.realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 성과 리스트 생성
        List<SectorPerformance> performances = new ArrayList<>();

        for (Map.Entry<Sector, SectorStats> entry : sectorStats.entrySet()) {
            Sector sector = entry.getKey();
            SectorStats stats = entry.getValue();

            BigDecimal totalReturn = stats.realizedPnl.add(stats.unrealizedPnl);
            BigDecimal winRate = stats.tradeCount > 0
                    ? BigDecimal.valueOf(stats.winCount).divide(BigDecimal.valueOf(stats.tradeCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            BigDecimal contribution = totalRealizedPnl.compareTo(BigDecimal.ZERO) != 0
                    ? stats.realizedPnl.divide(totalRealizedPnl.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            performances.add(SectorPerformance.builder()
                    .sector(sector)
                    .sectorLabel(sector.getLabel())
                    .totalReturn(totalReturn.setScale(0, RoundingMode.HALF_UP))
                    .realizedPnl(stats.realizedPnl.setScale(0, RoundingMode.HALF_UP))
                    .unrealizedPnl(stats.unrealizedPnl.setScale(0, RoundingMode.HALF_UP))
                    .contribution(contribution.setScale(2, RoundingMode.HALF_UP))
                    .winRate(winRate.setScale(1, RoundingMode.HALF_UP))
                    .tradeCount(stats.tradeCount)
                    .winCount(stats.winCount)
                    .lossCount(stats.lossCount)
                    .bestTrade(stats.bestTrade != null ? stats.bestTrade.setScale(0, RoundingMode.HALF_UP) : null)
                    .worstTrade(stats.worstTrade != null ? stats.worstTrade.setScale(0, RoundingMode.HALF_UP) : null)
                    .build());
        }

        // 총수익 순 정렬
        performances.sort((a, b) -> b.getTotalReturn().compareTo(a.getTotalReturn()));

        return performances;
    }

    private List<SectorRotation> calculateSectorRotation(List<Transaction> transactions) {
        Map<YearMonth, Map<Sector, BigDecimal>> monthlyPositions = new TreeMap<>();

        // 월별 포지션 가치 추적
        Map<Stock, BigDecimal> runningPositions = new HashMap<>();

        transactions.sort(Comparator.comparing(Transaction::getTransactionDate));

        for (Transaction tx : transactions) {
            Stock stock = tx.getStock();
            BigDecimal qty = runningPositions.getOrDefault(stock, BigDecimal.ZERO);

            if (tx.getType() == TransactionType.BUY) {
                qty = qty.add(tx.getQuantity());
            } else if (tx.getType() == TransactionType.SELL) {
                qty = qty.subtract(tx.getQuantity());
            }

            runningPositions.put(stock, qty);

            YearMonth ym = YearMonth.from(tx.getTransactionDate().toLocalDate());
            Map<Sector, BigDecimal> sectorValues = monthlyPositions.computeIfAbsent(ym, k -> new HashMap<>());

            // 월말 기준 섹터별 가치 계산
            for (Map.Entry<Stock, BigDecimal> entry : runningPositions.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                    Sector sector = entry.getKey().getSector() != null ? entry.getKey().getSector() : Sector.OTHER;
                    BigDecimal value = entry.getValue().multiply(tx.getPrice());
                    sectorValues.merge(sector, value, BigDecimal::add);
                }
            }
        }

        // 로테이션 리스트 생성
        List<SectorRotation> rotations = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (Map.Entry<YearMonth, Map<Sector, BigDecimal>> entry : monthlyPositions.entrySet()) {
            Map<Sector, BigDecimal> sectorValues = entry.getValue();
            BigDecimal total = sectorValues.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

            if (total.compareTo(BigDecimal.ZERO) == 0) continue;

            Map<Sector, BigDecimal> weights = new HashMap<>();
            Sector dominant = null;
            BigDecimal maxWeight = BigDecimal.ZERO;

            for (Map.Entry<Sector, BigDecimal> sv : sectorValues.entrySet()) {
                BigDecimal weight = sv.getValue().divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                weights.put(sv.getKey(), weight.setScale(2, RoundingMode.HALF_UP));

                if (weight.compareTo(maxWeight) > 0) {
                    maxWeight = weight;
                    dominant = sv.getKey();
                }
            }

            rotations.add(SectorRotation.builder()
                    .period(entry.getKey().format(formatter))
                    .sectorWeights(weights)
                    .dominantSector(dominant)
                    .dominantWeight(maxWeight.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return rotations;
    }

    private BigDecimal calculateHHI(List<SectorAllocation> allocations) {
        // HHI = sum of squared weights
        BigDecimal hhi = BigDecimal.ZERO;
        for (SectorAllocation alloc : allocations) {
            BigDecimal weight = alloc.getWeight().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            hhi = hhi.add(weight.pow(2));
        }
        return hhi.multiply(BigDecimal.valueOf(10000)).setScale(0, RoundingMode.HALF_UP);
    }

    private String getDiversificationRating(BigDecimal hhi) {
        if (hhi == null) return "분석불가";

        double hhiValue = hhi.doubleValue();
        if (hhiValue < 1500) return "우수 (분산투자)";
        if (hhiValue < 2500) return "보통";
        if (hhiValue < 4000) return "집중";
        return "매우 집중";
    }

    private SectorAnalysisDto buildEmptyAnalysis(LocalDate startDate, LocalDate endDate) {
        return SectorAnalysisDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .currentAllocation(new ArrayList<>())
                .sectorPerformance(new ArrayList<>())
                .rotationHistory(new ArrayList<>())
                .totalStocks(0)
                .classifiedStocks(0)
                .unclassifiedStocks(0)
                .sectorConcentrationIndex(BigDecimal.ZERO)
                .diversificationRating("분석불가")
                .build();
    }

    // Helper classes
    private static class PositionInfo {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal lastPrice;
    }

    private static class SectorStats {
        BigDecimal realizedPnl = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        int tradeCount = 0;
        int winCount = 0;
        int lossCount = 0;
        BigDecimal bestTrade;
        BigDecimal worstTrade;
    }
}
