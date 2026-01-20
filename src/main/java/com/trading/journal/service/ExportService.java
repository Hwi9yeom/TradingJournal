package com.trading.journal.service;

import com.opencsv.CSVWriter;
import com.trading.journal.dto.*;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.exception.ExportException;
import com.trading.journal.repository.TransactionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 데이터 내보내기 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TransactionRepository transactionRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final GoalService goalService;

    // ==================== 상수 정의 ====================

    // 날짜 포맷
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 시트 이름
    private static final String SHEET_TRANSACTIONS = "거래 내역";
    private static final String SHEET_PORTFOLIO_SUMMARY = "포트폴리오 요약";
    private static final String SHEET_HOLDINGS = "보유 종목";
    private static final String SHEET_GOALS = "목표 현황";

    // 헤더 배열
    private static final String[] HEADERS_TRANSACTIONS = {
        "거래일", "종목코드", "종목명", "거래유형", "수량", "단가", "거래금액", "수수료", "메모"
    };
    private static final String[] HEADERS_TRANSACTIONS_SHORT = {
        "거래일", "종목코드", "종목명", "거래유형", "수량", "단가", "거래금액", "수수료"
    };
    private static final String[] HEADERS_HOLDINGS = {
        "종목코드", "종목명", "수량", "평균단가", "현재가", "평가금액", "손익", "수익률"
    };
    private static final String[] HEADERS_GOALS_FULL = {
        "목표명", "유형", "상태", "시작값", "현재값", "목표값", "진행률", "시작일", "마감일", "남은일수"
    };
    private static final String[] HEADERS_GOALS_SHORT = {
        "목표명", "유형", "상태", "현재값", "목표값", "진행률", "마감일"
    };

    // 셀 스타일 관련
    private static final String CURRENCY_FORMAT = "#,##0";
    private static final String PERCENT_FORMAT = "0.00%";
    private static final short HEADER_BG_COLOR = IndexedColors.GREY_25_PERCENT.getIndex();

    // CSV BOM (Excel 한글 호환)
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // 내보내기 타입
    private static final String EXPORT_TYPE_EXCEL = "Excel";
    private static final String EXPORT_TYPE_CSV = "CSV";

    // ==================== 거래 내역 내보내기 ====================

    /** 거래 내역 Excel 내보내기 */
    public byte[] exportTransactionsToExcel(LocalDate startDate, LocalDate endDate) {
        log.debug("거래 내역 Excel 내보내기 시작 - 기간: {} ~ {}", startDate, endDate);

        List<Transaction> transactions = getTransactions(startDate, endDate);
        log.debug("내보낼 거래 내역 수: {}", transactions.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_TRANSACTIONS);
            ExcelStyles styles = createExcelStyles(workbook);

            createHeaderRow(sheet, HEADERS_TRANSACTIONS, styles.header());

            int rowNum = 1;
            for (Transaction tx : transactions) {
                createTransactionRow(sheet, rowNum++, tx, styles, true);
            }

            autoSizeColumns(sheet, HEADERS_TRANSACTIONS.length);
            addAutoFilter(sheet, HEADERS_TRANSACTIONS.length);

            byte[] result = writeWorkbook(workbook);
            log.debug("거래 내역 Excel 내보내기 완료 - 크기: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("거래 내역 Excel 내보내기 실패", e);
            throw new ExportException(EXPORT_TYPE_EXCEL, "거래 내역", e.getMessage(), e);
        }
    }

    /** 거래 내역 CSV 내보내기 */
    public byte[] exportTransactionsToCsv(LocalDate startDate, LocalDate endDate) {
        log.debug("거래 내역 CSV 내보내기 시작 - 기간: {} ~ {}", startDate, endDate);

        List<Transaction> transactions = getTransactions(startDate, endDate);
        log.debug("내보낼 거래 내역 수: {}", transactions.size());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                CSVWriter writer =
                        new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            out.write(UTF8_BOM);
            writer.writeNext(HEADERS_TRANSACTIONS);

            for (Transaction tx : transactions) {
                writer.writeNext(createTransactionCsvRow(tx));
            }

            writer.flush();
            byte[] result = out.toByteArray();
            log.debug("거래 내역 CSV 내보내기 완료 - 크기: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("거래 내역 CSV 내보내기 실패", e);
            throw new ExportException(EXPORT_TYPE_CSV, "거래 내역", e.getMessage(), e);
        }
    }

    // ==================== 포트폴리오 분석 내보내기 ====================

    /** 포트폴리오 분석 Excel 내보내기 */
    public byte[] exportPortfolioAnalysisToExcel() {
        log.debug("포트폴리오 분석 Excel 내보내기 시작");

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = createExcelStyles(workbook);

            createPortfolioSummarySheet(workbook, styles);
            createHoldingsSheet(workbook, styles);

            byte[] result = writeWorkbook(workbook);
            log.debug("포트폴리오 분석 Excel 내보내기 완료 - 크기: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("포트폴리오 분석 Excel 내보내기 실패", e);
            throw new ExportException(EXPORT_TYPE_EXCEL, "포트폴리오 분석", e.getMessage(), e);
        }
    }

    private void createPortfolioSummarySheet(Workbook workbook, ExcelStyles styles) {
        Sheet sheet = workbook.createSheet(SHEET_PORTFOLIO_SUMMARY);
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        int rowNum = 0;

        // 제목
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(SHEET_PORTFOLIO_SUMMARY);
        titleCell.setCellStyle(styles.header());

        rowNum++; // 빈 행

        // 요약 데이터
        addSummaryRow(sheet, rowNum++, "총 투자금액", summary.getTotalInvestment(), styles.currency());
        addSummaryRow(
                sheet, rowNum++, "현재 평가금액", summary.getTotalCurrentValue(), styles.currency());
        addSummaryRow(sheet, rowNum++, "총 손익", summary.getTotalProfitLoss(), styles.currency());
        addSummaryRowPercent(
                sheet, rowNum++, "수익률", summary.getTotalProfitLossPercent(), styles.percent());
        addSummaryRow(sheet, rowNum++, "실현 손익", summary.getTotalRealizedPnl(), styles.currency());

        BigDecimal unrealizedPnl = calculateUnrealizedPnl(summary);
        addSummaryRow(sheet, rowNum++, "미실현 손익", unrealizedPnl, styles.currency());

        int holdingCount = summary.getHoldings() != null ? summary.getHoldings().size() : 0;
        addSummaryRow(sheet, rowNum, "보유 종목 수", BigDecimal.valueOf(holdingCount), null);

        autoSizeColumns(sheet, 2);
    }

    private void createHoldingsSheet(Workbook workbook, ExcelStyles styles) {
        Sheet sheet = workbook.createSheet(SHEET_HOLDINGS);
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();
        List<PortfolioDto> holdings =
                summary.getHoldings() != null ? summary.getHoldings() : List.of();

        createHeaderRow(sheet, HEADERS_HOLDINGS, styles.header());

        int rowNum = 1;
        for (PortfolioDto holding : holdings) {
            createHoldingRow(sheet, rowNum++, holding, styles);
        }

        autoSizeColumns(sheet, HEADERS_HOLDINGS.length);
    }

    // ==================== 목표 진행 상황 내보내기 ====================

    /** 목표 진행 상황 Excel 내보내기 */
    public byte[] exportGoalsToExcel() {
        log.debug("목표 현황 Excel 내보내기 시작");

        List<GoalDto> goals = goalService.getAllGoals();
        log.debug("내보낼 목표 수: {}", goals.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_GOALS);
            ExcelStyles styles = createExcelStyles(workbook);

            createHeaderRow(sheet, HEADERS_GOALS_FULL, styles.header());

            int rowNum = 1;
            for (GoalDto goal : goals) {
                createGoalRowFull(sheet, rowNum++, goal, styles);
            }

            autoSizeColumns(sheet, HEADERS_GOALS_FULL.length);

            byte[] result = writeWorkbook(workbook);
            log.debug("목표 현황 Excel 내보내기 완료 - 크기: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("목표 현황 Excel 내보내기 실패", e);
            throw new ExportException(EXPORT_TYPE_EXCEL, "목표 현황", e.getMessage(), e);
        }
    }

    // ==================== 종합 리포트 내보내기 ====================

    /** 종합 리포트 Excel 내보내기 (모든 데이터 포함) */
    public byte[] exportFullReportToExcel(LocalDate startDate, LocalDate endDate) {
        log.debug("종합 리포트 Excel 내보내기 시작 - 기간: {} ~ {}", startDate, endDate);

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = createExcelStyles(workbook);

            createPortfolioSummarySheet(workbook, styles);
            createHoldingsSheet(workbook, styles);
            createTransactionsSheet(workbook, styles, startDate, endDate);
            createGoalsSheet(workbook, styles);

            byte[] result = writeWorkbook(workbook);
            log.debug("종합 리포트 Excel 내보내기 완료 - 크기: {} bytes", result.length);
            return result;

        } catch (IOException e) {
            log.error("종합 리포트 Excel 내보내기 실패", e);
            throw new ExportException(EXPORT_TYPE_EXCEL, "종합 리포트", e.getMessage(), e);
        }
    }

    private void createTransactionsSheet(
            Workbook workbook, ExcelStyles styles, LocalDate startDate, LocalDate endDate) {
        Sheet sheet = workbook.createSheet(SHEET_TRANSACTIONS);
        List<Transaction> transactions = getTransactions(startDate, endDate);

        createHeaderRow(sheet, HEADERS_TRANSACTIONS_SHORT, styles.header());

        int rowNum = 1;
        for (Transaction tx : transactions) {
            createTransactionRow(sheet, rowNum++, tx, styles, false);
        }

        autoSizeColumns(sheet, HEADERS_TRANSACTIONS_SHORT.length);
    }

    private void createGoalsSheet(Workbook workbook, ExcelStyles styles) {
        Sheet sheet = workbook.createSheet(SHEET_GOALS);
        List<GoalDto> goals = goalService.getAllGoals();

        createHeaderRow(sheet, HEADERS_GOALS_SHORT, styles.header());

        int rowNum = 1;
        for (GoalDto goal : goals) {
            createGoalRowShort(sheet, rowNum++, goal, styles);
        }

        autoSizeColumns(sheet, HEADERS_GOALS_SHORT.length);
    }

    // ==================== 행 생성 헬퍼 메서드 ====================

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createTransactionRow(
            Sheet sheet, int rowNum, Transaction tx, ExcelStyles styles, boolean includeMemo) {
        Row row = sheet.createRow(rowNum);
        int col = 0;

        createDateCell(row, col++, tx.getTransactionDate(), styles.date());
        row.createCell(col++).setCellValue(getStockSymbol(tx));
        row.createCell(col++).setCellValue(getStockName(tx));
        row.createCell(col++).setCellValue(getTransactionTypeLabel(tx.getType()));
        row.createCell(col++).setCellValue(getDoubleValue(tx.getQuantity()));
        createCurrencyCell(row, col++, tx.getPrice(), styles.currency());
        createCurrencyCell(row, col++, calculateAmount(tx), styles.currency());
        createCurrencyCell(row, col++, tx.getCommission(), styles.currency());

        if (includeMemo) {
            row.createCell(col).setCellValue(tx.getNotes() != null ? tx.getNotes() : "");
        }
    }

    private String[] createTransactionCsvRow(Transaction tx) {
        BigDecimal amount = calculateAmount(tx);
        return new String[] {
            formatDateTime(tx.getTransactionDate()),
            getStockSymbol(tx),
            getStockName(tx),
            getTransactionTypeLabel(tx.getType()),
            getStringValue(tx.getQuantity()),
            getStringValue(tx.getPrice()),
            getStringValue(amount),
            getStringValue(tx.getCommission()),
            tx.getNotes() != null ? tx.getNotes() : ""
        };
    }

    private void createHoldingRow(
            Sheet sheet, int rowNum, PortfolioDto holding, ExcelStyles styles) {
        Row row = sheet.createRow(rowNum);

        row.createCell(0)
                .setCellValue(holding.getStockSymbol() != null ? holding.getStockSymbol() : "");
        row.createCell(1)
                .setCellValue(holding.getStockName() != null ? holding.getStockName() : "");
        row.createCell(2).setCellValue(getDoubleValue(holding.getQuantity()));
        createCurrencyCell(row, 3, holding.getAveragePrice(), styles.currency());
        createCurrencyCell(row, 4, holding.getCurrentPrice(), styles.currency());
        createCurrencyCell(row, 5, holding.getCurrentValue(), styles.currency());
        createCurrencyCell(row, 6, holding.getProfitLoss(), styles.currency());
        createPercentCell(row, 7, holding.getProfitLossPercent(), styles.percent());
    }

    private void createGoalRowFull(Sheet sheet, int rowNum, GoalDto goal, ExcelStyles styles) {
        Row row = sheet.createRow(rowNum);
        int col = 0;

        row.createCell(col++).setCellValue(goal.getName());
        row.createCell(col++).setCellValue(goal.getGoalTypeLabel());
        row.createCell(col++).setCellValue(goal.getStatusLabel());
        createCurrencyCell(row, col++, goal.getStartValue(), styles.currency());
        createCurrencyCell(row, col++, goal.getCurrentValue(), styles.currency());
        createCurrencyCell(row, col++, goal.getTargetValue(), styles.currency());
        createPercentCell(row, col++, goal.getProgressPercent(), styles.percent());
        createDateCell(row, col++, goal.getStartDate(), styles.date());
        createDateCell(row, col++, goal.getDeadline(), styles.date());
        row.createCell(col)
                .setCellValue(goal.getDaysRemaining() != null ? goal.getDaysRemaining() : 0);
    }

    private void createGoalRowShort(Sheet sheet, int rowNum, GoalDto goal, ExcelStyles styles) {
        Row row = sheet.createRow(rowNum);

        row.createCell(0).setCellValue(goal.getName());
        row.createCell(1).setCellValue(goal.getGoalTypeLabel());
        row.createCell(2).setCellValue(goal.getStatusLabel());
        createCurrencyCell(row, 3, goal.getCurrentValue(), styles.currency());
        createCurrencyCell(row, 4, goal.getTargetValue(), styles.currency());
        createPercentCell(row, 5, goal.getProgressPercent(), styles.percent());
        createDateCell(row, 6, goal.getDeadline(), styles.date());
    }

    // ==================== 셀 생성 헬퍼 메서드 ====================

    private void createCurrencyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(getDoubleValue(value));
        cell.setCellStyle(style);
    }

    private void createPercentCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() / 100 : 0);
        cell.setCellStyle(style);
    }

    private void createDateCell(Row row, int col, LocalDateTime dateTime, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(formatDateTime(dateTime));
        cell.setCellStyle(style);
    }

    private void createDateCell(Row row, int col, LocalDate date, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(date != null ? date.format(DATE_FORMAT) : "");
        cell.setCellStyle(style);
    }

    // ==================== 스타일 관련 ====================

    /** Excel 스타일 묶음 레코드 */
    private record ExcelStyles(
            CellStyle header, CellStyle date, CellStyle currency, CellStyle percent) {}

    private ExcelStyles createExcelStyles(Workbook workbook) {
        return new ExcelStyles(
                createHeaderStyle(workbook),
                createDateStyle(workbook),
                createCurrencyStyle(workbook),
                createPercentStyle(workbook));
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(HEADER_BG_COLOR);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(CURRENCY_FORMAT));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat(PERCENT_FORMAT));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    // ==================== 유틸리티 메서드 ====================

    private List<Transaction> getTransactions(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
            return transactionRepository.findByDateRange(startDateTime, endDateTime);
        }
        return transactionRepository.findAll();
    }

    private BigDecimal calculateAmount(Transaction tx) {
        if (tx.getQuantity() != null && tx.getPrice() != null) {
            return tx.getQuantity().multiply(tx.getPrice());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateUnrealizedPnl(PortfolioSummaryDto summary) {
        if (summary.getTotalProfitLoss() != null && summary.getTotalRealizedPnl() != null) {
            return summary.getTotalProfitLoss().subtract(summary.getTotalRealizedPnl());
        }
        return BigDecimal.ZERO;
    }

    private String getTransactionTypeLabel(TransactionType type) {
        if (type == null) return "";
        return switch (type) {
            case BUY -> "매수";
            case SELL -> "매도";
        };
    }

    private String getStockSymbol(Transaction tx) {
        return tx.getStock() != null ? tx.getStock().getSymbol() : "";
    }

    private String getStockName(Transaction tx) {
        return tx.getStock() != null ? tx.getStock().getName() : "";
    }

    private double getDoubleValue(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }

    private String getStringValue(BigDecimal value) {
        return value != null ? value.toString() : "0";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMAT) : "";
    }

    private void addSummaryRow(
            Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(getDoubleValue(value));
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private void addSummaryRowPercent(
            Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value.doubleValue() / 100 : 0);
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void addAutoFilter(Sheet sheet, int columnCount) {
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columnCount - 1));
    }

    private byte[] writeWorkbook(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
