package com.privateflow.modules.image.service;

import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import com.privateflow.modules.image.client.ImageRecognitionClient;
import com.privateflow.modules.image.health.ImageServiceHealthMonitor;
import com.privateflow.modules.image.parser.RecognitionResultParser;
import com.privateflow.modules.image.processing.ImagePreprocessor;
import com.privateflow.modules.image.processing.ImageValidator;
import org.springframework.stereotype.Service;

@Service
public class ImageRecognitionServiceImpl implements ImageRecognitionService {

  private final ImageValidator validator;
  private final ImagePreprocessor preprocessor;
  private final ImageRecognitionClient client;
  private final RecognitionResultParser parser;
  private final ImageServiceHealthMonitor healthMonitor;

  public ImageRecognitionServiceImpl(
      ImageValidator validator,
      ImagePreprocessor preprocessor,
      ImageRecognitionClient client,
      RecognitionResultParser parser,
      ImageServiceHealthMonitor healthMonitor) {
    this.validator = validator;
    this.preprocessor = preprocessor;
    this.client = client;
    this.parser = parser;
    this.healthMonitor = healthMonitor;
  }

  @Override
  public RecognitionResult recognize(byte[] image, Source source) {
    byte[] workingImage = image;
    try {
      validator.validate(workingImage);
      byte[] jpeg = preprocessor.preprocess(workingImage);
      workingImage = null;
      String rawResponse = client.recognize(jpeg);
      jpeg = null;
      RecognitionResult result = parser.parse(rawResponse);
      healthMonitor.recordSuccess();
      return result;
    } catch (ImageRecognitionException ex) {
      healthMonitor.recordFailure(ex);
      throw ex;
    } finally {
      workingImage = null;
    }
  }
}
