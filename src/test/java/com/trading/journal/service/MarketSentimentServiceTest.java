package com.trading.journal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.trading.journal.dto.MarketSentimentDto;
import com.trading.journal.dto.SentimentDashboardDto;
import com.trading.journal.entity.MarketSentiment;
import com.trading.journal.entity.SentimentIndicator;
import com.trading.journal.entity.SentimentZone;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.MarketSentimentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("시장 심리 지표 서비스 테스트")
class MarketSentimentServiceTest {

    @Mock private MarketSentimentRepository marketSentimentRepository;

    @InjectMocks private MarketSentimentService marketSentimentService;

    private static MarketSentiment sentiment(
            SentimentIndicator indicator, String value, LocalDate date) {
        return MarketSentiment.builder()
                .indicator(indicator)
                .value(new BigDecimal(value))
                .recordedDate(date)
                .build();
    }

    @Nested
    @DisplayName("기록")
    class RecordTests {

        @Test
        @DisplayName("날짜를 비우면 오늘로 기록된다")
        void record_DefaultsToToday() {
            when(marketSentimentRepository.findByIndicatorAndRecordedDate(any(), any()))
                    .thenReturn(Optional.empty());
            when(marketSentimentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            MarketSentimentDto result =
                    marketSentimentService.record(
                            MarketSentimentDto.builder()
                                    .indicator(SentimentIndicator.FEAR_GREED_INDEX)
                                    .value(new BigDecimal("18"))
                                    .build());

            assertThat(result.getRecordedDate()).isEqualTo(LocalDate.now());
            assertThat(result.getZone()).isEqualTo(SentimentZone.EXTREME_FEAR);
            assertThat(result.getZoneLabel()).isEqualTo("극단적 공포");
            assertThat(result.getZoneScore()).isEqualTo(-2);
        }

        @Test
        @DisplayName("같은 지표를 같은 날 다시 기록하면 덮어쓴다")
        void record_OverwritesSameDay() {
            MarketSentiment existing =
                    sentiment(SentimentIndicator.NAAIM_EXPOSURE, "50", LocalDate.of(2026, 7, 20));
            existing.setId(5L);
            when(marketSentimentRepository.findByIndicatorAndRecordedDate(
                            SentimentIndicator.NAAIM_EXPOSURE, LocalDate.of(2026, 7, 20)))
                    .thenReturn(Optional.of(existing));
            when(marketSentimentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            marketSentimentService.record(
                    MarketSentimentDto.builder()
                            .indicator(SentimentIndicator.NAAIM_EXPOSURE)
                            .recordedDate(LocalDate.of(2026, 7, 20))
                            .value(new BigDecimal("95"))
                            .build());

            ArgumentCaptor<MarketSentiment> captor = ArgumentCaptor.forClass(MarketSentiment.class);
            verify(marketSentimentRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(5L);
            assertThat(captor.getValue().getValue()).isEqualByComparingTo("95");
        }

        @Test
        @DisplayName("지표나 값이 없으면 예외")
        void record_RequiresIndicatorAndValue() {
            assertThatThrownBy(
                            () ->
                                    marketSentimentService.record(
                                            MarketSentimentDto.builder()
                                                    .value(BigDecimal.ONE)
                                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("indicator");

            assertThatThrownBy(
                            () ->
                                    marketSentimentService.record(
                                            MarketSentimentDto.builder()
                                                    .indicator(SentimentIndicator.FEAR_GREED_INDEX)
                                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
        }

        @Test
        @DisplayName("없는 기록 삭제 시 예외")
        void delete_NotFound() {
            when(marketSentimentRepository.existsById(1L)).thenReturn(false);

            assertThatThrownBy(() -> marketSentimentService.delete(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("지표 카탈로그")
    class CatalogTests {

        @Test
        @DisplayName("모든 지표가 라벨·단위·출처와 함께 노출된다")
        void catalog_ExposesEveryIndicatorWithMetadata() {
            List<MarketSentimentDto> catalog = marketSentimentService.getCatalog();

            assertThat(catalog).hasSameSizeAs(SentimentIndicator.values());
            assertThat(catalog)
                    .allSatisfy(
                            dto -> {
                                assertThat(dto.getIndicatorLabel()).isNotBlank();
                                assertThat(dto.getUnit()).isNotBlank();
                                assertThat(dto.getSourceUrl()).startsWith("https://");
                                assertThat(dto.getInterpretation()).isNotBlank();
                                assertThat(dto.getMarket()).isNotNull();
                            });
        }
    }

    @Nested
    @DisplayName("종합 대시보드")
    class DashboardTests {

        @Test
        @DisplayName("기록이 없으면 종합 판정은 null이고 전 지표가 미기록으로 보고된다")
        void dashboard_EmptyWhenNoRecords() {
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(List.of());

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getOverallZone()).isNull();
            assertThat(dashboard.getLatestByIndicator()).isEmpty();
            assertThat(dashboard.getMissingIndicators())
                    .hasSize(SentimentIndicator.values().length);
        }

        @Test
        @DisplayName("지표별 최신값 평균으로 종합 구간을 판정한다")
        void dashboard_AveragesLatestValues() {
            LocalDate today = LocalDate.now();
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(
                            List.of(
                                    // 공포 -2
                                    sentiment(SentimentIndicator.FEAR_GREED_INDEX, "10", today),
                                    // 공포 -2
                                    sentiment(SentimentIndicator.PUT_CALL_RATIO, "1.2", today)));

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getOverallZone()).isEqualTo(SentimentZone.EXTREME_FEAR);
            assertThat(dashboard.getOverallScore()).isEqualByComparingTo("-2.00");
            assertThat(dashboard.getLatestByIndicator()).hasSize(2);
        }

        @Test
        @DisplayName("같은 지표는 가장 최근 기록만 반영한다")
        void dashboard_UsesMostRecentRecordPerIndicator() {
            LocalDate today = LocalDate.now();
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(
                            List.of(
                                    sentiment(SentimentIndicator.FEAR_GREED_INDEX, "90", today),
                                    sentiment(
                                            SentimentIndicator.FEAR_GREED_INDEX,
                                            "10",
                                            today.minusDays(3))));

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getLatestByIndicator()).hasSize(1);
            assertThat(dashboard.getLatestByIndicator().get(0).getValue())
                    .isEqualByComparingTo("90");
            assertThat(dashboard.getOverallZone()).isEqualTo(SentimentZone.EXTREME_GREED);
        }

        @Test
        @DisplayName("오래된 기록은 종합 평균에서 빼고 stale로 보고한다")
        void dashboard_ExcludesStaleRecords() {
            LocalDate today = LocalDate.now();
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(
                            List.of(
                                    sentiment(SentimentIndicator.FEAR_GREED_INDEX, "90", today),
                                    sentiment(
                                            SentimentIndicator.NAAIM_EXPOSURE,
                                            "5",
                                            today.minusDays(
                                                    MarketSentimentService.STALE_AFTER_DAYS + 1))));

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getStaleIndicators())
                    .containsExactly(SentimentIndicator.NAAIM_EXPOSURE.getLabel());
            assertThat(dashboard.getLatestByIndicator()).hasSize(1);
            // 오래된 극단 공포(-2)가 섞였다면 평균이 0이 됐을 것이다.
            assertThat(dashboard.getOverallScore()).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("경계일(14일)까지는 유효한 기록으로 본다")
        void dashboard_KeepsRecordsOnStaleBoundary() {
            LocalDate today = LocalDate.now();
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(
                            List.of(
                                    sentiment(
                                            SentimentIndicator.FEAR_GREED_INDEX,
                                            "90",
                                            today.minusDays(
                                                    MarketSentimentService.STALE_AFTER_DAYS))));

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getStaleIndicators()).isEmpty();
            assertThat(dashboard.getLatestByIndicator()).hasSize(1);
        }

        @Test
        @DisplayName("시장별로 분리된 평균 점수와 구간을 제공한다")
        void dashboard_SplitsScoresByMarket() {
            LocalDate today = LocalDate.now();
            when(marketSentimentRepository.findAllByOrderByRecordedDateDesc())
                    .thenReturn(
                            List.of(
                                    // 미국 주식: 극단적 탐욕 +2
                                    sentiment(SentimentIndicator.FEAR_GREED_INDEX, "90", today),
                                    // 암호화폐: 극단적 공포 -2
                                    sentiment(SentimentIndicator.MVRV_Z_SCORE, "-1", today)));

            SentimentDashboardDto dashboard = marketSentimentService.getDashboard();

            assertThat(dashboard.getScoreByMarket())
                    .containsEntry("US_STOCK", new BigDecimal("2.00"))
                    .containsEntry("CRYPTO", new BigDecimal("-2.00"));
            assertThat(dashboard.getZoneByMarket())
                    .containsEntry("US_STOCK", SentimentZone.EXTREME_GREED)
                    .containsEntry("CRYPTO", SentimentZone.EXTREME_FEAR);
            // 상반된 두 시장이 상쇄되어 종합은 중립이 된다.
            assertThat(dashboard.getOverallZone()).isEqualTo(SentimentZone.NEUTRAL);
        }
    }
}
