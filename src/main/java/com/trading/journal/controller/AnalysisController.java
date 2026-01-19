package com.trading.journal.controller;

import com.trading.journal.dto.BenchmarkComparisonDto;
import com.trading.journal.dto.CorrelationMatrixDto;
import com.trading.journal.dto.DrawdownDto;
import com.trading.journal.dto.EquityCurveDto;
import com.trading.journal.dto.PairCorrelationDto;
import com.trading.journal.dto.PeriodAnalysisDto;
import com.trading.journal.dto.PortfolioTreemapDto;
import com.trading.journal.dto.RiskMetricsDto;
import com.trading.journal.dto.RollingCorrelationDto;
import com.trading.journal.dto.SectorAnalysisDto;
import com.trading.journal.dto.SectorCorrelationDto;
import com.trading.journal.dto.StockAnalysisDto;
import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.dto.TradingPatternDto;
import com.trading.journal.entity.BenchmarkType;
import com.trading.journal.entity.Sector;
import com.trading.journal.entity.Stock;
import com.trading.journal.service.AnalysisService;
import com.trading.journal.service.BenchmarkService;
import com.trading.journal.service.PortfolioAnalysisService;
import com.trading.journal.service.RiskMetricsService;
import com.trading.journal.service.SectorAnalysisService;
import com.trading.journal.service.StockAnalysisService;
import com.trading.journal.service.TaxCalculationService;
import com.trading.journal.service.TradingPatternService;
import com.trading.journal.service.TradingStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {
    
    private final AnalysisService analysisService;
    private final RiskMetricsService riskMetricsService;
    private final TradingPatternService tradingPatternService;
    private final BenchmarkService benchmarkService;
    private final SectorAnalysisService sectorAnalysisService;
    private final StockAnalysisService stockAnalysisService;
    private final TaxCalculationService taxCalculationService;
    private final TradingStatisticsService tradingStatisticsService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    
    @GetMapping("/period")
    public ResponseEntity<PeriodAnalysisDto> analyzePeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/period/year/{year}")
    public ResponseEntity<PeriodAnalysisDto> analyzeYear(@PathVariable Integer year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/period/month/{year}/{month}")
    public ResponseEntity<PeriodAnalysisDto> analyzeMonth(
            @PathVariable Integer year,
            @PathVariable Integer month) {
        
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/stock/{symbol}")
    public ResponseEntity<StockAnalysisDto> analyzeStock(@PathVariable String symbol) {
        StockAnalysisDto analysis = stockAnalysisService.analyzeStock(symbol.toUpperCase());
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/tax/{year}")
    public ResponseEntity<TaxCalculationDto> calculateTax(@PathVariable Integer year) {
        if (year < 2000 || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("유효하지 않은 연도입니다");
        }
        
        TaxCalculationDto taxCalculation = taxCalculationService.calculateTax(year);
        return ResponseEntity.ok(taxCalculation);
    }
    
    @GetMapping("/tax/current")
    public ResponseEntity<TaxCalculationDto> calculateCurrentYearTax() {
        Integer currentYear = LocalDate.now().getYear();
        TaxCalculationDto taxCalculation = taxCalculationService.calculateTax(currentYear);
        return ResponseEntity.ok(taxCalculation);
    }
    
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTradeStatistics() {
        log.info("Getting trade statistics");
        return ResponseEntity.ok(tradingStatisticsService.getOverallStatistics());
    }
    
    @GetMapping("/asset-history")
    public ResponseEntity<Map<String, Object>> getAssetHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting asset history from {} to {}", startDate, endDate);
        return ResponseEntity.ok(tradingStatisticsService.getAssetHistory(startDate, endDate));
    }

    @GetMapping("/monthly-returns")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyReturns() {
        log.info("Getting monthly returns");
        return ResponseEntity.ok(tradingStatisticsService.getMonthlyReturns());
    }

    @GetMapping("/equity-curve")
    public ResponseEntity<EquityCurveDto> getEquityCurve(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting equity curve from {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        EquityCurveDto equityCurve = analysisService.calculateEquityCurve(startDate, endDate);
        return ResponseEntity.ok(equityCurve);
    }

    @GetMapping("/drawdown")
    public ResponseEntity<DrawdownDto> getDrawdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting drawdown from {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        DrawdownDto drawdown = analysisService.calculateDrawdown(startDate, endDate);
        return ResponseEntity.ok(drawdown);
    }

    @GetMapping("/correlation")
    public ResponseEntity<CorrelationMatrixDto> getCorrelation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting correlation matrix from {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        CorrelationMatrixDto correlation = analysisService.calculateCorrelationMatrix(startDate, endDate);
        return ResponseEntity.ok(correlation);
    }

    /**
     * 롤링 상관관계 조회
     * 시간에 따른 두 종목 간 상관관계 변화 추적
     */
    @GetMapping("/correlation/rolling")
    public ResponseEntity<RollingCorrelationDto> getRollingCorrelation(
            @RequestParam String symbol1,
            @RequestParam String symbol2,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "30") int windowDays) {
        log.info("Getting rolling correlation for {} vs {} from {} to {} with {}d window",
                symbol1, symbol2, startDate, endDate, windowDays);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        RollingCorrelationDto rolling = analysisService.calculateRollingCorrelation(
                symbol1, symbol2, startDate, endDate, windowDays);
        return ResponseEntity.ok(rolling);
    }

    /**
     * 종목 쌍 상세 분석
     */
    @GetMapping("/correlation/pair")
    public ResponseEntity<PairCorrelationDto> getPairCorrelation(
            @RequestParam String symbol1,
            @RequestParam String symbol2,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting pair correlation for {} vs {} from {} to {}", symbol1, symbol2, startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        PairCorrelationDto pair = analysisService.calculatePairCorrelation(symbol1, symbol2, startDate, endDate);
        return ResponseEntity.ok(pair);
    }

    /**
     * 섹터별 상관관계 요약
     */
    @GetMapping("/correlation/sector-summary")
    public ResponseEntity<SectorCorrelationDto> getSectorCorrelation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting sector correlation summary from {} to {}", startDate, endDate);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        SectorCorrelationDto sector = analysisService.calculateSectorCorrelation(startDate, endDate);
        return ResponseEntity.ok(sector);
    }

    /**
     * 종합 리스크 메트릭스 조회
     * VaR, Sortino, Calmar 등 고급 리스크 지표 반환
     */
    @GetMapping("/risk-metrics")
    public ResponseEntity<RiskMetricsDto> getRiskMetrics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Getting risk metrics from {} to {} for account {}", startDate, endDate, accountId);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        RiskMetricsDto riskMetrics = riskMetricsService.calculateRiskMetrics(accountId, startDate, endDate);
        return ResponseEntity.ok(riskMetrics);
    }

    /**
     * VaR (Value at Risk) 상세 조회
     */
    @GetMapping("/var")
    public ResponseEntity<Map<String, RiskMetricsDto.VaRDto>> getVaR(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Getting VaR from {} to {} for account {}", startDate, endDate, accountId);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        RiskMetricsDto riskMetrics = riskMetricsService.calculateRiskMetrics(accountId, startDate, endDate);

        Map<String, RiskMetricsDto.VaRDto> varData = new HashMap<>();
        varData.put("var95", riskMetrics.getVar95());
        varData.put("var99", riskMetrics.getVar99());

        return ResponseEntity.ok(varData);
    }

    /**
     * 거래 패턴 분석
     * 연승/연패, 요일별/월별 성과, 보유 기간 분석
     */
    @GetMapping("/patterns")
    public ResponseEntity<TradingPatternDto> getTradingPatterns(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Getting trading patterns from {} to {} for account {}", startDate, endDate, accountId);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        TradingPatternDto patterns = tradingPatternService.analyzePatterns(accountId, startDate, endDate);
        return ResponseEntity.ok(patterns);
    }

    /**
     * 연승/연패 분석만 조회
     */
    @GetMapping("/patterns/streaks")
    public ResponseEntity<TradingPatternDto.StreakAnalysis> getStreakAnalysis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Getting streak analysis from {} to {} for account {}", startDate, endDate, accountId);

        TradingPatternDto patterns = tradingPatternService.analyzePatterns(accountId, startDate, endDate);
        return ResponseEntity.ok(patterns.getStreakAnalysis());
    }

    // ==================== 벤치마크 비교 API ====================

    /**
     * 포트폴리오와 벤치마크 비교 분석
     */
    @GetMapping("/benchmark/compare")
    public ResponseEntity<BenchmarkComparisonDto> compareToBenchmark(
            @RequestParam BenchmarkType benchmark,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Comparing portfolio to {} from {} to {} for account {}", benchmark, startDate, endDate, accountId);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        BenchmarkComparisonDto comparison = benchmarkService.compareToBenchmark(accountId, benchmark, startDate, endDate);
        return ResponseEntity.ok(comparison);
    }

    /**
     * 모든 벤치마크 요약 정보 조회
     */
    @GetMapping("/benchmark/summaries")
    public ResponseEntity<List<BenchmarkComparisonDto.BenchmarkSummary>> getBenchmarkSummaries() {
        log.info("Getting benchmark summaries");
        List<BenchmarkComparisonDto.BenchmarkSummary> summaries = benchmarkService.getBenchmarkSummaries();
        return ResponseEntity.ok(summaries);
    }

    /**
     * 사용 가능한 벤치마크 목록 조회
     */
    @GetMapping("/benchmark/types")
    public ResponseEntity<List<Map<String, String>>> getBenchmarkTypes() {
        log.info("Getting benchmark types");
        List<Map<String, String>> types = Arrays.stream(BenchmarkType.values())
                .map(type -> {
                    Map<String, String> typeInfo = new HashMap<>();
                    typeInfo.put("value", type.name());
                    typeInfo.put("label", type.getLabel());
                    typeInfo.put("symbol", type.getSymbol());
                    typeInfo.put("description", type.getDescription());
                    return typeInfo;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    /**
     * 샘플 벤치마크 데이터 생성 (테스트/데모용)
     */
    @PostMapping("/benchmark/generate-sample")
    public ResponseEntity<Map<String, String>> generateSampleBenchmarkData(
            @RequestParam BenchmarkType benchmark,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Generating sample benchmark data for {} from {} to {}", benchmark, startDate, endDate);

        benchmarkService.generateSampleBenchmarkData(benchmark, startDate, endDate);

        Map<String, String> response = new HashMap<>();
        response.put("message", benchmark.getLabel() + " 샘플 데이터가 생성되었습니다.");
        return ResponseEntity.ok(response);
    }

    // ==================== 섹터별 분석 API ====================

    /**
     * 섹터별 종합 분석
     */
    @GetMapping("/sectors")
    public ResponseEntity<SectorAnalysisDto> analyzeSectors(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long accountId) {
        log.info("Analyzing sectors from {} to {} for account {}", startDate, endDate, accountId);

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다");
        }

        SectorAnalysisDto analysis = sectorAnalysisService.analyzeSectors(accountId, startDate, endDate);
        return ResponseEntity.ok(analysis);
    }

    /**
     * 모든 섹터 목록 조회
     */
    @GetMapping("/sectors/list")
    public ResponseEntity<List<SectorAnalysisDto.SectorOption>> getAllSectors() {
        log.info("Getting all sectors");
        List<SectorAnalysisDto.SectorOption> sectors = sectorAnalysisService.getAllSectors();
        return ResponseEntity.ok(sectors);
    }

    /**
     * 종목 섹터 업데이트
     */
    @PutMapping("/sectors/stock/{stockId}")
    public ResponseEntity<Stock> updateStockSector(
            @PathVariable Long stockId,
            @RequestParam Sector sector,
            @RequestParam(required = false) String industry) {
        log.info("Updating stock {} sector to {} ({})", stockId, sector, industry);
        Stock updated = sectorAnalysisService.updateStockSector(stockId, sector, industry);
        return ResponseEntity.ok(updated);
    }

    // ==================== Portfolio Treemap API ====================

    /**
     * 포트폴리오 트리맵 데이터 조회 (Finviz 스타일)
     * 기간별 성과를 시각화하기 위한 데이터 반환
     *
     * @param period 기간 (1D, 1W, 1M, MTD, 3M, 6M, 1Y)
     * @return 트리맵 데이터 (셀 크기=투자금액, 색상=수익률)
     */
    @GetMapping("/portfolio/treemap")
    public ResponseEntity<PortfolioTreemapDto> getPortfolioTreemap(
            @RequestParam(defaultValue = "1D") String period) {
        log.info("Getting portfolio treemap for period: {}", period);
        PortfolioTreemapDto treemap = portfolioAnalysisService.getPortfolioTreemap(period);
        return ResponseEntity.ok(treemap);
    }

}