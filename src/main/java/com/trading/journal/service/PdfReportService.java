package com.trading.journal.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.trading.journal.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PDF 리포트 생성 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final AnalysisService analysisService;
    private final PortfolioAnalysisService portfolioAnalysisService;
    private final RiskMetricsService riskMetricsService;
    private final ChartGenerationService chartGenerationService;

    // 색상 설정
    private static final Color PRIMARY_COLOR = new Color(13, 110, 253);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color DANGER_COLOR = new Color(239, 68, 68);
    private static final Color HEADER_BG = new Color(248, 249, 250);
    private static final Color BORDER_COLOR = new Color(222, 226, 230);

    private Font titleFont;
    private Font headerFont;
    private Font subHeaderFont;
    private Font normalFont;
    private Font boldFont;
    private Font smallFont;

    /**
     * 포트폴리오 성과 리포트 생성
     */
    public byte[] generatePortfolioReport(Long accountId, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            // 페이지 이벤트 핸들러 (헤더/푸터)
            writer.setPageEvent(new ReportHeaderFooter());

            document.open();
            initializeFonts();

            // 1. 표지
            addCoverPage(document, startDate, endDate);
            document.newPage();

            // 2. 요약 섹션
            addSummarySection(document, accountId, startDate, endDate);
            document.newPage();

            // 3. 포트폴리오 구성
            addPortfolioComposition(document, accountId);
            document.newPage();

            // 4. 성과 분석
            addPerformanceAnalysis(document, accountId, startDate, endDate);
            document.newPage();

            // 5. 리스크 분석
            addRiskAnalysis(document, accountId, startDate, endDate);
            document.newPage();

            // 6. 거래 내역 요약
            addTradeSummary(document, accountId, startDate, endDate);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("PDF 리포트 생성 실패", e);
            throw new RuntimeException("PDF 리포트 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void initializeFonts() {
        try {
            // 시스템 폰트 사용 (한글 지원)
            BaseFont baseFont = BaseFont.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
            titleFont = new Font(baseFont, 24, Font.BOLD, PRIMARY_COLOR);
            headerFont = new Font(baseFont, 16, Font.BOLD, Color.BLACK);
            subHeaderFont = new Font(baseFont, 12, Font.BOLD, Color.DARK_GRAY);
            normalFont = new Font(baseFont, 10, Font.NORMAL, Color.BLACK);
            boldFont = new Font(baseFont, 10, Font.BOLD, Color.BLACK);
            smallFont = new Font(baseFont, 8, Font.NORMAL, Color.GRAY);
        } catch (Exception e) {
            // Fallback to Helvetica
            titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, PRIMARY_COLOR);
            headerFont = new Font(Font.HELVETICA, 16, Font.BOLD, Color.BLACK);
            subHeaderFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            boldFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
            smallFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
        }
    }

    private void addCoverPage(Document document, LocalDate startDate, LocalDate endDate) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // 제목
        Paragraph title = new Paragraph("Portfolio Performance Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        document.add(Chunk.NEWLINE);

        // 부제목
        Paragraph subtitle = new Paragraph("Investment Analysis Report", headerFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // 기간 정보
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Paragraph period = new Paragraph(
                String.format("Period: %s ~ %s", startDate.format(formatter), endDate.format(formatter)),
                subHeaderFont);
        period.setAlignment(Element.ALIGN_CENTER);
        document.add(period);

        document.add(Chunk.NEWLINE);

        // 생성 일시
        Paragraph generated = new Paragraph(
                "Generated: " + LocalDate.now().format(formatter),
                normalFont);
        generated.setAlignment(Element.ALIGN_CENTER);
        document.add(generated);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // 구분선
        LineSeparator line = new LineSeparator();
        line.setLineColor(PRIMARY_COLOR);
        line.setLineWidth(2f);
        document.add(new Chunk(line));
    }

    private void addSummarySection(Document document, Long accountId, LocalDate startDate, LocalDate endDate)
            throws DocumentException {

        addSectionTitle(document, "1. Executive Summary");

        // 기간 분석 데이터 조회
        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);

        // 요약 테이블
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(20);

        addSummaryRow(table, "Total Investment", formatCurrency(analysis.getTotalBuyAmount()));
        addSummaryRow(table, "Current Value", formatCurrency(analysis.getTotalSellAmount()));
        addSummaryRow(table, "Realized P&L", formatCurrency(analysis.getRealizedProfit()),
                analysis.getRealizedProfit().compareTo(BigDecimal.ZERO) >= 0);
        addSummaryRow(table, "Unrealized P&L", formatCurrency(analysis.getUnrealizedProfit()),
                analysis.getUnrealizedProfit() != null && analysis.getUnrealizedProfit().compareTo(BigDecimal.ZERO) >= 0);
        addSummaryRow(table, "Total Transactions", String.valueOf(analysis.getTotalTransactions()));
        addSummaryRow(table, "Win Rate",
                analysis.getWinRate() != null ? formatPercent(analysis.getWinRate()) : "N/A");

        document.add(table);

        // 핵심 성과 지표
        addSubSectionTitle(document, "Key Performance Indicators");

        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingBefore(10);

        addKpiCell(kpiTable, "Total Return",
                analysis.getTotalProfitPercent() != null ? formatPercent(analysis.getTotalProfitPercent()) : "N/A");
        addKpiCell(kpiTable, "Sharpe Ratio",
                analysis.getSharpeRatio() != null ? String.format("%.2f", analysis.getSharpeRatio()) : "N/A");
        addKpiCell(kpiTable, "Max Drawdown",
                analysis.getMaxDrawdown() != null ? formatPercent(analysis.getMaxDrawdown().negate()) : "N/A");
        addKpiCell(kpiTable, "Avg Return",
                analysis.getAverageReturn() != null ? formatPercent(analysis.getAverageReturn()) : "N/A");

        document.add(kpiTable);
    }

    private void addPortfolioComposition(Document document, Long accountId)
            throws DocumentException, IOException {

        addSectionTitle(document, "2. Portfolio Composition");

        // 포트폴리오 요약 조회
        PortfolioSummaryDto summary = portfolioAnalysisService.getPortfolioSummary();

        if (summary.getHoldings() != null && !summary.getHoldings().isEmpty()) {
            // 포트폴리오 구성 테이블
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2, 1.5f, 1.5f, 1.5f, 1.5f});
            table.setSpacingBefore(10);

            // 헤더
            addTableHeader(table, "Symbol", "Quantity", "Avg Price", "Current Value", "P&L");

            // 데이터 행
            for (PortfolioDto holding : summary.getHoldings()) {
                addTableCell(table, holding.getStockSymbol(), false);
                addTableCell(table, formatNumber(holding.getQuantity()), false);
                addTableCell(table, formatCurrency(holding.getAveragePrice()), false);
                addTableCell(table, formatCurrency(holding.getCurrentValue()), false);

                BigDecimal pnl = holding.getProfitLoss() != null ? holding.getProfitLoss() : BigDecimal.ZERO;
                addTableCell(table, formatCurrency(pnl), pnl.compareTo(BigDecimal.ZERO) >= 0, true);
            }

            document.add(table);

            // 파이 차트 추가
            try {
                Map<String, BigDecimal> holdings = summary.getHoldings().stream()
                        .collect(Collectors.toMap(
                                PortfolioDto::getStockSymbol,
                                h -> h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO
                        ));

                byte[] chartBytes = chartGenerationService.generatePortfolioPieChart(holdings);
                Image chartImage = Image.getInstance(chartBytes);
                chartImage.setAlignment(Element.ALIGN_CENTER);
                chartImage.scaleToFit(400, 300);
                document.add(Chunk.NEWLINE);
                document.add(chartImage);
            } catch (Exception e) {
                log.warn("Failed to generate portfolio chart", e);
            }
        } else {
            document.add(new Paragraph("No holdings data available.", normalFont));
        }
    }

    private void addPerformanceAnalysis(Document document, Long accountId, LocalDate startDate, LocalDate endDate)
            throws DocumentException, IOException {

        addSectionTitle(document, "3. Performance Analysis");

        // 자산 곡선 조회
        try {
            EquityCurveDto equityCurve = analysisService.getEquityCurve(accountId, startDate, endDate);

            if (equityCurve != null && equityCurve.getLabels() != null && !equityCurve.getLabels().isEmpty()) {
                addSubSectionTitle(document, "Equity Curve");

                byte[] chartBytes = chartGenerationService.generateEquityCurveChart(
                        equityCurve.getLabels(),
                        equityCurve.getValues()
                );
                Image chartImage = Image.getInstance(chartBytes);
                chartImage.setAlignment(Element.ALIGN_CENTER);
                chartImage.scaleToFit(500, 300);
                document.add(chartImage);

                // 낙폭 차트 - dailyReturns를 활용
                if (equityCurve.getDailyReturns() != null && !equityCurve.getDailyReturns().isEmpty()) {
                    document.add(Chunk.NEWLINE);
                    addSubSectionTitle(document, "Daily Returns Distribution");

                    // 누적 수익률을 낙폭 계산에 사용
                    List<BigDecimal> drawdowns = calculateDrawdowns(equityCurve.getCumulativeReturns());
                    if (!drawdowns.isEmpty()) {
                        byte[] ddChartBytes = chartGenerationService.generateDrawdownChart(
                                equityCurve.getLabels(),
                                drawdowns
                        );
                        Image ddChartImage = Image.getInstance(ddChartBytes);
                        ddChartImage.setAlignment(Element.ALIGN_CENTER);
                        ddChartImage.scaleToFit(500, 200);
                        document.add(ddChartImage);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate equity curve", e);
            document.add(new Paragraph("Equity curve data not available.", normalFont));
        }

        // 월별 수익률
        document.add(Chunk.NEWLINE);
        addSubSectionTitle(document, "Monthly Performance");

        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);
        if (analysis.getMonthlyAnalysis() != null && !analysis.getMonthlyAnalysis().isEmpty()) {
            try {
                Map<String, BigDecimal> monthlyProfits = new LinkedHashMap<>();
                for (PeriodAnalysisDto.MonthlyAnalysisDto monthly : analysis.getMonthlyAnalysis()) {
                    monthlyProfits.put(monthly.getYearMonth(), monthly.getProfit());
                }

                byte[] monthlyChartBytes = chartGenerationService.generateMonthlyProfitChart(monthlyProfits);
                Image monthlyChartImage = Image.getInstance(monthlyChartBytes);
                monthlyChartImage.setAlignment(Element.ALIGN_CENTER);
                monthlyChartImage.scaleToFit(500, 250);
                document.add(monthlyChartImage);
            } catch (Exception e) {
                log.warn("Failed to generate monthly chart", e);
            }
        }
    }

    /**
     * 누적 수익률로부터 낙폭(Drawdown) 계산
     */
    private List<BigDecimal> calculateDrawdowns(List<BigDecimal> cumulativeReturns) {
        List<BigDecimal> drawdowns = new ArrayList<>();
        if (cumulativeReturns == null || cumulativeReturns.isEmpty()) {
            return drawdowns;
        }

        BigDecimal peak = cumulativeReturns.get(0);
        for (BigDecimal current : cumulativeReturns) {
            if (current.compareTo(peak) > 0) {
                peak = current;
            }
            BigDecimal drawdown = current.subtract(peak);
            drawdowns.add(drawdown);
        }
        return drawdowns;
    }

    private void addRiskAnalysis(Document document, Long accountId, LocalDate startDate, LocalDate endDate)
            throws DocumentException {

        addSectionTitle(document, "4. Risk Analysis");

        try {
            RiskMetricsDto riskMetrics = riskMetricsService.calculateRiskMetrics(startDate, endDate);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(80);
            table.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.setSpacingBefore(10);

            addRiskMetricRow(table, "Sharpe Ratio", formatRatio(riskMetrics.getSharpeRatio()));
            addRiskMetricRow(table, "Sortino Ratio", formatRatio(riskMetrics.getSortinoRatio()));
            addRiskMetricRow(table, "Calmar Ratio", formatRatio(riskMetrics.getCalmarRatio()));
            addRiskMetricRow(table, "Volatility", formatPercent(riskMetrics.getVolatility()));
            addRiskMetricRow(table, "Max Drawdown", formatPercent(riskMetrics.getMaxDrawdown()));

            if (riskMetrics.getVar95() != null) {
                addRiskMetricRow(table, "VaR (95%)", formatPercent(riskMetrics.getVar95().getDailyVaR()));
            }
            if (riskMetrics.getVar99() != null) {
                addRiskMetricRow(table, "VaR (99%)", formatPercent(riskMetrics.getVar99().getDailyVaR()));
            }

            document.add(table);

            // 리스크 해석
            document.add(Chunk.NEWLINE);
            addSubSectionTitle(document, "Risk Interpretation");

            Paragraph interpretation = new Paragraph();
            interpretation.setSpacingBefore(10);

            if (riskMetrics.getSharpeRatio() != null) {
                String sharpeDesc = riskMetrics.getSharpeRatio().compareTo(BigDecimal.ONE) >= 0
                        ? "Good risk-adjusted return"
                        : "Risk-adjusted return needs improvement";
                interpretation.add(new Chunk("• Sharpe Ratio: ", boldFont));
                interpretation.add(new Chunk(sharpeDesc + "\n", normalFont));
            }

            if (riskMetrics.getMaxDrawdown() != null) {
                String ddDesc = riskMetrics.getMaxDrawdown().abs().compareTo(BigDecimal.valueOf(20)) <= 0
                        ? "Drawdown within acceptable range"
                        : "High drawdown - consider risk management";
                interpretation.add(new Chunk("• Max Drawdown: ", boldFont));
                interpretation.add(new Chunk(ddDesc + "\n", normalFont));
            }

            document.add(interpretation);

        } catch (Exception e) {
            log.warn("Failed to calculate risk metrics", e);
            document.add(new Paragraph("Risk metrics not available.", normalFont));
        }
    }

    private void addTradeSummary(Document document, Long accountId, LocalDate startDate, LocalDate endDate)
            throws DocumentException {

        addSectionTitle(document, "5. Trading Summary");

        PeriodAnalysisDto analysis = analysisService.analyzePeriod(startDate, endDate);

        // 거래 통계
        PdfPTable statsTable = new PdfPTable(2);
        statsTable.setWidthPercentage(60);
        statsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        statsTable.setSpacingBefore(10);

        addStatRow(statsTable, "Total Trades", String.valueOf(analysis.getTotalTransactions()));
        addStatRow(statsTable, "Buy Transactions", String.valueOf(analysis.getBuyTransactions()));
        addStatRow(statsTable, "Sell Transactions", String.valueOf(analysis.getSellTransactions()));
        addStatRow(statsTable, "Win Rate", analysis.getWinRate() != null ? formatPercent(analysis.getWinRate()) : "N/A");
        addStatRow(statsTable, "Total Buy Amount", formatCurrency(analysis.getTotalBuyAmount()));
        addStatRow(statsTable, "Total Sell Amount", formatCurrency(analysis.getTotalSellAmount()));

        document.add(statsTable);

        // 수익/손실 분포 차트
        try {
            int winCount = analysis.getWinCount() != null ? analysis.getWinCount() : 0;
            int lossCount = analysis.getLossCount() != null ? analysis.getLossCount() : 0;

            if (winCount > 0 || lossCount > 0) {
                document.add(Chunk.NEWLINE);
                byte[] distChartBytes = chartGenerationService.generateProfitDistributionChart(winCount, lossCount);
                Image distChartImage = Image.getInstance(distChartBytes);
                distChartImage.setAlignment(Element.ALIGN_CENTER);
                distChartImage.scaleToFit(350, 250);
                document.add(distChartImage);
            }
        } catch (Exception e) {
            log.warn("Failed to generate distribution chart", e);
        }
    }

    // === Helper Methods ===

    private void addSectionTitle(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, headerFont);
        p.setSpacingBefore(20);
        p.setSpacingAfter(10);
        document.add(p);

        LineSeparator line = new LineSeparator();
        line.setLineColor(PRIMARY_COLOR);
        line.setLineWidth(1f);
        document.add(new Chunk(line));
    }

    private void addSubSectionTitle(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, subHeaderFont);
        p.setSpacingBefore(15);
        p.setSpacingAfter(8);
        document.add(p);
    }

    private void addSummaryRow(PdfPTable table, String label, String value) {
        addSummaryRow(table, label, value, true);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, boolean positive) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setPadding(8);
        table.addCell(labelCell);

        Font valueFont = new Font(boldFont);
        if (value.startsWith("-") || value.startsWith("(")) {
            valueFont.setColor(DANGER_COLOR);
        } else if (value.startsWith("+") || (positive && !value.equals("N/A"))) {
            valueFont.setColor(SUCCESS_COLOR);
        }

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addKpiCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBackgroundColor(HEADER_BG);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", smallFont));
        p.add(new Chunk(value, boldFont));
        p.setAlignment(Element.ALIGN_CENTER);

        cell.addElement(p);
        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, boldFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setBorderColor(BORDER_COLOR);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addTableCell(PdfPTable table, String value, boolean highlight) {
        addTableCell(table, value, highlight, false);
    }

    private void addTableCell(PdfPTable table, String value, boolean positive, boolean colorize) {
        Font font = normalFont;
        if (colorize) {
            font = new Font(normalFont);
            font.setColor(positive ? SUCCESS_COLOR : DANGER_COLOR);
        }

        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addRiskMetricRow(PdfPTable table, String metric, String value) {
        PdfPCell metricCell = new PdfPCell(new Phrase(metric, normalFont));
        metricCell.setBorder(Rectangle.BOTTOM);
        metricCell.setBorderColor(BORDER_COLOR);
        metricCell.setPadding(8);
        table.addCell(metricCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, boldFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addStatRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, normalFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, boldFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "N/A";
        return String.format("₩%,.0f", value);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "N/A";
        String sign = value.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, value);
    }

    private String formatRatio(BigDecimal value) {
        if (value == null) return "N/A";
        return String.format("%.2f", value);
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) return "N/A";
        return String.format("%,.2f", value);
    }

    /**
     * 헤더/푸터 이벤트 핸들러
     */
    private static class ReportHeaderFooter extends PdfPageEventHelper {
        private Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            // 푸터
            Phrase footer = new Phrase(
                    String.format("Page %d | Trading Journal Report | Generated on %s",
                            writer.getPageNumber(),
                            LocalDate.now().toString()),
                    footerFont
            );

            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 20, 0);
        }
    }
}
