package com.trading.journal.service;

import com.trading.journal.dto.StressTestRequestDto;
import com.trading.journal.dto.StressTestResultDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.Portfolio;
import com.trading.journal.entity.Sector;
import com.trading.journal.entity.StressScenario;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.StressScenarioRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스트레스 테스트 서비스
 *
 * <p>포트폴리오에 대한 스트레스 시나리오 분석을 수행합니다. - 시장 충격 시뮬레이션 - 섹터별 차등 충격 적용 - 포지션별 영향 분석 - 섹터별 영향 집계
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StressTestService {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final int SCALE = 2;

    private final StressScenarioRepository scenarioRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;

    /**
     * 스트레스 테스트 실행
     *
     * @param request 스트레스 테스트 요청
     * @return 스트레스 테스트 결과
     * @throws IllegalArgumentException 요청 검증 실패 시
     */
    public StressTestResultDto runStressTest(StressTestRequestDto request) {
        log.info(
                "스트레스 테스트 시작 - accountId: {}, scenarioCode: {}, scenarioId: {}",
                request.getAccountId(),
                request.getScenarioCode(),
                request.getScenarioId());

        // 1. 요청 검증
        validateRequest(request);

        // 2. 시나리오 조회
        StressScenario scenario = getScenario(request);

        // 3. 계좌 조회
        Account account =
                accountRepository
                        .findById(request.getAccountId())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "계좌를 찾을 수 없습니다: " + request.getAccountId()));

        // 4. 포트폴리오 포지션 조회
        List<Portfolio> positions =
                portfolioRepository.findByAccountIdWithStock(request.getAccountId());

        if (positions.isEmpty()) {
            log.warn("포트폴리오에 포지션이 없습니다 - accountId: {}", request.getAccountId());
            return buildEmptyResult(scenario, account, request);
        }

        // 5. 적용할 충격 비율 결정 (customShockPercent 또는 시나리오 기본값)
        BigDecimal effectiveShockPercent =
                request.getCustomShockPercent() != null
                        ? request.getCustomShockPercent()
                        : scenario.getMarketShockPercent();

        // 6. 포지션별 충격 적용 및 영향 계산
        List<StressTestResultDto.PositionImpact> positionImpacts =
                calculatePositionImpacts(positions, scenario, effectiveShockPercent);

        // 7. 섹터별 영향 집계
        List<StressTestResultDto.SectorImpact> sectorImpacts =
                calculateSectorImpacts(positionImpacts, scenario);

        // 8. 포트폴리오 전체 영향 계산
        BigDecimal portfolioValueBefore =
                positionImpacts.stream()
                        .map(StressTestResultDto.PositionImpact::getValueBefore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal portfolioValueAfter =
                positionImpacts.stream()
                        .map(StressTestResultDto.PositionImpact::getValueAfter)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal absoluteLoss = portfolioValueBefore.subtract(portfolioValueAfter);

        BigDecimal percentageLoss =
                portfolioValueBefore.compareTo(BigDecimal.ZERO) > 0
                        ? absoluteLoss
                                .divide(portfolioValueBefore, MC)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        // 9. 통계 계산
        BigDecimal maxPositionLoss =
                positionImpacts.stream()
                        .map(StressTestResultDto.PositionImpact::getAbsoluteLoss)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

        BigDecimal minPositionLoss =
                positionImpacts.stream()
                        .map(StressTestResultDto.PositionImpact::getAbsoluteLoss)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

        BigDecimal avgPositionLossPercent =
                positionImpacts.isEmpty()
                        ? BigDecimal.ZERO
                        : positionImpacts.stream()
                                .map(StressTestResultDto.PositionImpact::getImpactPercent)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(positionImpacts.size()), MC)
                                .setScale(SCALE, RoundingMode.HALF_UP);

        // 10. 결과 DTO 구성
        StressTestResultDto result =
                StressTestResultDto.builder()
                        .scenarioId(scenario.getId())
                        .scenarioCode(scenario.getScenarioCode())
                        .scenarioName(scenario.getName())
                        .scenarioDescription(scenario.getDescription())
                        .shockPercent(effectiveShockPercent)
                        .portfolioValueBefore(
                                portfolioValueBefore.setScale(SCALE, RoundingMode.HALF_UP))
                        .portfolioValueAfter(
                                portfolioValueAfter.setScale(SCALE, RoundingMode.HALF_UP))
                        .absoluteLoss(absoluteLoss.setScale(SCALE, RoundingMode.HALF_UP))
                        .percentageLoss(percentageLoss)
                        .maxPositionLoss(maxPositionLoss.setScale(SCALE, RoundingMode.HALF_UP))
                        .minPositionLoss(minPositionLoss.setScale(SCALE, RoundingMode.HALF_UP))
                        .avgPositionLossPercent(avgPositionLossPercent)
                        .positionImpacts(positionImpacts)
                        .sectorImpacts(sectorImpacts)
                        .executedAt(LocalDateTime.now())
                        .accountId(account.getId())
                        .accountName(account.getName())
                        .build();

        log.info(
                "스트레스 테스트 완료 - accountId: {}, totalLoss: {}, lossPercent: {}%",
                request.getAccountId(), absoluteLoss, percentageLoss);

        return result;
    }

    /**
     * 사용 가능한 사전 정의 시나리오 목록 조회
     *
     * @return 사전 정의 시나리오 목록
     */
    public List<StressScenario> getAvailableScenarios() {
        return scenarioRepository.findByIsPredefinedTrue();
    }

    /**
     * 모든 시나리오 목록 조회 (사전 정의 + 사용자 정의)
     *
     * @return 모든 시나리오 목록
     */
    public List<StressScenario> getAllScenarios() {
        return scenarioRepository.findAllByOrderByNameAsc();
    }

    /** 요청 검증 */
    private void validateRequest(StressTestRequestDto request) {
        if (!request.hasValidScenario()) {
            throw new IllegalArgumentException("scenarioCode 또는 scenarioId 중 하나는 필수입니다");
        }

        if (request.getCustomShockPercent() != null) {
            BigDecimal customShock = request.getCustomShockPercent();
            if (customShock.compareTo(BigDecimal.valueOf(-100)) < 0
                    || customShock.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(
                        "customShockPercent는 -100에서 100 사이여야 합니다: " + customShock);
            }
        }
    }

    /** 시나리오 조회 (코드 또는 ID) */
    private StressScenario getScenario(StressTestRequestDto request) {
        if (request.getScenarioCode() != null && !request.getScenarioCode().isBlank()) {
            return scenarioRepository
                    .findByScenarioCode(request.getScenarioCode())
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "시나리오를 찾을 수 없습니다: " + request.getScenarioCode()));
        } else {
            return scenarioRepository
                    .findById(request.getScenarioId())
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "시나리오를 찾을 수 없습니다: " + request.getScenarioId()));
        }
    }

    /** 포지션별 영향 계산 */
    private List<StressTestResultDto.PositionImpact> calculatePositionImpacts(
            List<Portfolio> positions, StressScenario scenario, BigDecimal marketShockPercent) {

        Map<String, BigDecimal> sectorImpacts =
                scenario.getSectorImpacts() != null
                        ? scenario.getSectorImpacts()
                        : Collections.emptyMap();

        BigDecimal totalLoss = BigDecimal.ZERO;

        // First pass: calculate absolute losses
        List<StressTestResultDto.PositionImpact> impacts = new ArrayList<>();

        for (Portfolio position : positions) {
            // 현재 포지션 가치 (평균 단가 × 보유 수량)
            BigDecimal valueBefore =
                    position.getAveragePrice()
                            .multiply(position.getQuantity())
                            .setScale(SCALE, RoundingMode.HALF_UP);

            // 적용할 충격 비율 결정 (섹터별 충격이 있으면 우선 적용)
            BigDecimal effectiveShock = marketShockPercent;
            Sector sector = position.getStock().getSector();

            if (sector != null && sectorImpacts.containsKey(sector.name())) {
                effectiveShock = sectorImpacts.get(sector.name());
                log.debug(
                        "섹터별 충격 적용 - symbol: {}, sector: {}, shock: {}%",
                        position.getStock().getSymbol(), sector.name(), effectiveShock);
            }

            // 충격 적용 후 가치
            BigDecimal valueAfter = applyShock(valueBefore, effectiveShock);

            // 손실 금액 및 비율
            BigDecimal absoluteLoss = valueBefore.subtract(valueAfter);
            BigDecimal impactPercent =
                    valueBefore.compareTo(BigDecimal.ZERO) > 0
                            ? absoluteLoss
                                    .divide(valueBefore, MC)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            totalLoss = totalLoss.add(absoluteLoss);

            StressTestResultDto.PositionImpact impact =
                    StressTestResultDto.PositionImpact.builder()
                            .symbol(position.getStock().getSymbol())
                            .stockName(position.getStock().getName())
                            .sector(sector != null ? sector.getLabel() : "미분류")
                            .quantity(position.getQuantity())
                            .valueBefore(valueBefore)
                            .valueAfter(valueAfter.setScale(SCALE, RoundingMode.HALF_UP))
                            .absoluteLoss(absoluteLoss.setScale(SCALE, RoundingMode.HALF_UP))
                            .impactPercent(impactPercent)
                            .contributionToTotalLoss(
                                    BigDecimal.ZERO) // Will be calculated in second pass
                            .build();

            impacts.add(impact);
        }

        // Second pass: calculate contribution to total loss
        BigDecimal finalTotalLoss = totalLoss;
        if (finalTotalLoss.compareTo(BigDecimal.ZERO) > 0) {
            impacts.forEach(
                    impact -> {
                        BigDecimal contribution =
                                impact.getAbsoluteLoss()
                                        .divide(finalTotalLoss, MC)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(SCALE, RoundingMode.HALF_UP);
                        impact.setContributionToTotalLoss(contribution);
                    });
        }

        // Sort by absolute loss descending (highest impact first)
        impacts.sort(
                Comparator.comparing(StressTestResultDto.PositionImpact::getAbsoluteLoss)
                        .reversed());

        return impacts;
    }

    /** 섹터별 영향 집계 */
    private List<StressTestResultDto.SectorImpact> calculateSectorImpacts(
            List<StressTestResultDto.PositionImpact> positionImpacts, StressScenario scenario) {

        if (positionImpacts.isEmpty()) {
            return Collections.emptyList();
        }

        // 섹터별 그룹화
        Map<String, List<StressTestResultDto.PositionImpact>> bySector =
                positionImpacts.stream()
                        .collect(
                                Collectors.groupingBy(
                                        StressTestResultDto.PositionImpact::getSector));

        // 전체 손실 계산
        BigDecimal totalLoss =
                positionImpacts.stream()
                        .map(StressTestResultDto.PositionImpact::getAbsoluteLoss)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> sectorShocks =
                scenario.getSectorImpacts() != null
                        ? scenario.getSectorImpacts()
                        : Collections.emptyMap();

        List<StressTestResultDto.SectorImpact> sectorImpacts = new ArrayList<>();

        for (Map.Entry<String, List<StressTestResultDto.PositionImpact>> entry :
                bySector.entrySet()) {
            String sectorName = entry.getKey();
            List<StressTestResultDto.PositionImpact> sectorPositions = entry.getValue();

            BigDecimal sectorValueBefore =
                    sectorPositions.stream()
                            .map(StressTestResultDto.PositionImpact::getValueBefore)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sectorValueAfter =
                    sectorPositions.stream()
                            .map(StressTestResultDto.PositionImpact::getValueAfter)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sectorAbsoluteLoss = sectorValueBefore.subtract(sectorValueAfter);

            BigDecimal sectorLossPercent =
                    sectorValueBefore.compareTo(BigDecimal.ZERO) > 0
                            ? sectorAbsoluteLoss
                                    .divide(sectorValueBefore, MC)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            BigDecimal contribution =
                    totalLoss.compareTo(BigDecimal.ZERO) > 0
                            ? sectorAbsoluteLoss
                                    .divide(totalLoss, MC)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

            // Find sector-specific shock if exists
            BigDecimal sectorShockPercent = scenario.getMarketShockPercent();
            for (Map.Entry<String, BigDecimal> shockEntry : sectorShocks.entrySet()) {
                try {
                    Sector sector = Sector.valueOf(shockEntry.getKey());
                    if (sector.getLabel().equals(sectorName)) {
                        sectorShockPercent = shockEntry.getValue();
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown sector in scenario: {}", shockEntry.getKey());
                }
            }

            StressTestResultDto.SectorImpact sectorImpact =
                    StressTestResultDto.SectorImpact.builder()
                            .sectorName(sectorName)
                            .sectorShockPercent(sectorShockPercent)
                            .positionCount(sectorPositions.size())
                            .totalValueBefore(
                                    sectorValueBefore.setScale(SCALE, RoundingMode.HALF_UP))
                            .totalValueAfter(sectorValueAfter.setScale(SCALE, RoundingMode.HALF_UP))
                            .absoluteLoss(sectorAbsoluteLoss.setScale(SCALE, RoundingMode.HALF_UP))
                            .lossPercent(sectorLossPercent)
                            .contributionToTotalLoss(contribution)
                            .build();

            sectorImpacts.add(sectorImpact);
        }

        // Sort by absolute loss descending
        sectorImpacts.sort(
                Comparator.comparing(StressTestResultDto.SectorImpact::getAbsoluteLoss).reversed());

        return sectorImpacts;
    }

    /** 빈 포트폴리오에 대한 결과 생성 */
    private StressTestResultDto buildEmptyResult(
            StressScenario scenario, Account account, StressTestRequestDto request) {

        BigDecimal effectiveShockPercent =
                request.getCustomShockPercent() != null
                        ? request.getCustomShockPercent()
                        : scenario.getMarketShockPercent();

        return StressTestResultDto.builder()
                .scenarioId(scenario.getId())
                .scenarioCode(scenario.getScenarioCode())
                .scenarioName(scenario.getName())
                .scenarioDescription(scenario.getDescription())
                .shockPercent(effectiveShockPercent)
                .portfolioValueBefore(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .portfolioValueAfter(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .absoluteLoss(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .percentageLoss(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .maxPositionLoss(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .minPositionLoss(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .avgPositionLossPercent(BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP))
                .positionImpacts(Collections.emptyList())
                .sectorImpacts(Collections.emptyList())
                .executedAt(LocalDateTime.now())
                .accountId(account.getId())
                .accountName(account.getName())
                .build();
    }

    /**
     * 충격 비율 적용
     *
     * @param value 원래 가치
     * @param shockPercent 충격 비율 (%)
     * @return 충격 적용 후 가치
     */
    private BigDecimal applyShock(BigDecimal value, BigDecimal shockPercent) {
        // newValue = currentValue * (1 + shockPercent/100)
        // 예: shockPercent = -30 -> newValue = value * 0.70
        BigDecimal multiplier =
                BigDecimal.ONE.add(shockPercent.divide(BigDecimal.valueOf(100), MC));
        return value.multiply(multiplier);
    }
}
