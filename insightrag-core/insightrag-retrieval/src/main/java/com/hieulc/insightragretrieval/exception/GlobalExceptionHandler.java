package com.hieulc.insightragretrieval.exception;

import com.hieulc.insightragretrieval.exception.appli.InsufficientContextException;
import com.hieulc.insightragretrieval.exception.infras.AiContentGenerateException;
import com.hieulc.insightragretrieval.exception.infras.EmbeddingClientException;
import com.hieulc.insightragretrieval.exception.infras.TokenExceededLimitException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(InsufficientContextException.class)
  public ProblemDetail handleInsufficientContextException(InsufficientContextException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INSUFFICIENT_STORAGE, ex.getMessage());
    problemDetail.setTitle("Insufficient Document Context");
    return problemDetail;
  }

  @ExceptionHandler(TokenExceededLimitException.class)
  public ProblemDetail handleTokenExceededLimitException(TokenExceededLimitException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    problemDetail.setTitle("Token Exceeded Limit");
    return problemDetail;
  }

  @ExceptionHandler(AiContentGenerateException.class)
  public ProblemDetail handleAiContentGenerateException(AiContentGenerateException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    problemDetail.setTitle("Ai Content Generation Failed");
    return problemDetail;
  }

  @ExceptionHandler(EmbeddingClientException.class)
  public ProblemDetail handleEmbeddingClientException(EmbeddingClientException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    problemDetail.setTitle("Embedding Client Generation Failed");
    return problemDetail;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request payload failed validation");
    problemDetail.setTitle("Invalid Request Payload");
    Map<String, Object> errors = new HashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      errors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    problemDetail.setProperties(errors);
    return problemDetail;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUncatchException(Exception ex) {
    log.error("Unhandled exception catch by global handler", ex);
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal system error occurred");
    problemDetail.setTitle("Internal Server Error");
    return problemDetail;
  }

  public ProblemDetail resolveToProblemDetail(Throwable ex) {
    return switch (ex) {
      case InsufficientContextException e -> handleInsufficientContextException(e);
      case TokenExceededLimitException e -> handleTokenExceededLimitException(e);
      case AiContentGenerateException e -> handleAiContentGenerateException(e);
      case MethodArgumentNotValidException e -> handleValidationExceptions(e);
      case null, default -> handleUncatchException(new Exception(ex));
    };
  }
}
