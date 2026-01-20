package com.trading.journal.service;

import com.trading.journal.dto.DisclosureDto;
import com.trading.journal.dto.DisclosureSummaryDto;
import com.trading.journal.entity.Disclosure;
import com.trading.journal.entity.Stock;
import com.trading.journal.repository.DisclosureRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.StockRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DisclosureService {

    private final DisclosureRepository disclosureRepository;
    private final StockRepository stockRepository;
    private final PortfolioRepository portfolioRepository;
    private final DartApiService dartApiService;

    /** 종목별 공시 조회 */
    @Transactional(readOnly = true)
    public List<DisclosureDto> getDisclosuresByStock(String stockSymbol) {
        return disclosureRepository.findByStockSymbolOrderByReceivedDateDesc(stockSymbol).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 포트폴리오 공시 요약 */
    @Transactional(readOnly = true)
    public DisclosureSummaryDto getPortfolioDisclosureSummary() {
        // 최근 7일간의 공시
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Disclosure> recentDisclosures =
                disclosureRepository.findRecentDisclosuresForPortfolio(sevenDaysAgo);

        // 중요 공시
        List<Disclosure> importantDisclosures =
                disclosureRepository.findImportantDisclosuresForPortfolio();

        // 읽지 않은 공시
        List<Disclosure> unreadDisclosures =
                disclosureRepository.findUnreadDisclosuresForPortfolio();

        Long unreadCount = disclosureRepository.countUnreadDisclosuresForPortfolio();

        return DisclosureSummaryDto.builder()
                .totalCount((long) recentDisclosures.size())
                .unreadCount(unreadCount)
                .importantCount((long) importantDisclosures.size())
                .recentDisclosures(
                        recentDisclosures.stream()
                                .limit(10)
                                .map(this::toDto)
                                .collect(Collectors.toList()))
                .importantDisclosures(
                        importantDisclosures.stream()
                                .limit(5)
                                .map(this::toDto)
                                .collect(Collectors.toList()))
                .unreadDisclosures(
                        unreadDisclosures.stream()
                                .limit(10)
                                .map(this::toDto)
                                .collect(Collectors.toList()))
                .build();
    }

    /** 공시 읽음 처리 */
    public DisclosureDto markAsRead(Long disclosureId) {
        Disclosure disclosure =
                disclosureRepository
                        .findById(disclosureId)
                        .orElseThrow(() -> new RuntimeException("공시를 찾을 수 없습니다: " + disclosureId));

        disclosure.setIsRead(true);
        disclosureRepository.save(disclosure);

        return toDto(disclosure);
    }

    /** 공시 중요 표시 토글 */
    public DisclosureDto toggleImportant(Long disclosureId) {
        Disclosure disclosure =
                disclosureRepository
                        .findById(disclosureId)
                        .orElseThrow(() -> new RuntimeException("공시를 찾을 수 없습니다: " + disclosureId));

        disclosure.setIsImportant(!disclosure.getIsImportant());
        disclosureRepository.save(disclosure);

        return toDto(disclosure);
    }

    /** 종목의 최신 공시 동기화 */
    public void syncDisclosuresForStock(String stockSymbol) {
        Stock stock =
                stockRepository
                        .findBySymbol(stockSymbol)
                        .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다: " + stockSymbol));

        // DART API에서 회사 코드 조회
        dartApiService
                .getCorpCodeByName(stock.getName())
                .flatMap(
                        corpCode -> {
                            LocalDate endDate = LocalDate.now();
                            LocalDate beginDate = endDate.minusDays(30); // 최근 30일
                            return dartApiService.getDisclosuresByCorpCode(
                                    corpCode, beginDate, endDate);
                        })
                .subscribe(
                        disclosureDtos -> saveDisclosures(stock, disclosureDtos),
                        error -> log.error("공시 동기화 실패: {}", error.getMessage()));
    }

    /** 포트폴리오 전체 공시 동기화 */
    public void syncAllPortfolioDisclosures() {
        List<Stock> portfolioStocks =
                portfolioRepository.findAll().stream()
                        .map(portfolio -> portfolio.getStock())
                        .distinct()
                        .collect(Collectors.toList());

        for (Stock stock : portfolioStocks) {
            try {
                syncDisclosuresForStock(stock.getSymbol());
                Thread.sleep(1000); // API 호출 제한을 위한 딜레이
            } catch (Exception e) {
                log.error("종목 {} 공시 동기화 실패: {}", stock.getSymbol(), e.getMessage());
            }
        }
    }

    private void saveDisclosures(Stock stock, List<DisclosureDto> disclosureDtos) {
        for (DisclosureDto dto : disclosureDtos) {
            // 이미 존재하는 공시인지 확인
            if (disclosureRepository.findByReportNumber(dto.getReportNumber()).isPresent()) {
                continue;
            }

            Disclosure disclosure =
                    Disclosure.builder()
                            .stock(stock)
                            .reportNumber(dto.getReportNumber())
                            .corpCode(dto.getCorpCode())
                            .corpName(dto.getCorpName())
                            .reportName(dto.getReportName())
                            .receivedDate(dto.getReceivedDate())
                            .submitter(dto.getSubmitter())
                            .reportType(dto.getReportType())
                            .viewUrl(dto.getViewUrl())
                            .isImportant(dto.getIsImportant())
                            .isRead(false)
                            .build();

            disclosureRepository.save(disclosure);
            log.info("새 공시 저장: {} - {}", stock.getSymbol(), dto.getReportName());
        }
    }

    private DisclosureDto toDto(Disclosure disclosure) {
        return DisclosureDto.builder()
                .id(disclosure.getId())
                .stockSymbol(disclosure.getStock().getSymbol())
                .stockName(disclosure.getStock().getName())
                .reportNumber(disclosure.getReportNumber())
                .corpCode(disclosure.getCorpCode())
                .corpName(disclosure.getCorpName())
                .reportName(disclosure.getReportName())
                .receivedDate(disclosure.getReceivedDate())
                .submitter(disclosure.getSubmitter())
                .reportType(disclosure.getReportType())
                .viewUrl(disclosure.getViewUrl())
                .summary(disclosure.getSummary())
                .isImportant(disclosure.getIsImportant())
                .isRead(disclosure.getIsRead())
                .createdAt(disclosure.getCreatedAt())
                .build();
    }
}
