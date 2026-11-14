package io.github.samarthshubham.tutor.common.web;

import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.ErrorResponse;
import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.RateLimitedException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  static final String VALIDATION_MESSAGE = "Request is not valid";
  static final String INTERNAL_MESSAGE = "Unexpected error";

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  ResponseEntity<ErrorResponse> handleUnknownPath(Exception ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
    return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
  }

  @ExceptionHandler(AiUnavailableException.class)
  ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException ex) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", ex.getMessage());
  }

  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfter().toSeconds()))
        .body(new ErrorResponse(429, "RATE_LIMITED", ex.getMessage()));
  }

  @ExceptionHandler(ValidationException.class)
  ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
    return validationFailed(ex.getMessage(), ex.fields());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
    return validationFailed(VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ErrorResponse> handleInvalidParameters(HandlerMethodValidationException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getParameterValidationResults()
        .forEach(result -> {
          if (result instanceof ParameterErrors errors) {
            errors
                .getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
          } else {
            result
                .getResolvableErrors()
                .forEach(error ->
                    fields.putIfAbsent(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage()));
          }
        });
    return validationFailed(VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(violation ->
            fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
    return validationFailed(VALIDATION_MESSAGE, fields);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return validationFailed(VALIDATION_MESSAGE, Map.of(ex.getName(), "has an invalid value"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return validationFailed("Request body is malformed", null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    if (ex instanceof org.springframework.web.ErrorResponse frameworkError
        && frameworkError.getStatusCode().is4xxClientError()) {
      return frameworkClientError(frameworkError.getStatusCode());
    }
    log.error("Unexpected error", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", INTERNAL_MESSAGE);
  }

  private ResponseEntity<ErrorResponse> frameworkClientError(HttpStatusCode statusCode) {
    HttpStatus status = HttpStatus.valueOf(statusCode.value());
    if (status == HttpStatus.BAD_REQUEST) {
      return validationFailed(VALIDATION_MESSAGE, null);
    }
    return error(status, status.name(), status.getReasonPhrase());
  }

  private ResponseEntity<ErrorResponse> validationFailed(
      String message, Map<String, String> fields) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse(400, "VALIDATION_FAILED", message, fields));
  }

  private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ErrorResponse(status.value(), code, message));
  }
}
