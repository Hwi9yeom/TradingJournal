package com.trading.journal.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.trading.journal.dto.ImportResultDto;
import com.trading.journal.dto.ImportTransactionDto;
import com.trading.journal.dto.TransactionDto;
import com.trading.journal.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataImportService {
    
    private final TransactionService transactionService;
    
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );
    
    public ImportResultDto importFromCsv(MultipartFile file) {
        ImportResultDto result = ImportResultDto.builder()
                .totalRows(0)
                .successCount(0)
                .failureCount(0)
                .errors(new ArrayList<>())
                .build();
        
        try {
            RFC4180Parser parser = new RFC4180ParserBuilder().build();
            CSVReader reader = new CSVReaderBuilder(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .withCSVParser(parser)
                    .build();
            
            String[] headers = reader.readNext(); // Skip header row
            String[] line;
            int rowNumber = 1;
            
            while ((line = reader.readNext()) != null) {
                rowNumber++;
                result.setTotalRows(result.getTotalRows() + 1);
                
                try {
                    ImportTransactionDto importDto = parseCSVLine(line);
                    TransactionDto transactionDto = convertToTransactionDto(importDto);
                    transactionService.createTransaction(transactionDto);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailureCount(result.getFailureCount() + 1);
                    result.getErrors().add(ImportResultDto.ImportErrorDto.builder()
                            .rowNumber(rowNumber)
                            .message(e.getMessage())
                            .data(parseCSVLine(line))
                            .build());
                    log.error("Failed to import row {}: {}", rowNumber, e.getMessage());
                }
            }
            
            reader.close();
            
        } catch (Exception e) {
            log.error("Failed to read CSV file", e);
            throw new RuntimeException("CSV 파일 읽기 실패: " + e.getMessage());
        }
        
        return result;
    }
    
    public ImportResultDto importFromExcel(MultipartFile file) {
        ImportResultDto result = ImportResultDto.builder()
                .totalRows(0)
                .successCount(0)
                .failureCount(0)
                .errors(new ArrayList<>())
                .build();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                result.setTotalRows(result.getTotalRows() + 1);
                
                try {
                    ImportTransactionDto importDto = parseExcelRow(row);
                    TransactionDto transactionDto = convertToTransactionDto(importDto);
                    transactionService.createTransaction(transactionDto);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (Exception e) {
                    result.setFailureCount(result.getFailureCount() + 1);
                    result.getErrors().add(ImportResultDto.ImportErrorDto.builder()
                            .rowNumber(i + 1)
                            .message(e.getMessage())
                            .data(parseExcelRow(row))
                            .build());
                    log.error("Failed to import row {}: {}", i + 1, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to read Excel file", e);
            throw new RuntimeException("Excel 파일 읽기 실패: " + e.getMessage());
        }
        
        return result;
    }
    
    private ImportTransactionDto parseCSVLine(String[] line) {
        return ImportTransactionDto.builder()
                .date(getValueOrEmpty(line, 0))
                .stockCode(getValueOrEmpty(line, 1))
                .stockName(getValueOrEmpty(line, 2))
                .transactionType(getValueOrEmpty(line, 3))
                .quantity(getValueOrEmpty(line, 4))
                .price(getValueOrEmpty(line, 5))
                .amount(getValueOrEmpty(line, 6))
                .commission(getValueOrEmpty(line, 7))
                .tax(getValueOrEmpty(line, 8))
                .notes(getValueOrEmpty(line, 9))
                .build();
    }
    
    private ImportTransactionDto parseExcelRow(Row row) {
        return ImportTransactionDto.builder()
                .date(getCellValueAsString(row.getCell(0)))
                .stockCode(getCellValueAsString(row.getCell(1)))
                .stockName(getCellValueAsString(row.getCell(2)))
                .transactionType(getCellValueAsString(row.getCell(3)))
                .quantity(getCellValueAsString(row.getCell(4)))
                .price(getCellValueAsString(row.getCell(5)))
                .amount(getCellValueAsString(row.getCell(6)))
                .commission(getCellValueAsString(row.getCell(7)))
                .tax(getCellValueAsString(row.getCell(8)))
                .notes(getCellValueAsString(row.getCell(9)))
                .build();
    }
    
    private TransactionDto convertToTransactionDto(ImportTransactionDto importDto) {
        return TransactionDto.builder()
                .stockSymbol(importDto.getStockCode())
                .stockName(importDto.getStockName())
                .type(parseTransactionType(importDto.getTransactionType()))
                .quantity(parseBigDecimal(importDto.getQuantity()))
                .price(parseBigDecimal(importDto.getPrice()))
                .commission(parseBigDecimal(importDto.getCommission()))
                .transactionDate(parseDate(importDto.getDate()))
                .notes(importDto.getNotes())
                .build();
    }
    
    private String getValueOrEmpty(String[] array, int index) {
        return (array != null && index < array.length && array[index] != null) 
                ? array[index].trim() : "";
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    private TransactionType parseTransactionType(String type) {
        String upperType = type.toUpperCase();
        if (upperType.contains("매수") || upperType.contains("BUY")) {
            return TransactionType.BUY;
        } else if (upperType.contains("매도") || upperType.contains("SELL")) {
            return TransactionType.SELL;
        }
        throw new IllegalArgumentException("올바르지 않은 거래 유형: " + type);
    }
    
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            // Remove commas and parse
            return new BigDecimal(value.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("올바르지 않은 숫자 형식: " + value);
        }
    }
    
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new IllegalArgumentException("날짜가 비어있습니다");
        }
        
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr.trim(), formatter);
                return date.atStartOfDay();
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        
        throw new IllegalArgumentException("날짜 형식을 파싱할 수 없습니다: " + dateStr);
    }
}