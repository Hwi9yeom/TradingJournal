package com.trading.journal.repository;

import com.trading.journal.entity.AccountRiskSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRiskSettingsRepository extends JpaRepository<AccountRiskSettings, Long> {

    /**
     * 계정 ID로 리스크 설정 조회
     */
    Optional<AccountRiskSettings> findByAccountId(Long accountId);

    /**
     * 계정 ID로 리스크 설정 존재 여부 확인
     */
    boolean existsByAccountId(Long accountId);

    /**
     * 계정 정보와 함께 리스크 설정 조회
     */
    @Query("SELECT rs FROM AccountRiskSettings rs JOIN FETCH rs.account WHERE rs.account.id = :accountId")
    Optional<AccountRiskSettings> findByAccountIdWithAccount(@Param("accountId") Long accountId);

    /**
     * 기본 계정의 리스크 설정 조회
     */
    @Query("SELECT rs FROM AccountRiskSettings rs JOIN rs.account a WHERE a.isDefault = true")
    Optional<AccountRiskSettings> findByDefaultAccount();
}
