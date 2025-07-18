package com.trading.journal.service;

import com.opencsv.CSVWriter;
import com.trading.journal.dto.TransactionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportService {
    
    private final TransactionService transactionService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public byte[] exportTransactionsToCsv() {
        List<TransactionDto> transactions = transactionService.getAllTransactions();
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVWriter writer = new CSVWriter(osw)) {
            
            // Write BOM for Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            
            // Write headers
            String[] headers = {
                "거래일시", "종목코드", "종목명", "거래구분", 
                "수량", "단가", "금액", "수수료", "비고"
            };
            writer.writeNext(headers);
            
            // Write data
            for (TransactionDto transaction : transactions) {
                String[] data = {
                    transaction.getTransactionDate().format(DATE_FORMATTER),
                    transaction.getStockSymbol(),
                    transaction.getStockName(),
                    transaction.getType().toString(),
                    transaction.getQuantity().toString(),
                    transaction.getPrice().toString(),
                    transaction.getTotalAmount().toString(),
                    transaction.getCommission() != null ? transaction.getCommission().toString() : "0",
                    transaction.getNotes() != null ? transaction.getNotes() : ""
                };
                writer.writeNext(data);
            }
            
            writer.flush();
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to export transactions to CSV", e);
            throw new RuntimeException("CSV 내보내기 실패: " + e.getMessage());
        }
    }
    
    public byte[] exportTransactionsToExcel() {
        List<TransactionDto> transactions = transactionService.getAllTransactions();
        
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("거래내역");
            
            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Create date style
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper createHelper = workbook.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            
            // Create number style
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));
            
            // Create headers
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "거래일시", "종목코드", "종목명", "거래구분", 
                "수량", "단가", "금액", "수수료", "비고"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Write data
            int rowNum = 1;
            for (TransactionDto transaction : transactions) {
                Row row = sheet.createRow(rowNum++);
                
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(transaction.getTransactionDate());
                dateCell.setCellStyle(dateStyle);
                
                row.createCell(1).setCellValue(transaction.getStockSymbol());
                row.createCell(2).setCellValue(transaction.getStockName());
                row.createCell(3).setCellValue(transaction.getType().toString());
                
                Cell quantityCell = row.createCell(4);
                quantityCell.setCellValue(transaction.getQuantity().doubleValue());
                quantityCell.setCellStyle(numberStyle);
                
                Cell priceCell = row.createCell(5);
                priceCell.setCellValue(transaction.getPrice().doubleValue());
                priceCell.setCellStyle(numberStyle);
                
                Cell amountCell = row.createCell(6);
                amountCell.setCellValue(transaction.getTotalAmount().doubleValue());
                amountCell.setCellStyle(numberStyle);
                
                Cell commissionCell = row.createCell(7);
                commissionCell.setCellValue(transaction.getCommission() != null 
                    ? transaction.getCommission().doubleValue() : 0);
                commissionCell.setCellStyle(numberStyle);
                
                row.createCell(8).setCellValue(transaction.getNotes() != null ? transaction.getNotes() : "");
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to export transactions to Excel", e);
            throw new RuntimeException("Excel 내보내기 실패: " + e.getMessage());
        }
    }
    
    public byte[] exportTransactionsByDateRangeToCsv(String startDate, String endDate) {
        // Similar implementation with date range filter
        // TODO: Implement date range filtering
        return exportTransactionsToCsv();
    }
    
    public byte[] exportTransactionsByDateRangeToExcel(String startDate, String endDate) {
        // Similar implementation with date range filter
        // TODO: Implement date range filtering
        return exportTransactionsToExcel();
    }
}