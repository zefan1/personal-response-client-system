package com.privateflow.modules.image;

public class ImageFormatException extends ImageRecognitionException {

  public ImageFormatException(String message) {
    super(ImageErrorCodes.IMAGE_FORMAT_UNSUPPORTED, message);
  }

  public ImageFormatException(String message, Throwable cause) {
    super(ImageErrorCodes.IMAGE_FORMAT_UNSUPPORTED, message, cause);
  }
}
