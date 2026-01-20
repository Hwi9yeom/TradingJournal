package com.trading.journal.exception;

/**
 * 데이터 내보내기(Excel, PDF, CSV) 중 발생하는 예외
 */
public class ExportException extends RuntimeException {

    private final String exportType;
    private final String fileName;

    public ExportException(String message) {
        super(message);
        this.exportType = null;
        this.fileName = null;
    }

    public ExportException(String message, Throwable cause) {
        super(message, cause);
        this.exportType = null;
        this.fileName = null;
    }

    public ExportException(String exportType, String message) {
        super(String.format("%s 내보내기 실패: %s", exportType, message));
        this.exportType = exportType;
        this.fileName = null;
    }

    public ExportException(String exportType, String fileName, String message) {
        super(String.format("%s 내보내기 실패 [파일: %s]: %s", exportType, fileName, message));
        this.exportType = exportType;
        this.fileName = fileName;
    }

    public ExportException(String exportType, String fileName, String message, Throwable cause) {
        super(String.format("%s 내보내기 실패 [파일: %s]: %s", exportType, fileName, message), cause);
        this.exportType = exportType;
        this.fileName = fileName;
    }

    public String getExportType() {
        return exportType;
    }

    public String getFileName() {
        return fileName;
    }
}
