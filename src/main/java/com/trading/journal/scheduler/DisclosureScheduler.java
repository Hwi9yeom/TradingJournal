package com.trading.journal.scheduler;

import com.trading.journal.service.DisclosureService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DisclosureScheduler {

    private final DisclosureService disclosureService;

    /** 매일 오전 9시와 오후 6시에 공시 정보 동기화 한국 주식시장 개장 전후로 설정 */
    @Scheduled(cron = "0 0 9,18 * * *")
    public void syncDisclosures() {
        log.info("공시 정보 동기화 시작: {}", LocalDateTime.now());

        try {
            disclosureService.syncAllPortfolioDisclosures();
            log.info("공시 정보 동기화 완료");
        } catch (Exception e) {
            log.error("공시 정보 동기화 실패: {}", e.getMessage(), e);
        }
    }

    /** 매 시간마다 중요 공시 확인 (장중) 평일 오전 9시부터 오후 6시까지 */
    @Scheduled(cron = "0 0 9-18 * * MON-FRI")
    public void checkImportantDisclosures() {
        log.debug("중요 공시 확인: {}", LocalDateTime.now());

        try {
            // 포트폴리오 종목들의 최신 공시만 간단히 체크
            // 실제로는 더 효율적인 방법으로 구현 필요
            disclosureService.syncAllPortfolioDisclosures();
        } catch (Exception e) {
            log.error("중요 공시 확인 실패: {}", e.getMessage());
        }
    }
}
