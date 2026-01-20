package com.trading.journal.controller;

import com.trading.journal.dto.DisclosureDto;
import com.trading.journal.dto.DisclosureSummaryDto;
import com.trading.journal.service.DisclosureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disclosures")
@RequiredArgsConstructor
public class DisclosureController {

    private final DisclosureService disclosureService;

    /** 포트폴리오 공시 요약 조회 */
    @GetMapping("/summary")
    public ResponseEntity<DisclosureSummaryDto> getPortfolioDisclosureSummary() {
        DisclosureSummaryDto summary = disclosureService.getPortfolioDisclosureSummary();
        return ResponseEntity.ok(summary);
    }

    /** 종목별 공시 조회 */
    @GetMapping("/stock/{symbol}")
    public ResponseEntity<List<DisclosureDto>> getDisclosuresByStock(@PathVariable String symbol) {
        List<DisclosureDto> disclosures =
                disclosureService.getDisclosuresByStock(symbol.toUpperCase());
        return ResponseEntity.ok(disclosures);
    }

    /** 공시 읽음 처리 */
    @PutMapping("/{id}/read")
    public ResponseEntity<DisclosureDto> markAsRead(@PathVariable Long id) {
        DisclosureDto disclosure = disclosureService.markAsRead(id);
        return ResponseEntity.ok(disclosure);
    }

    /** 공시 중요 표시 토글 */
    @PutMapping("/{id}/important")
    public ResponseEntity<DisclosureDto> toggleImportant(@PathVariable Long id) {
        DisclosureDto disclosure = disclosureService.toggleImportant(id);
        return ResponseEntity.ok(disclosure);
    }

    /** 종목 공시 동기화 */
    @PostMapping("/sync/{symbol}")
    public ResponseEntity<Void> syncDisclosuresForStock(@PathVariable String symbol) {
        disclosureService.syncDisclosuresForStock(symbol.toUpperCase());
        return ResponseEntity.accepted().build();
    }

    /** 전체 포트폴리오 공시 동기화 */
    @PostMapping("/sync")
    public ResponseEntity<Void> syncAllDisclosures() {
        disclosureService.syncAllPortfolioDisclosures();
        return ResponseEntity.accepted().build();
    }
}
