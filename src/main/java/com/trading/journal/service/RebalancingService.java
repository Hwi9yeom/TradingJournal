package com.trading.journal.service;

import com.trading.journal.dto.RebalancingDashboardDto;
import com.trading.journal.dto.RebalancingDashboardDto.*;
import com.trading.journal.dto.TargetAllocationBatchDto;
import com.trading.journal.dto.TargetAllocationDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Sector;
import com.trading.journal.entity.Stock;
import com.trading.journal.entity.TargetAllocation;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.StockRepository;
import com.trading.journal.repository.TargetAllocationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포트폴리오 리밸런싱 서비스 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RebalancingService {

    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int DECIMAL_SCALE = 4;

    private static final String ACTION_BUY = "BUY";
    private static final String ACTION_SELL = "SELL";

    private final TargetAllocationRepository targetAllocationRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final AccountService accountService;
    private final StockPriceService stockPriceService;

    // ===== 목표 배분 CRUD =====

    /** 목표 배분 목록 조회 */
    public List<TargetAllocationDto> getTargetAllocations(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);
        return targetAllocationRepository.findByAccountIdWithStock(targetAccountId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /** 목표 배분 단건 조회 */
    public TargetAllocationDto getTargetAllocation(Long id) {
        TargetAllocation allocation =
                targetAllocationRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("목표 배분을 찾을 수 없습니다: " + id));
        return convertToDto(allocation);
    }

    /** 목표 배분 생성 */
    @Transactional
    @CacheEvict(value = "rebalancing", allEntries = true)
    public TargetAllocationDto createTargetAllocation(TargetAllocationDto dto) {
        Long targetAccountId = resolveAccountId(dto.getAccountId());
        Account account = accountService.getAccountEntity(targetAccountId);

        Stock stock =
                stockRepository
                        .findById(dto.getStockId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "종목을 찾을 수 없습니다: " + dto.getStockId()));

        // 중복 체크
        if (targetAllocationRepository.existsByAccountIdAndStockId(
                targetAccountId, stock.getId())) {
            throw new IllegalArgumentException("이미 해당 종목에 대한 목표 배분이 존재합니다: " + stock.getSymbol());
        }

        // 목표 합계 체크
        BigDecimal currentTotal =
                targetAllocationRepository.sumTargetPercentByAccountId(targetAccountId);
        BigDecimal newTotal = currentTotal.add(dto.getTargetPercent());
        if (newTotal.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "목표 배분율 합계가 100%%를 초과합니다. 현재: %.2f%%, 추가: %.2f%%",
                            currentTotal, dto.getTargetPercent()));
        }

        TargetAllocation allocation =
                TargetAllocation.builder()
                        .account(account)
                        .stock(stock)
                        .targetPercent(dto.getTargetPercent())
                        .driftThresholdPercent(
                                dto.getDriftThresholdPercent() != null
                                        ? dto.getDriftThresholdPercent()
                                        : BigDecimal.valueOf(5))
                        .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                        .priority(dto.getPriority() != null ? dto.getPriority() : 0)
                        .notes(dto.getNotes())
                        .build();

        TargetAllocation saved = targetAllocationRepository.save(allocation);
        log.info(
                "목표 배분 생성: accountId={}, stock={}, target={}%",
                targetAccountId, stock.getSymbol(), dto.getTargetPercent());
        return convertToDto(saved);
    }

    /** 목표 배분 수정 */
    @Transactional
    @CacheEvict(value = "rebalancing", allEntries = true)
    public TargetAllocationDto updateTargetAllocation(Long id, TargetAllocationDto dto) {
        TargetAllocation allocation =
                targetAllocationRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("목표 배분을 찾을 수 없습니다: " + id));

        // 목표 합계 체크 (자신 제외)
        BigDecimal currentTotal =
                targetAllocationRepository.sumTargetPercentExcluding(
                        allocation.getAccount().getId(), id);
        BigDecimal newTotal = currentTotal.add(dto.getTargetPercent());
        if (newTotal.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "목표 배분율 합계가 100%%를 초과합니다. 현재(자신제외): %.2f%%, 변경: %.2f%%",
                            currentTotal, dto.getTargetPercent()));
        }

        allocation.setTargetPercent(dto.getTargetPercent());
        if (dto.getDriftThresholdPercent() != null) {
            allocation.setDriftThresholdPercent(dto.getDriftThresholdPercent());
        }
        if (dto.getIsActive() != null) {
            allocation.setIsActive(dto.getIsActive());
        }
        if (dto.getPriority() != null) {
            allocation.setPriority(dto.getPriority());
        }
        allocation.setNotes(dto.getNotes());

        TargetAllocation saved = targetAllocationRepository.save(allocation);
        log.info("목표 배분 수정: id={}, target={}%", id, dto.getTargetPercent());
        return convertToDto(saved);
    }

    /** 목표 배분 삭제 */
    @Transactional
    @CacheEvict(value = "rebalancing", allEntries = true)
    public void deleteTargetAllocation(Long id) {
        if (!targetAllocationRepository.existsById(id)) {
            throw new EntityNotFoundException("목표 배분을 찾을 수 없습니다: " + id);
        }
        targetAllocationRepository.deleteById(id);
        log.info("목표 배분 삭제: id={}", id);
    }

    /** 배치 설정 */
    @Transactional
    @CacheEvict(value = "rebalancing", allEntries = true)
    public List<TargetAllocationDto> batchSetAllocations(TargetAllocationBatchDto batchDto) {
        Long targetAccountId = resolveAccountId(batchDto.getAccountId());
        Account account = accountService.getAccountEntity(targetAccountId);

        // 합계 검증
        BigDecimal totalPercent =
                batchDto.getAllocations().stream()
                        .map(TargetAllocationDto::getTargetPercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    String.format("목표 배분율 합계가 100%%를 초과합니다: %.2f%%", totalPercent));
        }

        // 기존 데이터 삭제 (replaceExisting가 true인 경우)
        if (Boolean.TRUE.equals(batchDto.getReplaceExisting())) {
            targetAllocationRepository.deleteByAccountId(targetAccountId);
            log.info("기존 목표 배분 삭제: accountId={}", targetAccountId);
        }

        List<TargetAllocationDto> results = new ArrayList<>();
        for (TargetAllocationDto dto : batchDto.getAllocations()) {
            Stock stock =
                    stockRepository
                            .findById(dto.getStockId())
                            .orElseThrow(
                                    () ->
                                            new EntityNotFoundException(
                                                    "종목을 찾을 수 없습니다: " + dto.getStockId()));

            // 기존 배분 확인
            Optional<TargetAllocation> existing =
                    targetAllocationRepository.findByAccountIdAndStockId(
                            targetAccountId, stock.getId());

            TargetAllocation allocation;
            if (existing.isPresent()) {
                allocation = existing.get();
                allocation.setTargetPercent(dto.getTargetPercent());
                allocation.setDriftThresholdPercent(
                        dto.getDriftThresholdPercent() != null
                                ? dto.getDriftThresholdPercent()
                                : allocation.getDriftThresholdPercent());
                allocation.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                allocation.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
                allocation.setNotes(dto.getNotes());
            } else {
                allocation =
                        TargetAllocation.builder()
                                .account(account)
                                .stock(stock)
                                .targetPercent(dto.getTargetPercent())
                                .driftThresholdPercent(
                                        dto.getDriftThresholdPercent() != null
                                                ? dto.getDriftThresholdPercent()
                                                : BigDecimal.valueOf(5))
                                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                                .priority(dto.getPriority() != null ? dto.getPriority() : 0)
                                .notes(dto.getNotes())
                                .build();
            }

            results.add(convertToDto(targetAllocationRepository.save(allocation)));
        }

        log.info("배치 목표 배분 설정: accountId={}, count={}", targetAccountId, results.size());
        return results;
    }

    /** 목표 배분율 합계 검증 */
    public Map<String, Object> validateAllocations(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);
        BigDecimal total = targetAllocationRepository.sumTargetPercentByAccountId(targetAccountId);

        Map<String, Object> result = new HashMap<>();
        result.put("totalPercent", total);
        result.put("unallocatedPercent", ONE_HUNDRED.subtract(total));
        result.put("isValid", total.compareTo(ONE_HUNDRED) <= 0);
        result.put("isComplete", total.compareTo(ONE_HUNDRED) == 0);
        return result;
    }

    // ===== 리밸런싱 대시보드 =====

    /** 리밸런싱 대시보드 조회 */
    @Cacheable(value = "rebalancing", key = "'dashboard_' + #accountId")
    public RebalancingDashboardDto getRebalancingDashboard(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        // 포트폴리오 조회
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(targetAccountId);

        // 목표 배분 조회
        List<TargetAllocation> allocations =
                targetAllocationRepository.findByAccountIdWithStock(targetAccountId);

        // 총 포트폴리오 가치 계산
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);
        BigDecimal totalPortfolioValue = calculateTotalValue(portfolios, priceMap);

        // 목표 배분율 합계
        BigDecimal totalTargetPercent =
                allocations.stream()
                        .map(TargetAllocation::getTargetPercent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 포지션별 분석
        List<PositionRebalanceAnalysis> positionAnalyses =
                buildPositionAnalyses(portfolios, allocations, priceMap, totalPortfolioValue);

        // 목표는 있지만 포지션이 없는 종목도 분석에 추가
        Set<Long> positionStockIds =
                portfolios.stream()
                        .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .map(p -> p.getStock().getId())
                        .collect(Collectors.toSet());

        for (TargetAllocation allocation : allocations) {
            if (!positionStockIds.contains(allocation.getStock().getId())) {
                PositionRebalanceAnalysis analysis =
                        buildAnalysisForNewPosition(allocation, totalPortfolioValue, priceMap);
                positionAnalyses.add(analysis);
            }
        }

        // 드리프트 점수 계산 (임계값 초과 항목)
        BigDecimal totalDriftScore =
                positionAnalyses.stream()
                        .filter(a -> Boolean.TRUE.equals(a.getExceedsDriftThreshold()))
                        .map(a -> a.getDrift().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean needsRebalancing = totalDriftScore.compareTo(BigDecimal.ZERO) > 0;

        // 리밸런싱 추천 생성
        List<RebalanceRecommendation> recommendations =
                buildRecommendations(positionAnalyses, priceMap);

        // 섹터별 요약
        List<SectorAllocationSummary> sectorSummaries =
                buildSectorSummaries(positionAnalyses, allocations, totalPortfolioValue);

        // 목표 배분 DTO 변환
        List<TargetAllocationDto> targetAllocationDtos =
                allocations.stream().map(this::convertToDto).collect(Collectors.toList());

        return RebalancingDashboardDto.builder()
                .totalPortfolioValue(totalPortfolioValue)
                .totalTargetPercent(totalTargetPercent)
                .unallocatedPercent(ONE_HUNDRED.subtract(totalTargetPercent))
                .totalDriftScore(totalDriftScore)
                .needsRebalancing(needsRebalancing)
                .positionAnalyses(positionAnalyses)
                .recommendations(recommendations)
                .sectorSummaries(sectorSummaries)
                .targetAllocations(targetAllocationDtos)
                .build();
    }

    /** 단일 포지션 분석 */
    public PositionRebalanceAnalysis analyzePosition(Long accountId, String symbol) {
        Long targetAccountId = resolveAccountId(accountId);

        // 포트폴리오 조회
        List<Portfolio> portfolios = portfolioRepository.findByAccountIdWithStock(targetAccountId);
        Map<String, BigDecimal> priceMap = batchFetchCurrentPrices(portfolios);
        BigDecimal totalPortfolioValue = calculateTotalValue(portfolios, priceMap);

        // 해당 종목 포트폴리오 찾기
        Optional<Portfolio> portfolioOpt =
                portfolios.stream()
                        .filter(p -> p.getStock().getSymbol().equalsIgnoreCase(symbol))
                        .findFirst();

        // 목표 배분 찾기
        Optional<TargetAllocation> allocationOpt =
                targetAllocationRepository.findByAccountIdAndStockSymbol(targetAccountId, symbol);

        if (portfolioOpt.isEmpty() && allocationOpt.isEmpty()) {
            throw new EntityNotFoundException("포지션 또는 목표 배분을 찾을 수 없습니다: " + symbol);
        }

        if (portfolioOpt.isPresent()) {
            Portfolio portfolio = portfolioOpt.get();
            TargetAllocation allocation = allocationOpt.orElse(null);
            return buildPositionAnalysis(portfolio, allocation, priceMap, totalPortfolioValue);
        } else {
            return buildAnalysisForNewPosition(allocationOpt.get(), totalPortfolioValue, priceMap);
        }
    }

    /** 리밸런싱 추천 계산 */
    public List<RebalanceRecommendation> calculateRecommendations(
            Long accountId, BigDecimal minTradeAmount) {
        RebalancingDashboardDto dashboard = getRebalancingDashboard(accountId);

        if (minTradeAmount == null) {
            minTradeAmount = BigDecimal.valueOf(10000);
        }

        final BigDecimal threshold = minTradeAmount;
        return dashboard.getRecommendations().stream()
                .filter(r -> r.getEstimatedAmount().abs().compareTo(threshold) >= 0)
                .collect(Collectors.toList());
    }

    // ===== Helper Methods =====

    private Long resolveAccountId(Long accountId) {
        if (accountId == null) {
            return accountService.getDefaultAccount().getId();
        }
        return accountId;
    }

    private Map<String, BigDecimal> batchFetchCurrentPrices(List<Portfolio> portfolios) {
        Map<String, BigDecimal> priceMap = new HashMap<>();

        List<String> symbols =
                portfolios.stream()
                        .filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                        .map(p -> p.getStock().getSymbol())
                        .distinct()
                        .collect(Collectors.toList());

        for (String symbol : symbols) {
            try {
                BigDecimal price = stockPriceService.getCurrentPrice(symbol);
                priceMap.put(symbol, price);
            } catch (Exception e) {
                log.debug("Price fetch failed for {}: {}", symbol, e.getMessage());
            }
        }

        return priceMap;
    }

    private BigDecimal getPrice(
            Map<String, BigDecimal> priceMap, String symbol, BigDecimal fallbackPrice) {
        return priceMap.getOrDefault(symbol, fallbackPrice);
    }

    private BigDecimal calculateTotalValue(
            List<Portfolio> portfolios, Map<String, BigDecimal> priceMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String symbol = portfolio.getStock().getSymbol();
            BigDecimal price = getPrice(priceMap, symbol, portfolio.getAveragePrice());
            total = total.add(portfolio.getQuantity().multiply(price));
        }
        return total;
    }

    private List<PositionRebalanceAnalysis> buildPositionAnalyses(
            List<Portfolio> portfolios,
            List<TargetAllocation> allocations,
            Map<String, BigDecimal> priceMap,
            BigDecimal totalPortfolioValue) {

        Map<Long, TargetAllocation> allocationMap =
                allocations.stream().collect(Collectors.toMap(a -> a.getStock().getId(), a -> a));

        List<PositionRebalanceAnalysis> analyses = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            if (portfolio.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            TargetAllocation allocation = allocationMap.get(portfolio.getStock().getId());
            PositionRebalanceAnalysis analysis =
                    buildPositionAnalysis(portfolio, allocation, priceMap, totalPortfolioValue);
            analyses.add(analysis);
        }

        // 우선순위 및 드리프트 크기로 정렬
        analyses.sort(
                Comparator.comparing(
                                (PositionRebalanceAnalysis a) ->
                                        Boolean.TRUE.equals(a.getExceedsDriftThreshold()) ? 0 : 1)
                        .thenComparing(
                                a -> a.getPriority() != null ? a.getPriority() : Integer.MAX_VALUE)
                        .thenComparing(a -> a.getDrift().abs(), Comparator.reverseOrder()));

        return analyses;
    }

    private PositionRebalanceAnalysis buildPositionAnalysis(
            Portfolio portfolio,
            TargetAllocation allocation,
            Map<String, BigDecimal> priceMap,
            BigDecimal totalPortfolioValue) {

        Stock stock = portfolio.getStock();
        String symbol = stock.getSymbol();
        BigDecimal currentPrice = getPrice(priceMap, symbol, portfolio.getAveragePrice());
        BigDecimal currentValue = portfolio.getQuantity().multiply(currentPrice);

        BigDecimal currentPercent = BigDecimal.ZERO;
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
            currentPercent =
                    currentValue
                            .divide(totalPortfolioValue, DECIMAL_SCALE, RoundingMode.HALF_UP)
                            .multiply(PERCENT_MULTIPLIER);
        }

        BigDecimal targetPercent =
                allocation != null ? allocation.getTargetPercent() : BigDecimal.ZERO;
        BigDecimal drift = currentPercent.subtract(targetPercent);
        BigDecimal driftThreshold =
                allocation != null ? allocation.getDriftThresholdPercent() : BigDecimal.valueOf(5);

        boolean exceedsThreshold = drift.abs().compareTo(driftThreshold) > 0;

        BigDecimal targetValue =
                totalPortfolioValue
                        .multiply(targetPercent)
                        .divide(PERCENT_MULTIPLIER, DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal adjustmentAmount = targetValue.subtract(currentValue);
        BigDecimal adjustmentQuantity = BigDecimal.ZERO;
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            adjustmentQuantity =
                    adjustmentAmount.divide(currentPrice, DECIMAL_SCALE, RoundingMode.HALF_UP);
        }

        return PositionRebalanceAnalysis.builder()
                .stockId(stock.getId())
                .stockSymbol(symbol)
                .stockName(stock.getName())
                .sector(stock.getSector())
                .currentQuantity(portfolio.getQuantity())
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .currentPercent(currentPercent)
                .targetPercent(targetPercent)
                .drift(drift)
                .driftThreshold(driftThreshold)
                .exceedsDriftThreshold(exceedsThreshold)
                .targetValue(targetValue)
                .adjustmentAmount(adjustmentAmount)
                .adjustmentQuantity(adjustmentQuantity)
                .priority(allocation != null ? allocation.getPriority() : null)
                .build();
    }

    private PositionRebalanceAnalysis buildAnalysisForNewPosition(
            TargetAllocation allocation,
            BigDecimal totalPortfolioValue,
            Map<String, BigDecimal> priceMap) {

        Stock stock = allocation.getStock();
        String symbol = stock.getSymbol();

        BigDecimal currentPrice = BigDecimal.ZERO;
        try {
            currentPrice = stockPriceService.getCurrentPrice(symbol);
            priceMap.put(symbol, currentPrice);
        } catch (Exception e) {
            log.warn("Cannot fetch price for {}: {}", symbol, e.getMessage());
        }

        BigDecimal targetPercent = allocation.getTargetPercent();
        BigDecimal drift = BigDecimal.ZERO.subtract(targetPercent).negate(); // 현재 0% - 목표 = -목표
        BigDecimal driftThreshold = allocation.getDriftThresholdPercent();

        boolean exceedsThreshold = drift.abs().compareTo(driftThreshold) > 0;

        BigDecimal targetValue =
                totalPortfolioValue
                        .multiply(targetPercent)
                        .divide(PERCENT_MULTIPLIER, DECIMAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal adjustmentQuantity = BigDecimal.ZERO;
        if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            adjustmentQuantity =
                    targetValue.divide(currentPrice, DECIMAL_SCALE, RoundingMode.HALF_UP);
        }

        return PositionRebalanceAnalysis.builder()
                .stockId(stock.getId())
                .stockSymbol(symbol)
                .stockName(stock.getName())
                .sector(stock.getSector())
                .currentQuantity(BigDecimal.ZERO)
                .currentPrice(currentPrice)
                .currentValue(BigDecimal.ZERO)
                .currentPercent(BigDecimal.ZERO)
                .targetPercent(targetPercent)
                .drift(targetPercent.negate()) // 현재 0이므로 drift = -targetPercent
                .driftThreshold(driftThreshold)
                .exceedsDriftThreshold(exceedsThreshold)
                .targetValue(targetValue)
                .adjustmentAmount(targetValue)
                .adjustmentQuantity(adjustmentQuantity)
                .priority(allocation.getPriority())
                .build();
    }

    private List<RebalanceRecommendation> buildRecommendations(
            List<PositionRebalanceAnalysis> positionAnalyses, Map<String, BigDecimal> priceMap) {

        return positionAnalyses.stream()
                .filter(a -> Boolean.TRUE.equals(a.getExceedsDriftThreshold()))
                .map(
                        analysis -> {
                            String action =
                                    analysis.getAdjustmentAmount().compareTo(BigDecimal.ZERO) > 0
                                            ? ACTION_BUY
                                            : ACTION_SELL;
                            BigDecimal quantity = analysis.getAdjustmentQuantity().abs();
                            BigDecimal estimatedAmount = analysis.getAdjustmentAmount().abs();

                            String reason =
                                    String.format(
                                            "드리프트 %.2f%% (임계값 %.2f%% 초과)",
                                            analysis.getDrift().abs(),
                                            analysis.getDriftThreshold());

                            return RebalanceRecommendation.builder()
                                    .stockId(analysis.getStockId())
                                    .stockSymbol(analysis.getStockSymbol())
                                    .stockName(analysis.getStockName())
                                    .action(action)
                                    .quantity(quantity)
                                    .currentPrice(analysis.getCurrentPrice())
                                    .estimatedAmount(estimatedAmount)
                                    .drift(analysis.getDrift())
                                    .priority(analysis.getPriority())
                                    .reason(reason)
                                    .build();
                        })
                .sorted(
                        Comparator.comparing(
                                        (RebalanceRecommendation r) ->
                                                r.getPriority() != null
                                                        ? r.getPriority()
                                                        : Integer.MAX_VALUE)
                                .thenComparing(r -> r.getDrift().abs(), Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    private List<SectorAllocationSummary> buildSectorSummaries(
            List<PositionRebalanceAnalysis> positionAnalyses,
            List<TargetAllocation> allocations,
            BigDecimal totalPortfolioValue) {

        // 섹터별 현재 가치 합계
        Map<Sector, BigDecimal> sectorCurrentValues = new HashMap<>();
        Map<Sector, Integer> sectorPositionCounts = new HashMap<>();

        for (PositionRebalanceAnalysis analysis : positionAnalyses) {
            Sector sector = analysis.getSector() != null ? analysis.getSector() : Sector.OTHER;
            sectorCurrentValues.merge(sector, analysis.getCurrentValue(), BigDecimal::add);
            sectorPositionCounts.merge(sector, 1, Integer::sum);
        }

        // 섹터별 목표 배분율 합계
        Map<Sector, BigDecimal> sectorTargetPercents = new HashMap<>();
        for (TargetAllocation allocation : allocations) {
            Sector sector =
                    allocation.getStock().getSector() != null
                            ? allocation.getStock().getSector()
                            : Sector.OTHER;
            sectorTargetPercents.merge(sector, allocation.getTargetPercent(), BigDecimal::add);
        }

        // 모든 섹터 수집
        Set<Sector> allSectors = new HashSet<>();
        allSectors.addAll(sectorCurrentValues.keySet());
        allSectors.addAll(sectorTargetPercents.keySet());

        return allSectors.stream()
                .map(
                        sector -> {
                            BigDecimal currentValue =
                                    sectorCurrentValues.getOrDefault(sector, BigDecimal.ZERO);
                            BigDecimal currentPercent = BigDecimal.ZERO;
                            if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                                currentPercent =
                                        currentValue
                                                .divide(
                                                        totalPortfolioValue,
                                                        DECIMAL_SCALE,
                                                        RoundingMode.HALF_UP)
                                                .multiply(PERCENT_MULTIPLIER);
                            }
                            BigDecimal targetPercent =
                                    sectorTargetPercents.getOrDefault(sector, BigDecimal.ZERO);
                            BigDecimal drift = currentPercent.subtract(targetPercent);

                            return SectorAllocationSummary.builder()
                                    .sector(sector)
                                    .sectorLabel(sector.getLabel())
                                    .currentValue(currentValue)
                                    .currentPercent(currentPercent)
                                    .targetPercent(targetPercent)
                                    .drift(drift)
                                    .positionCount(sectorPositionCounts.getOrDefault(sector, 0))
                                    .build();
                        })
                .sorted((a, b) -> b.getCurrentPercent().compareTo(a.getCurrentPercent()))
                .collect(Collectors.toList());
    }

    private TargetAllocationDto convertToDto(TargetAllocation allocation) {
        return TargetAllocationDto.builder()
                .id(allocation.getId())
                .accountId(allocation.getAccount().getId())
                .stockId(allocation.getStock().getId())
                .stockSymbol(allocation.getStock().getSymbol())
                .stockName(allocation.getStock().getName())
                .targetPercent(allocation.getTargetPercent())
                .driftThresholdPercent(allocation.getDriftThresholdPercent())
                .isActive(allocation.getIsActive())
                .priority(allocation.getPriority())
                .notes(allocation.getNotes())
                .build();
    }
}
