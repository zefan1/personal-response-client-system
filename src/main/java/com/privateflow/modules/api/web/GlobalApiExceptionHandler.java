package com.privateflow.modules.api.web;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.match.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (ApiErrorCodes.BAD_REQUEST.equals(ex.getErrorCode()) || ApiErrorCodes.CONFIG_INVALID.equals(ex.getErrorCode())
        || ApiErrorCodes.VERSION_EXISTS.equals(ex.getErrorCode()) || ApiErrorCodes.VERSION_PACKAGE_MISSING.equals(ex.getErrorCode())
        || ApiErrorCodes.VERSION_UPLOAD_FAILED.equals(ex.getErrorCode())) {
      status = HttpStatus.BAD_REQUEST;
    } else if (ApiErrorCodes.VERSION_STATUS_INVALID.equals(ex.getErrorCode()) || ApiErrorCodes.CONFLICT.equals(ex.getErrorCode())) {
      status = HttpStatus.CONFLICT;
    } else if (ApiErrorCodes.AUTH_FAILED.equals(ex.getErrorCode())) {
      status = HttpStatus.UNAUTHORIZED;
    } else if (ApiErrorCodes.ACCOUNT_DISABLED.equals(ex.getErrorCode())) {
      status = HttpStatus.UNAUTHORIZED;
    } else if (ApiErrorCodes.LOGIN_RATE_LIMITED.equals(ex.getErrorCode())) {
      status = HttpStatus.TOO_MANY_REQUESTS;
    } else if (ApiErrorCodes.FORBIDDEN.equals(ex.getErrorCode())) {
      status = HttpStatus.FORBIDDEN;
    }
    return ResponseEntity.status(status).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      MissingServletRequestParameterException.class,
      HttpMessageNotReadableException.class,
      IllegalArgumentException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
    return ResponseEntity.badRequest().body(ApiResponse.error(ApiErrorCodes.BAD_REQUEST, "request parameter invalid"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error(ApiErrorCodes.INTERNAL_ERROR, "system internal error"));
  }
}
