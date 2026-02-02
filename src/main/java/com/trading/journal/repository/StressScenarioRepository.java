package com.trading.journal.repository;

import com.trading.journal.entity.StressScenario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 스트레스 시나리오 리포지토리 */
@Repository
public interface StressScenarioRepository extends JpaRepository<StressScenario, Long> {

    /**
     * 시나리오 코드로 조회
     *
     * @param scenarioCode 시나리오 코드
     * @return 스트레스 시나리오
     */
    Optional<StressScenario> findByScenarioCode(String scenarioCode);

    /**
     * 사전 정의된 시나리오 목록 조회
     *
     * @return 사전 정의된 시나리오 목록
     */
    List<StressScenario> findByIsPredefinedTrue();

    /**
     * 시나리오 코드 존재 여부 확인
     *
     * @param scenarioCode 시나리오 코드
     * @return 존재 여부
     */
    boolean existsByScenarioCode(String scenarioCode);

    /**
     * 모든 시나리오를 이름 순으로 조회
     *
     * @return 시나리오 목록
     */
    List<StressScenario> findAllByOrderByNameAsc();
}
