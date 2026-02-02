package com.trading.journal.repository;

import com.trading.journal.entity.PriceAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** 가격 알림 Repository */
@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    /**
     * 사용자별 활성 알림 조회
     *
     * @param userId 사용자 ID
     * @return 활성 상태인 가격 알림 목록
     */
    List<PriceAlert> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * 종목별 활성 알림 조회
     *
     * @param symbol 종목 코드
     * @return 활성 상태인 가격 알림 목록
     */
    List<PriceAlert> findBySymbolAndIsActiveTrue(String symbol);

    /**
     * 활성 상태이며 트리거되지 않은 모든 알림 조회
     *
     * @return 활성 상태이며 트리거되지 않은 가격 알림 목록
     */
    List<PriceAlert> findByIsActiveTrueAndIsTriggeredFalse();

    /**
     * 사용자별 모든 알림 조회 (활성/비활성 모두)
     *
     * @param userId 사용자 ID
     * @return 사용자의 모든 가격 알림 목록
     */
    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 사용자 및 종목별 알림 조회
     *
     * @param userId 사용자 ID
     * @param symbol 종목 코드
     * @return 해당 사용자의 특정 종목 알림 목록
     */
    List<PriceAlert> findByUserIdAndSymbol(Long userId, String symbol);

    /**
     * 트리거된 알림 중 알림 미전송 건 조회
     *
     * @return 트리거되었으나 알림이 전송되지 않은 목록
     */
    @Query(
            "SELECT pa FROM PriceAlert pa WHERE pa.isTriggered = true AND pa.notificationSent = false")
    List<PriceAlert> findTriggeredButNotNotified();

    /**
     * 사용자별 트리거된 알림 조회
     *
     * @param userId 사용자 ID
     * @return 트리거된 알림 목록
     */
    List<PriceAlert> findByUserIdAndIsTriggeredTrueOrderByTriggeredAtDesc(Long userId);

    /**
     * 종목 ID로 활성 알림 조회
     *
     * @param stockId 종목 ID
     * @return 활성 상태인 가격 알림 목록
     */
    List<PriceAlert> findByStockIdAndIsActiveTrue(Long stockId);

    /**
     * 사용자의 활성 알림 개수 조회
     *
     * @param userId 사용자 ID
     * @return 활성 알림 개수
     */
    long countByUserIdAndIsActiveTrue(Long userId);
}
