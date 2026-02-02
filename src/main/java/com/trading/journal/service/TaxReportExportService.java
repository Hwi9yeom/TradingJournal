package com.trading.journal.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.trading.journal.dto.TaxCalculationDto;
import com.trading.journal.repository.TransactionRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tax Report PDF Export Service
 *
 * <p>Generates comprehensive tax report PDFs containing year-end tax calculations, transaction
 * details, and tax summaries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaxReportExportService {

    private final TaxCalculationService taxCalculationService;
    private final TransactionRepository transactionRepository;

    // Color scheme
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
     * Generate tax report PDF for a specific year
     *
     * @param accountId Account ID (optional, null for all accounts)
     * @param year Tax year
     * @return PDF file as byte array
     */
    public byte[] generateTaxReport(Long accountId, int year) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            // Page event handler for header/footer
            writer.setPageEvent(new TaxReportHeaderFooter(year));

            document.open();
            initializeFonts();

            // 1. Cover page
            addCoverPage(document, year, accountId);
            document.newPage();

            // 2. Tax calculation from service
            TaxCalculationDto taxCalc = taxCalculationService.calculateTax(year);

            // 3. Tax summary section
            addTaxSummarySection(document, taxCalc);
            document.newPage();

            // 4. Transaction details
            addTransactionDetailsSection(document, taxCalc, accountId, year);

            // 5. Footer disclaimer
            document.add(Chunk.NEWLINE);
            addDisclaimer(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate tax report PDF for year {}", year, e);
            throw new RuntimeException("Failed to generate tax report: " + e.getMessage(), e);
        }
    }

    private void initializeFonts() {
        try {
            // Use system font with Korean support
            BaseFont baseFont =
                    BaseFont.createFont("HeiseiKakuGo-W5", "UniJIS-UCS2-H", BaseFont.NOT_EMBEDDED);
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

    private void addCoverPage(Document document, int year, Long accountId)
            throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Title
        Paragraph title = new Paragraph("Tax Report " + year, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        document.add(Chunk.NEWLINE);

        // Subtitle
        Paragraph subtitle = new Paragraph("Annual Tax Calculation Summary", headerFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Account info
        if (accountId != null) {
            Paragraph accountInfo = new Paragraph("Account ID: " + accountId, subHeaderFont);
            accountInfo.setAlignment(Element.ALIGN_CENTER);
            document.add(accountInfo);
        } else {
            Paragraph allAccounts = new Paragraph("All Accounts", subHeaderFont);
            allAccounts.setAlignment(Element.ALIGN_CENTER);
            document.add(allAccounts);
        }

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Generated date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Paragraph generated =
                new Paragraph("Generated on: " + LocalDate.now().format(formatter), normalFont);
        generated.setAlignment(Element.ALIGN_CENTER);
        document.add(generated);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        // Separator line
        LineSeparator line = new LineSeparator();
        line.setLineColor(PRIMARY_COLOR);
        line.setLineWidth(2f);
        document.add(new Chunk(line));
    }

    private void addTaxSummarySection(Document document, TaxCalculationDto taxCalc)
            throws DocumentException {

        addSectionTitle(document, "Tax Summary");

        // Summary table
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingBefore(10);
        summaryTable.setSpacingAfter(20);

        addSummaryRow(summaryTable, "Tax Year", String.valueOf(taxCalc.getTaxYear()));
        addSummaryRow(summaryTable, "Total Gains", formatCurrency(taxCalc.getTotalProfit()));
        addSummaryRow(summaryTable, "Total Losses", formatCurrency(taxCalc.getTotalLoss()));
        addSummaryRow(
                summaryTable,
                "Net Gain/Loss",
                formatCurrency(taxCalc.getNetProfit()),
                taxCalc.getNetProfit().compareTo(BigDecimal.ZERO) >= 0);
        addSummaryRow(summaryTable, "Taxable Amount", formatCurrency(taxCalc.getTaxableAmount()));
        addSummaryRow(
                summaryTable, "Estimated Tax", formatCurrency(taxCalc.getEstimatedTax()), false);
        addSummaryRow(summaryTable, "Tax Rate", formatPercent(taxCalc.getTaxRate()));

        document.add(summaryTable);

        // Tax breakdown
        document.add(Chunk.NEWLINE);
        addSubSectionTitle(document, "Tax Calculation Breakdown");

        Paragraph breakdown = new Paragraph();
        breakdown.setSpacingBefore(10);
        breakdown.add(new Chunk("Total Buy Amount: ", boldFont));
        breakdown.add(new Chunk(formatCurrency(taxCalc.getTotalBuyAmount()) + "\n", normalFont));
        breakdown.add(new Chunk("Total Sell Amount: ", boldFont));
        breakdown.add(new Chunk(formatCurrency(taxCalc.getTotalSellAmount()) + "\n", normalFont));
        breakdown.add(new Chunk("Basic Deduction: ", boldFont));
        breakdown.add(new Chunk("₩2,500,000\n", normalFont));

        document.add(breakdown);
    }

    private void addTransactionDetailsSection(
            Document document, TaxCalculationDto taxCalc, Long accountId, int year)
            throws DocumentException {

        addSectionTitle(document, "Transaction Details");

        if (taxCalc.getTaxDetails() == null || taxCalc.getTaxDetails().isEmpty()) {
            document.add(new Paragraph("No transactions for this tax year.", normalFont));
            return;
        }

        // Transaction details table
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1.2f, 1.5f, 0.8f, 1f, 1f, 1f, 1f, 1f});
        table.setSpacingBefore(10);

        // Headers
        addTableHeader(
                table,
                "Date",
                "Symbol",
                "Type",
                "Quantity",
                "Buy Price",
                "Sell Price",
                "Gain/Loss",
                "Term");

        // Data rows
        for (TaxCalculationDto.TaxDetailDto detail : taxCalc.getTaxDetails()) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            addTableCell(table, detail.getSellDate().format(dateFormatter), false);
            addTableCell(table, detail.getStockSymbol(), false);
            addTableCell(table, "SELL", false);
            addTableCell(
                    table,
                    formatDecimal(
                            detail.getSellAmount()
                                    .divide(detail.getSellAmount(), 2, BigDecimal.ROUND_HALF_UP)),
                    false);
            addTableCell(table, formatCurrency(detail.getBuyAmount()), false);
            addTableCell(table, formatCurrency(detail.getSellAmount()), false);

            // Profit/Loss with color
            BigDecimal gainLoss = detail.getProfit().subtract(detail.getLoss());
            boolean isProfit = gainLoss.compareTo(BigDecimal.ZERO) >= 0;
            addTableCell(table, formatCurrency(gainLoss), isProfit, true);

            // Term
            addTableCell(table, detail.getIsLongTerm() ? "Long" : "Short", false);
        }

        document.add(table);
    }

    private void addDisclaimer(Document document) throws DocumentException {
        Paragraph disclaimer = new Paragraph();
        disclaimer.setSpacingBefore(20);
        disclaimer.add(new Chunk("DISCLAIMER:\n", boldFont));
        disclaimer.add(
                new Chunk(
                        "This tax report is for informational purposes only and should not be considered as professional tax advice. "
                                + "Please consult with a qualified tax professional for accurate tax calculations and filing. "
                                + "Tax laws and rates may vary, and this report uses simplified calculations based on Korean tax regulations as of 2024.\n",
                        smallFont));

        PdfPCell disclaimerCell = new PdfPCell();
        disclaimerCell.setBorderColor(BORDER_COLOR);
        disclaimerCell.setBackgroundColor(HEADER_BG);
        disclaimerCell.setPadding(10);
        disclaimerCell.addElement(disclaimer);

        PdfPTable disclaimerTable = new PdfPTable(1);
        disclaimerTable.setWidthPercentage(100);
        disclaimerTable.addCell(disclaimerCell);

        document.add(disclaimerTable);
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
        if (value.startsWith("-")) {
            valueFont.setColor(DANGER_COLOR);
        } else if (positive && !value.equals("N/A") && !value.equals("0%")) {
            // Only colorize if it's a positive value context
            if (label.contains("Tax") || label.contains("Rate")) {
                valueFont.setColor(Color.BLACK); // Keep tax values neutral
            } else if (value.startsWith("₩") && !value.equals("₩0")) {
                valueFont.setColor(SUCCESS_COLOR);
            }
        }

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
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

    private String formatCurrency(BigDecimal value) {
        if (value == null) return "₩0";
        return String.format("₩%,.0f", value);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "0%";
        return String.format("%.2f%%", value);
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%.2f", value);
    }

    /** Header/Footer event handler */
    private static class TaxReportHeaderFooter extends PdfPageEventHelper {
        private final int year;
        private Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

        public TaxReportHeaderFooter(int year) {
            this.year = year;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            // Footer
            Phrase footer =
                    new Phrase(
                            String.format(
                                    "Page %d | Tax Report %d | Generated on %s",
                                    writer.getPageNumber(), year, LocalDate.now().toString()),
                            footerFont);

            ColumnText.showTextAligned(
                    cb,
                    Element.ALIGN_CENTER,
                    footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 20,
                    0);
        }
    }
}
