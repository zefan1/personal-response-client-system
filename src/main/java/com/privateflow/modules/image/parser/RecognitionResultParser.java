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
  private static final String UNABLE_TO_DETERMINE = "UNABLE_TO_DETERMINE";
  private static final String DEFAULT_FAILURE_REASON = "当前窗口未显示可识别的主聊天会话";
  private static final String GENERIC_CHAT_FAILURE_REASON = "未能从图片中识别到聊天内容，请确认截图中包含聊天窗口";
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
      String status = normalizeStatus(textOrNull(root.path("status")));
      String failureReason = textOrNull(root.path("failureReason"));
      if (UNABLE_TO_DETERMINE.equals(status)) {
        throw failed(firstNonBlank(failureReason, DEFAULT_FAILURE_REASON));
      }
      String platform = normalizePlatform(textOrNull(root.path("platform")));
      String customerIdentifier = normalizeIdentifier(textOrNull(root.path("customerIdentifier")));
      String nickname = normalizeNickname(textOrNull(root.path("nickname")));
      nickname = firstNonBlank(nickname, customerIdentifier);
      String phone = normalizePhone(textOrNull(root.path("phone")));
      String timestamp = textOrNull(root.path("timestamp"));
      JsonNode messagesNode = root.path("messages");
      if (!messagesNode.isArray()) {
        throw failed(firstNonBlank(failureReason, GENERIC_CHAT_FAILURE_REASON));
      }
      List<Message> messages = new ArrayList<>();
      for (JsonNode node : messagesNode) {
        String text = textOrNull(node.path("text"));
        if (text == null || text.isBlank()) {
          continue;
        }
        messages.add(new Message(normalizeRole(providerRole(node)), text.trim()));
      }
      if (messages.isEmpty()) {
        throw failed(firstNonBlank(failureReason, GENERIC_CHAT_FAILURE_REASON));
      }
      return new RecognitionResult(
          nickname,
          phone,
          List.copyOf(messages),
          timestamp,
          customerIdentifier,
          platform,
          confidence(root.path("confidence")));
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

  private String providerRole(JsonNode node) {
    String role = textOrNull(node.path("role"));
    if (role == null || role.isBlank()) {
      role = textOrNull(node.path("sender"));
    }
    if (role == null || role.isBlank()) {
      return null;
    }
    String normalized = role.trim().toLowerCase();
    if (List.of("client", "customer", "sender", "user").contains(normalized)) {
      return "client";
    }
    if (List.of("keeper", "staff", "assistant", "service", "agent").contains(normalized)) {
      return "keeper";
    }
    return role;
  }

  private String normalizePhone(String phone) {
    if (phone == null) {
      return null;
    }
    String cleaned = phone.replaceAll("[-\\s]", "");
    return cleaned.matches("\\d{11}") ? cleaned : null;
  }

  private String normalizeIdentifier(String identifier) {
    return identifier == null || identifier.isBlank() ? null : identifier.trim();
  }

  private String normalizePlatform(String platform) {
    if (platform == null || platform.isBlank()) {
      return "UNKNOWN";
    }
    return platform.trim().toUpperCase().replace('-', '_').replace(' ', '_');
  }

  private String normalizeStatus(String status) {
    return status == null ? "" : status.trim().toUpperCase();
  }

  private double confidence(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return 0.0;
    }
    double value;
    if (node.isNumber()) {
      value = node.asDouble();
    } else {
      try {
        value = Double.parseDouble(node.asText());
      } catch (NumberFormatException ex) {
        return 0.0;
      }
    }
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
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

  private String firstNonBlank(String first, String second) {
    return first == null || first.isBlank() ? second : first.trim();
  }

  private ImageRecognitionException failed(String message) {
    return new ImageRecognitionException(ImageErrorCodes.IMAGE_RECOGNITION_FAILED, message);
  }
}
