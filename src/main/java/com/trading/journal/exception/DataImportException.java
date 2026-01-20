package com.trading.journal.exception;

/** 데이터 임포트 중 발생하는 예외 */
public class DataImportException extends RuntimeException {

    private final String fileType;
    private final Integer rowNumber;
    private final String fieldName;

    public DataImportException(String message) {
        super(message);
        this.fileType = null;
        this.rowNumber = null;
        this.fieldName = null;
    }

    public DataImportException(String message, Throwable cause) {
        super(message, cause);
        this.fileType = null;
        this.rowNumber = null;
        this.fieldName = null;
    }

    public DataImportException(String fileType, Integer rowNumber, String message) {
        super(String.format("%s 파일 임포트 실패 (행 %d): %s", fileType, rowNumber, message));
        this.fileType = fileType;
        this.rowNumber = rowNumber;
        this.fieldName = null;
    }

    public DataImportException(
            String fileType, Integer rowNumber, String fieldName, String message) {
        super(
                String.format(
                        "%s 파일 임포트 실패 (행 %d, 필드 '%s'): %s",
                        fileType, rowNumber, fieldName, message));
        this.fileType = fileType;
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
    }

    public String getFileType() {
        return fileType;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public String getFieldName() {
        return fieldName;
    }
}
