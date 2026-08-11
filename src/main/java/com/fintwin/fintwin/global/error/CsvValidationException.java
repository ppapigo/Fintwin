package com.fintwin.fintwin.global.error;

public class CsvValidationException extends TransactionFileValidationException {

    public CsvValidationException(String code, Integer rowNumber, String columnName, String message) {
        super(code, rowNumber, columnName, message);
    }

    @Override
    public String safeSummary() {
        return "CSV validation failed";
    }
}
