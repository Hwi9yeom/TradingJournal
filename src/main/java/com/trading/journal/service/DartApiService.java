package com.trading.journal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.DisclosureDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DartApiService {
    
    private final WebClient dartWebClient;
    private final ObjectMapper objectMapper;
    
    @Value("${dart.api.key:}")
    private String apiKey;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * 회사별 공시 검색
     * @param corpCode 회사 고유번호
     * @param beginDate 시작일 (YYYYMMDD)
     * @param endDate 종료일 (YYYYMMDD)
     * @return 공시 목록
     */
    public Mono<List<DisclosureDto>> getDisclosuresByCorpCode(String corpCode, LocalDate beginDate, LocalDate endDate) {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", beginDate.format(DATE_FORMATTER))
                        .queryParam("end_de", endDate.format(DATE_FORMATTER))
                        .queryParam("page_count", 100)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseDisclosureList)
                .doOnError(error -> log.error("DART API 호출 실패: {}", error.getMessage()));
    }
    
    /**
     * 종목명으로 회사 코드 검색
     * @param corpName 회사명
     * @return 회사 코드
     */
    public Mono<String> getCorpCodeByName(String corpName) {
        // DART API는 별도의 회사 코드 조회 API를 제공하지 않으므로
        // 사전에 다운로드한 회사 코드 맵핑 파일을 사용하거나
        // 별도의 DB 테이블에 저장해서 사용해야 합니다.
        // 여기서는 간단히 하드코딩된 주요 종목만 처리합니다.
        
        String corpCode = getHardcodedCorpCode(corpName);
        if (corpCode != null) {
            return Mono.just(corpCode);
        }
        
        return Mono.error(new RuntimeException("회사 코드를 찾을 수 없습니다: " + corpName));
    }
    
    /**
     * 최근 공시 조회 (전체 시장)
     * @param date 조회일
     * @return 공시 목록
     */
    public Flux<DisclosureDto> getRecentDisclosures(LocalDate date) {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("bgn_de", date.format(DATE_FORMATTER))
                        .queryParam("end_de", date.format(DATE_FORMATTER))
                        .queryParam("page_count", 100)
                        .queryParam("sort", "date")  // 날짜순 정렬
                        .queryParam("sort_mth", "desc") // 내림차순
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(response -> Flux.fromIterable(parseDisclosureList(response)))
                .doOnError(error -> log.error("최근 공시 조회 실패: {}", error.getMessage()));
    }
    
    /**
     * 주요사항보고서 조회
     * @param corpCode 회사 고유번호
     * @param beginDate 시작일
     * @param endDate 종료일
     * @return 주요사항보고서 목록
     */
    public Mono<List<DisclosureDto>> getMajorReports(String corpCode, LocalDate beginDate, LocalDate endDate) {
        return dartWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/list.json")
                        .queryParam("crtfc_key", apiKey)
                        .queryParam("corp_code", corpCode)
                        .queryParam("bgn_de", beginDate.format(DATE_FORMATTER))
                        .queryParam("end_de", endDate.format(DATE_FORMATTER))
                        .queryParam("pblntf_ty", "B") // 주요사항보고서
                        .queryParam("page_count", 100)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseDisclosureList)
                .doOnError(error -> log.error("주요사항보고서 조회 실패: {}", error.getMessage()));
    }
    
    private List<DisclosureDto> parseDisclosureList(String jsonResponse) {
        List<DisclosureDto> disclosures = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            if (!"000".equals(root.path("status").asText())) {
                log.warn("DART API 오류 응답: {}", root.path("message").asText());
                return disclosures;
            }
            
            JsonNode list = root.path("list");
            if (list.isArray()) {
                for (JsonNode item : list) {
                    DisclosureDto disclosure = parseDisclosureItem(item);
                    disclosures.add(disclosure);
                }
            }
        } catch (Exception e) {
            log.error("공시 목록 파싱 실패: {}", e.getMessage());
        }
        
        return disclosures;
    }
    
    private DisclosureDto parseDisclosureItem(JsonNode item) {
        String rcpDt = item.path("rcpt_dt").asText();
        LocalDateTime receivedDate = parseDateTime(rcpDt);
        
        return DisclosureDto.builder()
                .corpCode(item.path("corp_code").asText())
                .corpName(item.path("corp_name").asText())
                .reportNumber(item.path("rcept_no").asText())
                .reportName(item.path("report_nm").asText())
                .submitter(item.path("flr_nm").asText())
                .receivedDate(receivedDate)
                .reportType(getReportType(item.path("report_nm").asText()))
                .viewUrl(buildViewUrl(item.path("rcept_no").asText()))
                .isImportant(isImportantReport(item.path("report_nm").asText()))
                .build();
    }
    
    private LocalDateTime parseDateTime(String dateStr) {
        try {
            // YYYYMMDD 형식을 LocalDateTime으로 변환
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            return date.atStartOfDay();
        } catch (Exception e) {
            log.error("날짜 파싱 실패: {}", dateStr);
            return LocalDateTime.now();
        }
    }
    
    private String buildViewUrl(String rceptNo) {
        return String.format("http://dart.fss.or.kr/dsaf001/main.do?rcpNo=%s", rceptNo);
    }
    
    private String getReportType(String reportName) {
        if (reportName.contains("사업보고서") || reportName.contains("반기보고서") || reportName.contains("분기보고서")) {
            return "정기공시";
        } else if (reportName.contains("주요사항") || reportName.contains("임시공시")) {
            return "주요사항보고";
        } else if (reportName.contains("유상증자") || reportName.contains("무상증자") || reportName.contains("합병")) {
            return "자본시장법";
        } else {
            return "기타";
        }
    }
    
    private boolean isImportantReport(String reportName) {
        String[] importantKeywords = {
            "유상증자", "무상증자", "합병", "분할", "주식분할", "감자",
            "영업정지", "거래정지", "관리종목", "상장폐지", "감리",
            "최대주주변경", "대표이사변경", "사업목적변경"
        };
        
        for (String keyword : importantKeywords) {
            if (reportName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private String getHardcodedCorpCode(String corpName) {
        // 주요 종목의 DART 회사 코드 (실제로는 DB나 파일에서 관리해야 함)
        return switch (corpName) {
            case "삼성전자" -> "00126380";
            case "SK하이닉스" -> "00164779";
            case "NAVER", "네이버" -> "00187038";
            case "카카오" -> "00258801";
            case "LG화학" -> "00356361";
            case "삼성바이오로직스" -> "00808511";
            case "셀트리온" -> "00421045";
            case "현대차", "현대자동차" -> "00401731";
            case "기아", "기아자동차" -> "00190980";
            case "삼성SDI" -> "00100840";
            default -> null;
        };
    }
}