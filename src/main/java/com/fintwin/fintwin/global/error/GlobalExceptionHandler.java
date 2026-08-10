package com.fintwin.fintwin.global.error;

import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception,
                                                           HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required",
                request, List.of());
    }

    @ExceptionHandler(AiAdapterException.class)
    ResponseEntity<ApiErrorResponse> handleAiAdapter(AiAdapterException exception, HttpServletRequest request) {
        return response(exception.httpStatus(), exception.code().name(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidRequestException exception,
                                                          HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(CsvValidationException.class)
    ResponseEntity<ApiErrorResponse> handleCsvValidation(CsvValidationException exception,
                                                         HttpServletRequest request) {
        ApiErrorResponse.FieldErrorDetail detail = new ApiErrorResponse.FieldErrorDetail(
                exception.getColumnName(), exception.getMessage(), exception.getRowNumber());
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), "CSV validation failed", request,
                List.of(detail));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleUploadSize(MaxUploadSizeExceededException exception,
                                                      HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "CSV_FILE_TOO_LARGE", "CSV file exceeds the 2MB limit",
                request, List.of(new ApiErrorResponse.FieldErrorDetail("file", "File exceeds the allowed size")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, fieldErrors);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
                                                       HttpServletRequest request,
                                                       List<ApiErrorResponse.FieldErrorDetail> fieldErrors) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), code, message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
