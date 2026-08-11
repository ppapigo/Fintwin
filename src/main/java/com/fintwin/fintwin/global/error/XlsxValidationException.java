package com.fintwin.fintwin.global.error;

public class XlsxValidationException extends TransactionFileValidationException {
    public XlsxValidationException(String code, Integer rowNumber, String columnName, String message) {
        super(code, rowNumber, columnName, message);
    }

    @Override
    public String safeSummary() {
        return "XLSX validation failed";
    }
}
