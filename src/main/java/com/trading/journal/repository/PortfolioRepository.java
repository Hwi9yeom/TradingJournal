package com.trading.journal.repository;

import com.trading.journal.entity.Portfolio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    // 기존 메서드 (하위 호환성) - account_id가 NULL인 경우 또는 전체 조회용
    Optional<Portfolio> findByStockId(Long stockId);

    Optional<Portfolio> findByStockSymbol(String symbol);

    // Account 기반 새로운 메서드
    Optional<Portfolio> findByAccountIdAndStockId(Long accountId, Long stockId);

    Optional<Portfolio> findByAccountIdAndStockSymbol(Long accountId, String symbol);

    List<Portfolio> findByAccountId(Long accountId);

    List<Portfolio> findByAccountIdOrderByUpdatedAtDesc(Long accountId);

    @Query("SELECT p FROM Portfolio p JOIN FETCH p.stock WHERE p.account.id = :accountId")
    List<Portfolio> findByAccountIdWithStock(@Param("accountId") Long accountId);

    @Query("SELECT p FROM Portfolio p JOIN FETCH p.stock LEFT JOIN FETCH p.account")
    List<Portfolio> findAllWithStockAndAccount();

    // 특정 종목이 여러 계좌에 있는지 확인
    List<Portfolio> findByStockIdOrderByAccountId(Long stockId);

    // account_id가 NULL인 포트폴리오 (마이그레이션용)
    @Query("SELECT p FROM Portfolio p WHERE p.account IS NULL")
    List<Portfolio> findByAccountIsNull();

    // 리스크 관리용
    int countByAccountId(Long accountId);

    @Query("SELECT COUNT(p) FROM Portfolio p WHERE p.account.id = :accountId AND p.quantity > 0")
    int countActivePositionsByAccountId(@Param("accountId") Long accountId);
}
