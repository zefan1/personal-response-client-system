package com.privateflow.modules.image;

public interface ImageRecognitionService {
  RecognitionResult recognize(byte[] image, Source source);
}
