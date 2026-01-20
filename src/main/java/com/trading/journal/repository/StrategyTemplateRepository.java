package com.trading.journal.repository;

import com.trading.journal.entity.StrategyTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 전략 템플릿 Repository */
@Repository
public interface StrategyTemplateRepository extends JpaRepository<StrategyTemplate, Long> {

    /** 계좌별 템플릿 목록 (또는 전체 공용) */
    @Query(
            "SELECT t FROM StrategyTemplate t WHERE "
                    + "t.accountId IS NULL OR t.accountId = :accountId "
                    + "ORDER BY t.isDefault DESC, t.usageCount DESC, t.name ASC")
    List<StrategyTemplate> findByAccountIdOrNull(@Param("accountId") Long accountId);

    /** 전체 템플릿 목록 (정렬: 기본 > 사용횟수 > 이름) */
    @Query(
            "SELECT t FROM StrategyTemplate t ORDER BY t.isDefault DESC, t.usageCount DESC, t.name ASC")
    List<StrategyTemplate> findAllOrderByUsage();

    /** 전략 종류별 템플릿 목록 */
    List<StrategyTemplate> findByStrategyTypeOrderByUsageCountDesc(String strategyType);

    /** 계좌의 기본 템플릿 조회 */
    @Query(
            "SELECT t FROM StrategyTemplate t WHERE "
                    + "(t.accountId IS NULL OR t.accountId = :accountId) "
                    + "AND t.isDefault = true")
    Optional<StrategyTemplate> findDefaultTemplate(@Param("accountId") Long accountId);

    /** 이름으로 템플릿 검색 */
    @Query(
            "SELECT t FROM StrategyTemplate t WHERE "
                    + "(t.accountId IS NULL OR t.accountId = :accountId) "
                    + "AND t.name LIKE %:name%")
    List<StrategyTemplate> findByNameContaining(
            @Param("accountId") Long accountId, @Param("name") String name);

    /** 기본 템플릿 해제 (특정 계좌) */
    @Query(
            "UPDATE StrategyTemplate t SET t.isDefault = false WHERE "
                    + "(t.accountId IS NULL OR t.accountId = :accountId)")
    void clearDefaultTemplates(@Param("accountId") Long accountId);

    /** 가장 많이 사용된 템플릿 */
    @Query("SELECT t FROM StrategyTemplate t ORDER BY t.usageCount DESC")
    List<StrategyTemplate> findTopByUsage();

    /** 이름 중복 체크 */
    boolean existsByNameAndAccountId(String name, Long accountId);
}
