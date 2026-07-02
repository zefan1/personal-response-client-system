package com.privateflow.modules.image.client;

public interface ImageRecognitionClient {
  String recognize(byte[] jpegImage);
}
