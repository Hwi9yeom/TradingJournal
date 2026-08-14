package com.trading.journal.service;

import com.trading.journal.dto.MarketSentimentDto;
import com.trading.journal.dto.SentimentDashboardDto;
import com.trading.journal.entity.MarketSentiment;
import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.entity.SentimentMarket;
import com.trading.journal.entity.SentimentZone;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.MarketSentimentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시장 심리 지표 서비스.
 *
 * <p>지표 값을 기록하고 {@link SentimentEvaluator}의 임계값으로 공포/탐욕 구간을 판정한다. 종합 대시보드는 지표별 최신값을 정규화 점수로 평균해 시장
 * 전체의 과열/공포 정도를 하나의 값으로 보여준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSentimentService {

    /** 이 일수를 넘긴 기록은 종합 판정에서 제외하고 '오래됨'으로 표시한다. */
    static final int STALE_AFTER_DAYS = 14;

    private final MarketSentimentRepository marketSentimentRepository;

    /** 기록 저장 (같은 지표 + 같은 날짜면 덮어쓴다) */
    @Transactional
    public MarketSentimentDto record(MarketSentimentDto dto) {
        if (dto.getIndicator() == null) {
            throw new IllegalArgumentException("지표(indicator)는 필수입니다");
        }
        if (dto.getValue() == null) {
            throw new IllegalArgumentException("지표 값(value)은 필수입니다");
        }

        LocalDate date = dto.getRecordedDate() != null ? dto.getRecordedDate() : LocalDate.now();

        MarketSentiment entity =
                marketSentimentRepository
                        .findByIndicatorAndRecordedDate(dto.getIndicator(), date)
                        .orElseGet(
                                () ->
                                        MarketSentiment.builder()
                                                .indicator(dto.getIndicator())
                                                .recordedDate(date)
                                                .build());

        entity.setValue(dto.getValue());
        entity.setNotes(dto.getNotes());

        MarketSentiment saved = marketSentimentRepository.save(entity);
        log.info(
                "심리 지표 기록: {} = {} ({})",
                saved.getIndicator(),
                saved.getValue(),
                SentimentEvaluator.evaluate(saved.getIndicator(), saved.getValue()));

        return toDto(saved);
    }

    /** 기록 삭제 */
    @Transactional
    public void delete(Long id) {
        if (!marketSentimentRepository.existsById(id)) {
            throw new ResourceNotFoundException("심리 지표 기록을 찾을 수 없습니다: " + id);
        }
        marketSentimentRepository.deleteById(id);
        log.info("심리 지표 기록 삭제: {}", id);
    }

    /** 전체 기록 최신순 조회 */
    public List<MarketSentimentDto> getAll() {
        return marketSentimentRepository.findAllByOrderByRecordedDateDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 지표별 기록 최신순 조회 */
    public List<MarketSentimentDto> getByIndicator(SentimentIndicator indicator) {
        return marketSentimentRepository.findByIndicatorOrderByRecordedDateDesc(indicator).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 지표별 기간 추이 (오래된 순) */
    public List<MarketSentimentDto> getTrend(
            SentimentIndicator indicator, LocalDate startDate, LocalDate endDate) {
        return marketSentimentRepository
                .findByIndicatorAndRecordedDateBetweenOrderByRecordedDateAsc(
                        indicator, startDate, endDate)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 지표 카탈로그 (아직 기록이 없어도 화면에 목록을 그릴 수 있도록) */
    public List<MarketSentimentDto> getCatalog() {
        List<MarketSentimentDto> catalog = new ArrayList<>();
        for (SentimentIndicator indicator : SentimentIndicator.values()) {
            catalog.add(
                    MarketSentimentDto.builder()
                            .indicator(indicator)
                            .indicatorLabel(indicator.getLabel())
                            .market(indicator.getMarket())
                            .unit(indicator.getUnit())
                            .sourceUrl(indicator.getSourceUrl())
                            .interpretation(indicator.getInterpretation())
                            .build());
        }
        return catalog;
    }

    /**
     * 종합 심리 대시보드.
     *
     * <p>지표별 최신 기록만 사용한다. {@link #STALE_AFTER_DAYS}일을 넘긴 기록은 현재 시장을 대변하지 못하므로 평균에서 빼고 '오래됨'으로 보고한다.
     */
    public SentimentDashboardDto getDashboard() {
        Map<SentimentIndicator, MarketSentiment> latest = latestPerIndicator();
        LocalDate today = LocalDate.now();

        List<MarketSentimentDto> fresh = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        Map<SentimentMarket, List<Integer>> scoresByMarket = new EnumMap<>(SentimentMarket.class);

        for (SentimentIndicator indicator : SentimentIndicator.values()) {
            MarketSentiment record = latest.get(indicator);
            if (record == null) {
                missing.add(indicator.getLabel());
                continue;
            }

            MarketSentimentDto dto = toDto(record);
            long age = ChronoUnit.DAYS.between(record.getRecordedDate(), today);
            if (age > STALE_AFTER_DAYS) {
                stale.add(indicator.getLabel());
                continue;
            }

            fresh.add(dto);
            scoresByMarket
                    .computeIfAbsent(indicator.getMarket(), k -> new ArrayList<>())
                    .add(dto.getZoneScore());
        }

        Map<String, BigDecimal> scoreByMarket = new LinkedHashMap<>();
        Map<String, SentimentZone> zoneByMarket = new LinkedHashMap<>();
        for (Map.Entry<SentimentMarket, List<Integer>> entry : scoresByMarket.entrySet()) {
            double average =
                    entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
            scoreByMarket.put(entry.getKey().name(), round2(average));
            zoneByMarket.put(entry.getKey().name(), SentimentZone.fromScore(average));
        }

        if (fresh.isEmpty()) {
            return SentimentDashboardDto.builder()
                    .scoreByMarket(scoreByMarket)
                    .zoneByMarket(zoneByMarket)
                    .latestByIndicator(List.of())
                    .missingIndicators(missing)
                    .staleIndicators(stale)
                    .build();
        }

        double overallAverage =
                fresh.stream().mapToInt(MarketSentimentDto::getZoneScore).average().orElse(0);
        SentimentZone overallZone = SentimentZone.fromScore(overallAverage);

        fresh.sort(Comparator.comparing(d -> d.getIndicator().ordinal()));

        return SentimentDashboardDto.builder()
                .overallZone(overallZone)
                .overallLabel(overallZone.getLabel())
                .overallAction(overallZone.getAction())
                .overallScore(round2(overallAverage))
                .scoreByMarket(scoreByMarket)
                .zoneByMarket(zoneByMarket)
                .latestByIndicator(fresh)
                .missingIndicators(missing)
                .staleIndicators(stale)
                .build();
    }

    /** 지표별 가장 최근 기록 */
    private Map<SentimentIndicator, MarketSentiment> latestPerIndicator() {
        Map<SentimentIndicator, MarketSentiment> latest = new EnumMap<>(SentimentIndicator.class);

        for (MarketSentiment record :
                marketSentimentRepository.findAllByOrderByRecordedDateDesc()) {
            latest.merge(
                    record.getIndicator(),
                    record,
                    (existing, candidate) ->
                            candidate.getRecordedDate().isAfter(existing.getRecordedDate())
                                    ? candidate
                                    : existing);
        }
        return latest;
    }

    private MarketSentimentDto toDto(MarketSentiment s) {
        SentimentIndicator indicator = s.getIndicator();
        SentimentZone zone = SentimentEvaluator.evaluate(indicator, s.getValue());

        return MarketSentimentDto.builder()
                .id(s.getId())
                .indicator(indicator)
                .recordedDate(s.getRecordedDate())
                .value(s.getValue())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .indicatorLabel(indicator.getLabel())
                .market(indicator.getMarket())
                .unit(indicator.getUnit())
                .sourceUrl(indicator.getSourceUrl())
                .interpretation(indicator.getInterpretation())
                .zone(zone)
                .zoneLabel(zone.getLabel())
                .zoneAction(zone.getAction())
                .zoneScore(zone.getScore())
                .daysSinceRecorded(
                        Optional.ofNullable(s.getRecordedDate())
                                .map(d -> ChronoUnit.DAYS.between(d, LocalDate.now()))
                                .orElse(null))
                .build();
    }

    private static BigDecimal round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
