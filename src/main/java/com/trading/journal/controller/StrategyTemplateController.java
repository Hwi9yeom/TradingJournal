package com.trading.journal.controller;

import com.trading.journal.dto.StrategyTemplateDto;
import com.trading.journal.dto.StrategyTemplateDto.*;
import com.trading.journal.service.StrategyTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 전략 템플릿 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class StrategyTemplateController {

    private final StrategyTemplateService templateService;

    /**
     * 템플릿 생성
     */
    @PostMapping
    public ResponseEntity<StrategyTemplateDto> createTemplate(@RequestBody TemplateRequest request) {
        log.info("템플릿 생성 요청: name={}", request.getName());
        StrategyTemplateDto template = templateService.createTemplate(request);
        return ResponseEntity.ok(template);
    }

    /**
     * 템플릿 수정
     */
    @PutMapping("/{id}")
    public ResponseEntity<StrategyTemplateDto> updateTemplate(
            @PathVariable Long id,
            @RequestBody TemplateRequest request) {
        log.info("템플릿 수정 요청: id={}", id);
        StrategyTemplateDto template = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(template);
    }

    /**
     * 템플릿 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        log.info("템플릿 삭제 요청: id={}", id);
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 템플릿 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<StrategyTemplateDto>> getTemplates(
            @RequestParam(required = false) Long accountId) {
        log.info("템플릿 목록 조회: accountId={}", accountId);
        List<StrategyTemplateDto> templates = templateService.getTemplates(accountId);
        return ResponseEntity.ok(templates);
    }

    /**
     * 템플릿 목록 (간소화)
     */
    @GetMapping("/list")
    public ResponseEntity<List<TemplateListItem>> getTemplateList(
            @RequestParam(required = false) Long accountId) {
        log.info("템플릿 목록 (간소화) 조회: accountId={}", accountId);
        List<TemplateListItem> templates = templateService.getTemplateList(accountId);
        return ResponseEntity.ok(templates);
    }

    /**
     * 템플릿 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<StrategyTemplateDto> getTemplate(@PathVariable Long id) {
        log.info("템플릿 상세 조회: id={}", id);
        StrategyTemplateDto template = templateService.getTemplate(id);
        return ResponseEntity.ok(template);
    }

    /**
     * 템플릿 복사
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<StrategyTemplateDto> duplicateTemplate(@PathVariable Long id) {
        log.info("템플릿 복사 요청: id={}", id);
        StrategyTemplateDto template = templateService.duplicateTemplate(id);
        return ResponseEntity.ok(template);
    }

    /**
     * 템플릿 적용 (백테스트 설정으로 변환)
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<BacktestConfig> applyTemplate(@PathVariable Long id) {
        log.info("템플릿 적용 요청: id={}", id);
        BacktestConfig config = templateService.applyTemplate(id);
        return ResponseEntity.ok(config);
    }

    /**
     * 기본 템플릿으로 설정
     */
    @PostMapping("/{id}/set-default")
    public ResponseEntity<StrategyTemplateDto> setAsDefault(@PathVariable Long id) {
        log.info("기본 템플릿 설정 요청: id={}", id);
        StrategyTemplateDto template = templateService.setAsDefault(id);
        return ResponseEntity.ok(template);
    }

    /**
     * 전략 종류 목록 조회
     */
    @GetMapping("/strategy-types")
    public ResponseEntity<List<StrategyTypeInfo>> getStrategyTypes() {
        log.info("전략 종류 목록 조회");
        List<StrategyTypeInfo> types = templateService.getStrategyTypes();
        return ResponseEntity.ok(types);
    }
}
