package com.privateflow.modules.image;

public class ImageRecognitionException extends RuntimeException {
  private final String errorCode;

  public ImageRecognitionException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ImageRecognitionException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
