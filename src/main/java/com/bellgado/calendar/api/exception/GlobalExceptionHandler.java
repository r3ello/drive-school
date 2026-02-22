package com.bellgado.calendar.api.exception;

import com.bellgado.calendar.api.dto.FieldError;
import com.bellgado.calendar.api.dto.ProblemDetails;
import com.bellgado.calendar.application.exception.ConflictException;
import com.bellgado.calendar.application.exception.InvalidStateException;
import com.bellgado.calendar.application.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_BASE = "https://api.bellgado.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetails> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "not-found",
                "Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetails> handleConflict(ConflictException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "conflict",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ProblemDetails> handleInvalidState(InvalidStateException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "invalid-state",
                "Unprocessable Entity",
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetails> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "conflict",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                "The resource was modified by another request. Please retry.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetails> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = "Data integrity violation";
        if (ex.getMessage() != null && ex.getMessage().contains("slots_start_at_key")) {
            message = "A slot already exists at this time";
        }
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "conflict",
                "Conflict",
                HttpStatus.CONFLICT.value(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();

        ProblemDetails problem = ProblemDetails.withErrors(
                PROBLEM_BASE + "validation-error",
                "Validation error",
                HttpStatus.BAD_REQUEST.value(),
                "Request validation failed",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetails> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.withErrors(
                PROBLEM_BASE + "validation-error",
                "Validation error",
                HttpStatus.BAD_REQUEST.value(),
                "Missing required parameter",
                request.getRequestURI(),
                List.of(new FieldError(ex.getParameterName(), "is required"))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetails> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.withErrors(
                PROBLEM_BASE + "validation-error",
                "Validation error",
                HttpStatus.BAD_REQUEST.value(),
                "Invalid parameter type",
                request.getRequestURI(),
                List.of(new FieldError(ex.getName(), "must be of type " + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown")))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "validation-error",
                "Validation error",
                HttpStatus.BAD_REQUEST.value(),
                "Malformed request body",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetails> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "validation-error",
                "Validation error",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ProblemDetails problem = ProblemDetails.of(
                PROBLEM_BASE + "internal-error",
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON))
                .body(problem);
    }
}
