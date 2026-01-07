package com.trading.journal.service;

import com.opencsv.CSVWriter;
import com.trading.journal.dto.*;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 데이터 내보내기 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TransactionRepository transactionRepository;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final GoalService goalService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== 거래 내역 내보내기 ====================

    /**
     * 거래 내역 Excel 내보내기
     */
    public byte[] exportTransactionsToExcel(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactions(startDate, endDate);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("거래 내역");

            // 스타일 생성
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // 헤더 행
            Row headerRow = sheet.createRow(0);
            String[] headers = {"거래일", "종목코드", "종목명", "거래유형", "수량", "단가", "거래금액", "수수료", "메모"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 데이터 행
            int rowNum = 1;
            for (Transaction tx : transactions) {
                Row row = sheet.createRow(rowNum++);

                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATETIME_FORMAT) : "");
                dateCell.setCellStyle(dateStyle);

                row.createCell(1).setCellValue(tx.getStock() != null ? tx.getStock().getSymbol() : "");
                row.createCell(2).setCellValue(tx.getStock() != null ? tx.getStock().getName() : "");
                row.createCell(3).setCellValue(getTransactionTypeLabel(tx.getType()));
                row.createCell(4).setCellValue(tx.getQuantity() != null ? tx.getQuantity().doubleValue() : 0);

                Cell priceCell = row.createCell(5);
                priceCell.setCellValue(tx.getPrice() != null ? tx.getPrice().doubleValue() : 0);
                priceCell.setCellStyle(currencyStyle);

                Cell amountCell = row.createCell(6);
                BigDecimal amount = calculateAmount(tx);
                amountCell.setCellValue(amount != null ? amount.doubleValue() : 0);
                amountCell.setCellStyle(currencyStyle);

                Cell feeCell = row.createCell(7);
                feeCell.setCellValue(tx.getCommission() != null ? tx.getCommission().doubleValue() : 0);
                feeCell.setCellStyle(currencyStyle);

                row.createCell(8).setCellValue(tx.getNotes() != null ? tx.getNotes() : "");
            }

            // 열 너비 자동 조정
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 필터 추가
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("거래 내역 Excel 내보내기 실패", e);
            throw new RuntimeException("Excel 내보내기 실패: " + e.getMessage());
        }
    }

    /**
     * 거래 내역 CSV 내보내기
     */
    public byte[] exportTransactionsToCsv(LocalDate startDate, LocalDate endDate) {
        List<Transaction> transactions = getTransactions(startDate, endDate);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            // BOM 추가 (Excel 한글 호환)
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);

            // 헤더
            String[] headers = {"거래일", "종목코드", "종목명", "거래유형", "수량", "단가", "거래금액", "수수료", "메모"};
            writer.writeNext(headers);

            // 데이터
            for (Transaction tx : transactions) {
                BigDecimal amount = calculateAmount(tx);
                String[] row = {
                        tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATETIME_FORMAT) : "",
                        tx.getStock() != null ? tx.getStock().getSymbol() : "",
                        tx.getStock() != null ? tx.getStock().getName() : "",
                        getTransactionTypeLabel(tx.getType()),
                        tx.getQuantity() != null ? tx.getQuantity().toString() : "0",
                        tx.getPrice() != null ? tx.getPrice().toString() : "0",
                        amount != null ? amount.toString() : "0",
                        tx.getCommission() != null ? tx.getCommission().toString() : "0",
                        tx.getNotes() != null ? tx.getNotes() : ""
                };
                writer.writeNext(row);
            }

            writer.flush();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("거래 내역 CSV 내보내기 실패", e);
            throw new RuntimeException("CSV 내보내기 실패: " + e.getMessage());
        }
    }

    // ==================== 포트폴리오 분석 내보내기 ====================

    /**
     * 포트폴리오 분석 Excel 내보내기
     */
    public byte[] exportPortfolioAnalysisToExcel() {
        try (Workbook workbook = new XSSFWorkbook()) {
            // 스타일
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            // 1. 포트폴리오 요약 시트
            createPortfolioSummarySheet(workbook, headerStyle, currencyStyle, percentStyle);

            // 2. 보유 종목 시트
            createHoldingsSheet(workbook, headerStyle, currencyStyle, percentStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("포트폴리오 분석 Excel 내보내기 실패", e);
            throw new RuntimeException("Excel 내보내기 실패: " + e.getMessage());
        }
    }

    private void createPortfolioSummarySheet(Workbook workbook, CellStyle headerStyle,
                                              CellStyle currencyStyle, CellStyle percentStyle) {
        Sheet sheet = workbook.createSheet("포트폴리오 요약");
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        int rowNum = 0;

        // 제목
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("포트폴리오 요약");
        titleCell.setCellStyle(headerStyle);

        rowNum++; // 빈 행

        // 요약 데이터
        addSummaryRow(sheet, rowNum++, "총 투자금액", summary.getTotalInvestment(), currencyStyle);
        addSummaryRow(sheet, rowNum++, "현재 평가금액", summary.getTotalCurrentValue(), currencyStyle);
        addSummaryRow(sheet, rowNum++, "총 손익", summary.getTotalProfitLoss(), currencyStyle);
        addSummaryRowPercent(sheet, rowNum++, "수익률", summary.getTotalProfitLossPercent(), percentStyle);
        addSummaryRow(sheet, rowNum++, "실현 손익", summary.getTotalRealizedPnl(), currencyStyle);

        // 미실현 손익 계산
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        if (summary.getTotalProfitLoss() != null && summary.getTotalRealizedPnl() != null) {
            unrealizedPnl = summary.getTotalProfitLoss().subtract(summary.getTotalRealizedPnl());
        }
        addSummaryRow(sheet, rowNum++, "미실현 손익", unrealizedPnl, currencyStyle);

        int holdingCount = summary.getHoldings() != null ? summary.getHoldings().size() : 0;
        addSummaryRow(sheet, rowNum++, "보유 종목 수", BigDecimal.valueOf(holdingCount), null);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createHoldingsSheet(Workbook workbook, CellStyle headerStyle,
                                      CellStyle currencyStyle, CellStyle percentStyle) {
        Sheet sheet = workbook.createSheet("보유 종목");
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();
        List<PortfolioDto> holdings = summary.getHoldings() != null ? summary.getHoldings() : List.of();

        // 헤더
        Row headerRow = sheet.createRow(0);
        String[] headers = {"종목코드", "종목명", "수량", "평균단가", "현재가", "평가금액", "손익", "수익률"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (PortfolioDto holding : holdings) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(holding.getStockSymbol() != null ? holding.getStockSymbol() : "");
            row.createCell(1).setCellValue(holding.getStockName() != null ? holding.getStockName() : "");
            row.createCell(2).setCellValue(holding.getQuantity() != null ? holding.getQuantity().doubleValue() : 0);

            Cell avgPriceCell = row.createCell(3);
            avgPriceCell.setCellValue(holding.getAveragePrice() != null ? holding.getAveragePrice().doubleValue() : 0);
            avgPriceCell.setCellStyle(currencyStyle);

            Cell currentPriceCell = row.createCell(4);
            currentPriceCell.setCellValue(holding.getCurrentPrice() != null ? holding.getCurrentPrice().doubleValue() : 0);
            currentPriceCell.setCellStyle(currencyStyle);

            Cell valueCell = row.createCell(5);
            valueCell.setCellValue(holding.getCurrentValue() != null ? holding.getCurrentValue().doubleValue() : 0);
            valueCell.setCellStyle(currencyStyle);

            Cell pnlCell = row.createCell(6);
            pnlCell.setCellValue(holding.getProfitLoss() != null ? holding.getProfitLoss().doubleValue() : 0);
            pnlCell.setCellStyle(currencyStyle);

            Cell pnlPctCell = row.createCell(7);
            pnlPctCell.setCellValue(holding.getProfitLossPercent() != null ? holding.getProfitLossPercent().doubleValue() / 100 : 0);
            pnlPctCell.setCellStyle(percentStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==================== 목표 진행 상황 내보내기 ====================

    /**
     * 목표 진행 상황 Excel 내보내기
     */
    public byte[] exportGoalsToExcel() {
        List<GoalDto> goals = goalService.getAllGoals();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("목표 현황");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            // 헤더
            Row headerRow = sheet.createRow(0);
            String[] headers = {"목표명", "유형", "상태", "시작값", "현재값", "목표값", "진행률", "시작일", "마감일", "남은일수"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (GoalDto goal : goals) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(goal.getName());
                row.createCell(1).setCellValue(goal.getGoalTypeLabel());
                row.createCell(2).setCellValue(goal.getStatusLabel());

                Cell startCell = row.createCell(3);
                startCell.setCellValue(goal.getStartValue() != null ? goal.getStartValue().doubleValue() : 0);
                startCell.setCellStyle(currencyStyle);

                Cell currentCell = row.createCell(4);
                currentCell.setCellValue(goal.getCurrentValue() != null ? goal.getCurrentValue().doubleValue() : 0);
                currentCell.setCellStyle(currencyStyle);

                Cell targetCell = row.createCell(5);
                targetCell.setCellValue(goal.getTargetValue() != null ? goal.getTargetValue().doubleValue() : 0);
                targetCell.setCellStyle(currencyStyle);

                Cell progressCell = row.createCell(6);
                progressCell.setCellValue(goal.getProgressPercent() != null ? goal.getProgressPercent().doubleValue() / 100 : 0);
                progressCell.setCellStyle(percentStyle);

                Cell startDateCell = row.createCell(7);
                startDateCell.setCellValue(goal.getStartDate() != null ? goal.getStartDate().format(DATE_FORMAT) : "");
                startDateCell.setCellStyle(dateStyle);

                Cell deadlineCell = row.createCell(8);
                deadlineCell.setCellValue(goal.getDeadline() != null ? goal.getDeadline().format(DATE_FORMAT) : "");
                deadlineCell.setCellStyle(dateStyle);

                row.createCell(9).setCellValue(goal.getDaysRemaining() != null ? goal.getDaysRemaining() : 0);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("목표 현황 Excel 내보내기 실패", e);
            throw new RuntimeException("Excel 내보내기 실패: " + e.getMessage());
        }
    }

    // ==================== 종합 리포트 내보내기 ====================

    /**
     * 종합 리포트 Excel 내보내기 (모든 데이터 포함)
     */
    public byte[] exportFullReportToExcel(LocalDate startDate, LocalDate endDate) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // 1. 포트폴리오 요약
            createPortfolioSummarySheet(workbook, headerStyle, currencyStyle, percentStyle);

            // 2. 보유 종목
            createHoldingsSheet(workbook, headerStyle, currencyStyle, percentStyle);

            // 3. 거래 내역
            createTransactionsSheet(workbook, headerStyle, currencyStyle, dateStyle, startDate, endDate);

            // 4. 목표 현황
            createGoalsSheet(workbook, headerStyle, currencyStyle, percentStyle, dateStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("종합 리포트 Excel 내보내기 실패", e);
            throw new RuntimeException("Excel 내보내기 실패: " + e.getMessage());
        }
    }

    private void createTransactionsSheet(Workbook workbook, CellStyle headerStyle,
                                          CellStyle currencyStyle, CellStyle dateStyle,
                                          LocalDate startDate, LocalDate endDate) {
        Sheet sheet = workbook.createSheet("거래 내역");
        List<Transaction> transactions = getTransactions(startDate, endDate);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"거래일", "종목코드", "종목명", "거래유형", "수량", "단가", "거래금액", "수수료"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Transaction tx : transactions) {
            Row row = sheet.createRow(rowNum++);

            Cell dateCell = row.createCell(0);
            dateCell.setCellValue(tx.getTransactionDate() != null ? tx.getTransactionDate().format(DATETIME_FORMAT) : "");
            dateCell.setCellStyle(dateStyle);

            row.createCell(1).setCellValue(tx.getStock() != null ? tx.getStock().getSymbol() : "");
            row.createCell(2).setCellValue(tx.getStock() != null ? tx.getStock().getName() : "");
            row.createCell(3).setCellValue(getTransactionTypeLabel(tx.getType()));
            row.createCell(4).setCellValue(tx.getQuantity() != null ? tx.getQuantity().doubleValue() : 0);

            Cell priceCell = row.createCell(5);
            priceCell.setCellValue(tx.getPrice() != null ? tx.getPrice().doubleValue() : 0);
            priceCell.setCellStyle(currencyStyle);

            Cell amountCell = row.createCell(6);
            BigDecimal amount = calculateAmount(tx);
            amountCell.setCellValue(amount != null ? amount.doubleValue() : 0);
            amountCell.setCellStyle(currencyStyle);

            Cell feeCell = row.createCell(7);
            feeCell.setCellValue(tx.getCommission() != null ? tx.getCommission().doubleValue() : 0);
            feeCell.setCellStyle(currencyStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createGoalsSheet(Workbook workbook, CellStyle headerStyle,
                                   CellStyle currencyStyle, CellStyle percentStyle, CellStyle dateStyle) {
        Sheet sheet = workbook.createSheet("목표 현황");
        List<GoalDto> goals = goalService.getAllGoals();

        Row headerRow = sheet.createRow(0);
        String[] headers = {"목표명", "유형", "상태", "현재값", "목표값", "진행률", "마감일"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (GoalDto goal : goals) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(goal.getName());
            row.createCell(1).setCellValue(goal.getGoalTypeLabel());
            row.createCell(2).setCellValue(goal.getStatusLabel());

            Cell currentCell = row.createCell(3);
            currentCell.setCellValue(goal.getCurrentValue() != null ? goal.getCurrentValue().doubleValue() : 0);
            currentCell.setCellStyle(currencyStyle);

            Cell targetCell = row.createCell(4);
            targetCell.setCellValue(goal.getTargetValue() != null ? goal.getTargetValue().doubleValue() : 0);
            targetCell.setCellStyle(currencyStyle);

            Cell progressCell = row.createCell(5);
            progressCell.setCellValue(goal.getProgressPercent() != null ? goal.getProgressPercent().doubleValue() / 100 : 0);
            progressCell.setCellStyle(percentStyle);

            Cell deadlineCell = row.createCell(6);
            deadlineCell.setCellValue(goal.getDeadline() != null ? goal.getDeadline().format(DATE_FORMAT) : "");
            deadlineCell.setCellStyle(dateStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==================== 헬퍼 메서드 ====================

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

    private String getTransactionTypeLabel(TransactionType type) {
        if (type == null) return "";
        return switch (type) {
            case BUY -> "매수";
            case SELL -> "매도";
        };
    }

    private void addSummaryRow(Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value.doubleValue() : 0);
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private void addSummaryRowPercent(Sheet sheet, int rowNum, String label, BigDecimal value, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value.doubleValue() / 100 : 0);
        if (style != null) {
            valueCell.setCellStyle(style);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
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
        style.setDataFormat(format.getFormat("#,##0"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
}
