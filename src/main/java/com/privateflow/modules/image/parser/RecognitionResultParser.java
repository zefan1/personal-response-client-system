package com.privateflow.modules.image.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageErrorCodes;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.Message;
import com.privateflow.modules.image.RecognitionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecognitionResultParser {

  private static final Logger log = LoggerFactory.getLogger(RecognitionResultParser.class);
  private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
  private final ObjectMapper objectMapper;

  public RecognitionResultParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public RecognitionResult parse(String rawText) {
    if (rawText == null || rawText.isBlank()) {
      throw failed("图片识别服务返回空响应");
    }
    try {
      Matcher matcher = JSON_BLOCK.matcher(rawText);
      if (!matcher.find()) {
        throw failed("图片识别结果异常，建议重新截图或手动复制文字");
      }
      JsonNode root = objectMapper.readTree(matcher.group());
      String nickname = normalizeNickname(textOrNull(root.path("nickname")));
      String phone = normalizePhone(textOrNull(root.path("phone")));
      String timestamp = textOrNull(root.path("timestamp"));
      JsonNode messagesNode = root.path("messages");
      if (!messagesNode.isArray()) {
        throw failed("未能从图片中识别到聊天内容，请确认截图中包含聊天窗口");
      }
      List<Message> messages = new ArrayList<>();
      for (JsonNode node : messagesNode) {
        String text = textOrNull(node.path("text"));
        if (text == null || text.isBlank()) {
          continue;
        }
        messages.add(new Message(normalizeRole(textOrNull(node.path("role"))), text.trim()));
      }
      if (messages.isEmpty()) {
        throw failed("未能从图片中识别到聊天内容，请确认截图中包含聊天窗口");
      }
      return new RecognitionResult(nickname, phone, List.copyOf(messages), timestamp);
    } catch (ImageRecognitionException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, "图片识别结果异常，建议重新截图或手动复制文字", ex);
    }
  }

  private String normalizeRole(String role) {
    if (role == null || role.isBlank()) {
      log.warn("empty message role from image recognition, default to client");
      return "client";
    }
    return switch (role.trim()) {
      case "client", "customer", "对方", "客户", "顾客", "用户" -> "client";
      case "keeper", "staff", "自己", "同事", "管家", "我", "本人" -> "keeper";
      default -> {
        log.warn("unknown message role from image recognition: {}", role);
        yield "client";
      }
    };
  }

  private String normalizePhone(String phone) {
    if (phone == null) {
      return null;
    }
    String cleaned = phone.replaceAll("[-\\s]", "");
    return cleaned.matches("\\d{11}") ? cleaned : null;
  }

  private String normalizeNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return null;
    }
    return nickname.split("[/／,，]")[0].trim();
  }

  private String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String text = node.asText();
    return text == null || text.isBlank() || "null".equalsIgnoreCase(text.trim()) ? null : text;
  }

  private ImageRecognitionException failed(String message) {
    return new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, message);
  }
}
