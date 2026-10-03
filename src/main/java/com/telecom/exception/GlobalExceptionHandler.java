package com.telecom.exception;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** A 5,000-record batch must not produce a 5,000-entry error body. */
    static final int MAX_FIELD_ERRORS = 20;

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail payloadTooLarge(PayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", ex.getMessage());
    }

    @ExceptionHandler(TooManyRecordsException.class)
    public ProblemDetail tooManyRecords(TooManyRecordsException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Too Many Records", ex.getMessage());
    }

    /** Buffered @RequestBody failures: the cause says whether it was size, gzip, parser limits or bad JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof PayloadTooLargeException p) {
                return payloadTooLarge(p);
            }
            if (cause instanceof ZipException) {
                return badGzip();
            }
            if (cause instanceof StreamConstraintsException s) {
                return problem(HttpStatus.BAD_REQUEST, "Payload Constraint Violated", s.getOriginalMessage());
            }
            cause = cause.getCause();
        }
        return problem(HttpStatus.BAD_REQUEST, "Malformed Body", "Request body is not valid JSON for this endpoint");
    }

    @ExceptionHandler(ZipException.class)
    public ProblemDetail zip(ZipException ex) {
        return badGzip();
    }

    @ExceptionHandler(MalformedPayloadException.class)
    public ProblemDetail malformed(MalformedPayloadException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Malformed Payload", ex.getMessage());
        pd.setProperty("partialResult", ex.getPartialResult());
        return pd;
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail validation(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().stream().limit(MAX_FIELD_ERRORS)
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid Request", "Validation failed");
        pd.setProperty("errors", errors);
        pd.setProperty("totalErrors", ex.getErrorCount());
        return pd;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail invalid(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Request", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ProblemDetail> busy(ServiceBusyException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Busy", ex.getMessage()));
    }

    private static ProblemDetail badGzip() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Compressed Body", "Body is not valid gzip");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
