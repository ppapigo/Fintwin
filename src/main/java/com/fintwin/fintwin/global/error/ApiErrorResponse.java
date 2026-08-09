package com.fintwin.fintwin.global.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(Instant timestamp, int status, String code, String message, String path,
                               List<FieldErrorDetail> fieldErrors) {
    public record FieldErrorDetail(String field, String message) {
    }
}
