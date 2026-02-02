package com.trading.journal.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 스트레스 테스트 시나리오 엔티티 사전 정의된 시장 스트레스 시나리오 또는 사용자 정의 시나리오를 관리 */
@Entity
@Table(
        name = "stress_scenario",
        indexes = {
            @Index(name = "idx_stress_scenario_code", columnList = "scenarioCode", unique = true),
            @Index(name = "idx_stress_scenario_predefined", columnList = "isPredefined")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StressScenario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 시나리오 코드 (예: "COVID_2020", "DOTCOM_BUBBLE", "CUSTOM_001") */
    @Column(nullable = false, unique = true, length = 50)
    private String scenarioCode;

    /** 시나리오 이름 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 시나리오 설명 */
    @Column(length = 1000)
    private String description;

    /** 시나리오 시작일 (실제 역사적 사건의 경우) */
    private LocalDate startDate;

    /** 시나리오 종료일 (실제 역사적 사건의 경우) */
    private LocalDate endDate;

    /** 전체 시장 충격 비율 (%) 예: -30 = 시장 30% 하락 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal marketShockPercent;

    /** 섹터별 영향도 (JSON) 예: {"TECH": -40, "FINANCE": -25, "HEALTHCARE": -10} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, BigDecimal> sectorImpacts;

    /** 사전 정의된 시나리오 여부 true: 시스템 제공 시나리오, false: 사용자 정의 시나리오 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPredefined = false;
}
