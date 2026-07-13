package com.privateflow.modules.image.client;

import com.privateflow.modules.image.config.ImageConfig;

public interface ConfigurableImageRecognitionClient {
  String recognize(byte[] jpegImage, ImageConfig config);
}
