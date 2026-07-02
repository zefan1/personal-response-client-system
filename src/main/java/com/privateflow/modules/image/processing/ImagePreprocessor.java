package com.privateflow.modules.image.processing;

import com.privateflow.modules.image.ImageFormatException;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ImagePreprocessor {

  private final ImageConfigProvider configProvider;

  public ImagePreprocessor(ImageConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public byte[] preprocess(byte[] image) {
    BufferedImage original = null;
    BufferedImage scaled = null;
    try {
      original = ImageIO.read(new ByteArrayInputStream(image));
      if (original == null) {
        throw new ImageFormatException("图片文件损坏，请重新截图");
      }
      ImageConfig config = configProvider.get();
      double scale = Math.min(1.0, (double) config.maxDimensionPx() / Math.max(original.getWidth(), original.getHeight()));
      int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
      int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
      scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = scaled.createGraphics();
      try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(original, 0, 0, width, height, null);
      } finally {
        graphics.dispose();
      }
      return writeJpeg(scaled, config.jpegQuality());
    } catch (ImageFormatException ex) {
      throw ex;
    } catch (OutOfMemoryError ex) {
      throw new ImageFormatException("图片尺寸过大，请使用较低分辨率截图", ex);
    } catch (Exception ex) {
      throw new ImageFormatException("图片文件损坏，请重新截图", ex);
    } finally {
      if (original != null) {
        original.flush();
      }
      if (scaled != null) {
        scaled.flush();
      }
    }
  }

  private byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw new ImageFormatException("图片文件损坏，请重新截图");
    }
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(baos)) {
      writer.setOutput(output);
      ImageWriteParam params = writer.getDefaultWriteParam();
      if (params.canWriteCompressed()) {
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), params);
      output.flush();
      return baos.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
