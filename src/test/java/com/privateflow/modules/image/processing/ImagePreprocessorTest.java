package com.privateflow.modules.image.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImagePreprocessorTest {

  private ImagePreprocessor preprocessor;

  @BeforeEach
  void setUp() {
    ImageConfigProvider configProvider = mock(ImageConfigProvider.class);
    when(configProvider.get()).thenReturn(new ImageConfig(
        "", "", 5000, 5 * 1024 * 1024, 1920, 85, "", "", 3));
    preprocessor = new ImagePreprocessor(configProvider);
  }

  @Test
  void convertsPngToJpeg() throws Exception {
    BufferedImage png = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    png.setRGB(0, 0, Color.RED.getRGB());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(png, "png", output);

    assertJpeg(preprocessor.preprocess(output.toByteArray()));
  }

  @Test
  void convertsWebpToJpeg() throws Exception {
    byte[] webp = Base64.getDecoder().decode(
        "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAgA0JaACdLoB+AADsAD+8Oj3/yC5YXXI1/8gP+QH/ID/+PIAAAA=");

    assertJpeg(preprocessor.preprocess(webp));
  }

  private void assertJpeg(byte[] image) throws Exception {
    assertThat(image).hasSizeGreaterThan(2);
    assertThat(image[0] & 0xFF).isEqualTo(0xFF);
    assertThat(image[1] & 0xFF).isEqualTo(0xD8);
    assertThat(ImageIO.read(new ByteArrayInputStream(image))).isNotNull();
  }
}
