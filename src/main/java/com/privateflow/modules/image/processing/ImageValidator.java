package com.privateflow.modules.image.processing;

import com.privateflow.modules.image.ImageFormatException;
import com.privateflow.modules.image.config.ImageConfigProvider;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator {

  private final ImageConfigProvider configProvider;

  public ImageValidator(ImageConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public void validate(byte[] image) {
    if (image == null || image.length == 0) {
      throw new ImageFormatException("图片格式不支持，请使用 PNG、JPEG 或 WebP 格式的截图");
    }
    if (!isPng(image) && !isJpeg(image) && !isWebp(image)) {
      throw new ImageFormatException("图片格式不支持，请使用 PNG、JPEG 或 WebP 格式的截图");
    }
    if (image.length > configProvider.get().maxSizeBytes()) {
      throw new ImageFormatException("图片体积过大，请压缩后重试（最大 5MB）");
    }
  }

  private boolean isPng(byte[] image) {
    byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    return hasPrefix(image, signature);
  }

  private boolean isJpeg(byte[] image) {
    return image.length >= 2 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8;
  }

  private boolean isWebp(byte[] image) {
    return image.length >= 12
        && image[0] == 0x52 && image[1] == 0x49 && image[2] == 0x46 && image[3] == 0x46
        && image[8] == 0x57 && image[9] == 0x45 && image[10] == 0x42 && image[11] == 0x50;
  }

  private boolean hasPrefix(byte[] image, byte[] prefix) {
    if (image.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (image[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
