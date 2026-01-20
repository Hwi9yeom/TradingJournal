package com.trading.journal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.StrategyTemplateDto;
import com.trading.journal.dto.StrategyTemplateDto.*;
import com.trading.journal.entity.StrategyTemplate;
import com.trading.journal.repository.StrategyTemplateRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전략 템플릿 서비스 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyTemplateService {

    private final StrategyTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    // 전략 종류별 한글명
    private static final Map<String, String> STRATEGY_LABELS =
            Map.of(
                    "MA_CROSS", "이동평균 교차",
                    "RSI", "RSI 과매수/과매도",
                    "BOLLINGER", "볼린저 밴드",
                    "MOMENTUM", "모멘텀",
                    "MACD", "MACD");

    /** 템플릿 생성 */
    @Transactional
    public StrategyTemplateDto createTemplate(TemplateRequest request) {
        log.info("템플릿 생성: name={}", request.getName());

        // 이름 중복 체크
        if (templateRepository.existsByNameAndAccountId(
                request.getName(), request.getAccountId())) {
            throw new RuntimeException("동일한 이름의 템플릿이 이미 존재합니다: " + request.getName());
        }

        // 기본 템플릿 설정 시 기존 기본 해제
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultTemplates(request.getAccountId());
        }

        StrategyTemplate template =
                StrategyTemplate.builder()
                        .accountId(request.getAccountId())
                        .name(request.getName())
                        .description(request.getDescription())
                        .strategyType(request.getStrategyType())
                        .parametersJson(toJson(request.getParameters()))
                        .positionSizePercent(request.getPositionSizePercent())
                        .stopLossPercent(request.getStopLossPercent())
                        .takeProfitPercent(request.getTakeProfitPercent())
                        .commissionRate(request.getCommissionRate())
                        .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                        .color(request.getColor())
                        .usageCount(0)
                        .build();

        StrategyTemplate saved = templateRepository.save(template);
        return convertToDto(saved);
    }

    /** 템플릿 수정 */
    @Transactional
    public StrategyTemplateDto updateTemplate(Long id, TemplateRequest request) {
        log.info("템플릿 수정: id={}", id);

        StrategyTemplate template =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("템플릿을 찾을 수 없습니다: " + id));

        // 기본 템플릿 설정 시 기존 기본 해제
        if (Boolean.TRUE.equals(request.getIsDefault())
                && !Boolean.TRUE.equals(template.getIsDefault())) {
            clearDefaultTemplates(template.getAccountId());
        }

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setStrategyType(request.getStrategyType());
        template.setParametersJson(toJson(request.getParameters()));
        template.setPositionSizePercent(request.getPositionSizePercent());
        template.setStopLossPercent(request.getStopLossPercent());
        template.setTakeProfitPercent(request.getTakeProfitPercent());
        template.setCommissionRate(request.getCommissionRate());
        template.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        template.setColor(request.getColor());

        StrategyTemplate saved = templateRepository.save(template);
        return convertToDto(saved);
    }

    /** 템플릿 삭제 */
    @Transactional
    public void deleteTemplate(Long id) {
        log.info("템플릿 삭제: id={}", id);
        if (!templateRepository.existsById(id)) {
            throw new RuntimeException("템플릿을 찾을 수 없습니다: " + id);
        }
        templateRepository.deleteById(id);
    }

    /** 템플릿 목록 조회 */
    public List<StrategyTemplateDto> getTemplates(Long accountId) {
        List<StrategyTemplate> templates;
        if (accountId != null) {
            templates = templateRepository.findByAccountIdOrNull(accountId);
        } else {
            templates = templateRepository.findAllOrderByUsage();
        }
        return templates.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /** 템플릿 목록 (간소화) */
    public List<TemplateListItem> getTemplateList(Long accountId) {
        List<StrategyTemplate> templates;
        if (accountId != null) {
            templates = templateRepository.findByAccountIdOrNull(accountId);
        } else {
            templates = templateRepository.findAllOrderByUsage();
        }
        return templates.stream().map(this::convertToListItem).collect(Collectors.toList());
    }

    /** 템플릿 상세 조회 */
    public StrategyTemplateDto getTemplate(Long id) {
        StrategyTemplate template =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("템플릿을 찾을 수 없습니다: " + id));
        return convertToDto(template);
    }

    /** 템플릿 복사 */
    @Transactional
    public StrategyTemplateDto duplicateTemplate(Long id) {
        log.info("템플릿 복사: id={}", id);

        StrategyTemplate original =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("템플릿을 찾을 수 없습니다: " + id));

        // 새 이름 생성 (원본 + _copy 또는 숫자 증가)
        String newName = generateCopyName(original.getName(), original.getAccountId());

        StrategyTemplate copy =
                StrategyTemplate.builder()
                        .accountId(original.getAccountId())
                        .name(newName)
                        .description(original.getDescription())
                        .strategyType(original.getStrategyType())
                        .parametersJson(original.getParametersJson())
                        .positionSizePercent(original.getPositionSizePercent())
                        .stopLossPercent(original.getStopLossPercent())
                        .takeProfitPercent(original.getTakeProfitPercent())
                        .commissionRate(original.getCommissionRate())
                        .isDefault(false) // 복사본은 기본 아님
                        .color(original.getColor())
                        .usageCount(0)
                        .build();

        StrategyTemplate saved = templateRepository.save(copy);
        return convertToDto(saved);
    }

    /** 템플릿 적용 (백테스트 설정으로 변환) */
    @Transactional
    public BacktestConfig applyTemplate(Long id) {
        log.info("템플릿 적용: id={}", id);

        StrategyTemplate template =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("템플릿을 찾을 수 없습니다: " + id));

        // 사용 횟수 증가
        template.incrementUsageCount();
        templateRepository.save(template);

        return BacktestConfig.builder()
                .strategyType(template.getStrategyType())
                .parameters(fromJson(template.getParametersJson()))
                .positionSizePercent(template.getPositionSizePercent())
                .stopLossPercent(template.getStopLossPercent())
                .takeProfitPercent(template.getTakeProfitPercent())
                .commissionRate(template.getCommissionRate())
                .build();
    }

    /** 기본 템플릿으로 설정 */
    @Transactional
    public StrategyTemplateDto setAsDefault(Long id) {
        log.info("기본 템플릿 설정: id={}", id);

        StrategyTemplate template =
                templateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("템플릿을 찾을 수 없습니다: " + id));

        // 기존 기본 해제
        clearDefaultTemplates(template.getAccountId());

        // 새 기본 설정
        template.setIsDefault(true);
        StrategyTemplate saved = templateRepository.save(template);
        return convertToDto(saved);
    }

    /** 전략 종류 목록 조회 */
    public List<StrategyTypeInfo> getStrategyTypes() {
        return STRATEGY_LABELS.entrySet().stream()
                .map(
                        entry ->
                                StrategyTypeInfo.builder()
                                        .type(entry.getKey())
                                        .label(entry.getValue())
                                        .description(getStrategyDescription(entry.getKey()))
                                        .defaultParameters(getDefaultParameters(entry.getKey()))
                                        .build())
                .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private void clearDefaultTemplates(Long accountId) {
        List<StrategyTemplate> templates;
        if (accountId != null) {
            templates = templateRepository.findByAccountIdOrNull(accountId);
        } else {
            templates = templateRepository.findAllOrderByUsage();
        }

        templates.forEach(
                t -> {
                    if (Boolean.TRUE.equals(t.getIsDefault())) {
                        t.setIsDefault(false);
                        templateRepository.save(t);
                    }
                });
    }

    private String generateCopyName(String originalName, Long accountId) {
        String baseName = originalName.replaceAll("_copy\\d*$", "");
        String newName = baseName + "_copy";
        int counter = 1;

        while (templateRepository.existsByNameAndAccountId(newName, accountId)) {
            newName = baseName + "_copy" + counter;
            counter++;
        }

        return newName;
    }

    private String toJson(Map<String, Object> parameters) {
        if (parameters == null) return null;
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            log.error("JSON 변환 실패", e);
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON 파싱 실패", e);
            return new HashMap<>();
        }
    }

    private StrategyTemplateDto convertToDto(StrategyTemplate template) {
        return StrategyTemplateDto.builder()
                .id(template.getId())
                .accountId(template.getAccountId())
                .name(template.getName())
                .description(template.getDescription())
                .strategyType(template.getStrategyType())
                .strategyTypeLabel(
                        STRATEGY_LABELS.getOrDefault(
                                template.getStrategyType(), template.getStrategyType()))
                .parameters(fromJson(template.getParametersJson()))
                .positionSizePercent(template.getPositionSizePercent())
                .stopLossPercent(template.getStopLossPercent())
                .takeProfitPercent(template.getTakeProfitPercent())
                .commissionRate(template.getCommissionRate())
                .isDefault(template.getIsDefault())
                .usageCount(template.getUsageCount())
                .color(template.getColor())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private TemplateListItem convertToListItem(StrategyTemplate template) {
        return TemplateListItem.builder()
                .id(template.getId())
                .name(template.getName())
                .strategyType(template.getStrategyType())
                .strategyTypeLabel(
                        STRATEGY_LABELS.getOrDefault(
                                template.getStrategyType(), template.getStrategyType()))
                .usageCount(template.getUsageCount())
                .isDefault(template.getIsDefault())
                .color(template.getColor())
                .build();
    }

    private String getStrategyDescription(String strategyType) {
        return switch (strategyType) {
            case "MA_CROSS" -> "단기/장기 이동평균선의 교차를 이용한 추세 추종 전략";
            case "RSI" -> "RSI 지표를 이용한 과매수/과매도 역추세 전략";
            case "BOLLINGER" -> "볼린저 밴드를 이용한 변동성 돌파 전략";
            case "MOMENTUM" -> "가격 모멘텀을 이용한 추세 추종 전략";
            case "MACD" -> "MACD 지표를 이용한 추세 전환 전략";
            default -> "";
        };
    }

    private Map<String, ParameterInfo> getDefaultParameters(String strategyType) {
        Map<String, ParameterInfo> params = new LinkedHashMap<>();

        switch (strategyType) {
            case "MA_CROSS" -> {
                params.put(
                        "shortPeriod",
                        ParameterInfo.builder()
                                .name("shortPeriod")
                                .label("단기 이평")
                                .type("number")
                                .defaultValue(5)
                                .min(2)
                                .max(50)
                                .step(1)
                                .build());
                params.put(
                        "longPeriod",
                        ParameterInfo.builder()
                                .name("longPeriod")
                                .label("장기 이평")
                                .type("number")
                                .defaultValue(20)
                                .min(5)
                                .max(200)
                                .step(1)
                                .build());
            }
            case "RSI" -> {
                params.put(
                        "period",
                        ParameterInfo.builder()
                                .name("period")
                                .label("RSI 기간")
                                .type("number")
                                .defaultValue(14)
                                .min(5)
                                .max(30)
                                .step(1)
                                .build());
                params.put(
                        "oversold",
                        ParameterInfo.builder()
                                .name("oversold")
                                .label("과매도")
                                .type("number")
                                .defaultValue(30)
                                .min(10)
                                .max(40)
                                .step(5)
                                .build());
                params.put(
                        "overbought",
                        ParameterInfo.builder()
                                .name("overbought")
                                .label("과매수")
                                .type("number")
                                .defaultValue(70)
                                .min(60)
                                .max(90)
                                .step(5)
                                .build());
            }
            case "BOLLINGER" -> {
                params.put(
                        "period",
                        ParameterInfo.builder()
                                .name("period")
                                .label("기간")
                                .type("number")
                                .defaultValue(20)
                                .min(10)
                                .max(50)
                                .step(1)
                                .build());
                params.put(
                        "stdDev",
                        ParameterInfo.builder()
                                .name("stdDev")
                                .label("표준편차")
                                .type("number")
                                .defaultValue(2.0)
                                .min(1.0)
                                .max(3.0)
                                .step(0.5)
                                .build());
            }
            case "MOMENTUM" -> {
                params.put(
                        "period",
                        ParameterInfo.builder()
                                .name("period")
                                .label("모멘텀 기간")
                                .type("number")
                                .defaultValue(10)
                                .min(5)
                                .max(30)
                                .step(1)
                                .build());
                params.put(
                        "threshold",
                        ParameterInfo.builder()
                                .name("threshold")
                                .label("진입 기준(%)")
                                .type("number")
                                .defaultValue(5)
                                .min(1)
                                .max(20)
                                .step(1)
                                .build());
            }
            case "MACD" -> {
                params.put(
                        "fastPeriod",
                        ParameterInfo.builder()
                                .name("fastPeriod")
                                .label("Fast EMA")
                                .type("number")
                                .defaultValue(12)
                                .min(5)
                                .max(20)
                                .step(1)
                                .build());
                params.put(
                        "slowPeriod",
                        ParameterInfo.builder()
                                .name("slowPeriod")
                                .label("Slow EMA")
                                .type("number")
                                .defaultValue(26)
                                .min(15)
                                .max(50)
                                .step(1)
                                .build());
                params.put(
                        "signalPeriod",
                        ParameterInfo.builder()
                                .name("signalPeriod")
                                .label("Signal")
                                .type("number")
                                .defaultValue(9)
                                .min(5)
                                .max(15)
                                .step(1)
                                .build());
            }
        }

        return params;
    }
}
