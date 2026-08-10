package com.fintwin.fintwin.global.error;

public class CsvValidationException extends RuntimeException {
    private final String code;
    private final Integer rowNumber;
    private final String columnName;

    public CsvValidationException(String code, Integer rowNumber, String columnName, String message) {
        super(message);
        this.code = code;
        this.rowNumber = rowNumber;
        this.columnName = columnName;
    }

    public String getCode() {
        return code;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public String getColumnName() {
        return columnName;
    }
}
