package com.privateflow.modules.arrival;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.ImageRecognitionService;
import com.privateflow.modules.image.RecognitionResult;
import com.privateflow.modules.image.Source;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerMatchService;
import com.privateflow.modules.match.CustomerSummary;
import com.privateflow.modules.match.MatchRequest;
import com.privateflow.modules.match.MatchResult;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

/** Extracts only the active chat identity and matches it to an accessible customer. */
@Service
public class CurrentChatCustomerService {
  private final ImageRecognitionService recognition;
  private final CustomerMatchService matcher;
  private final CustomerAccessService access;
  private final CustomerQueryService customers;

  public CurrentChatCustomerService(
      ImageRecognitionService recognition, CustomerMatchService matcher, CustomerAccessService access,
      CustomerQueryService customers) {
    this.recognition = recognition; this.matcher = matcher; this.access = access; this.customers = customers;
  }

  public CurrentChatMatchResult match(CurrentChatMatchRequest request) {
    if (request == null || request.imageBase64() == null || request.imageBase64().isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请先截取当前微信聊天窗口");
    }
    RecognitionResult recognized = recognize(request.imageBase64());
    String nickname = first(recognized.nickname(), recognized.customerIdentifier());
    MatchResult result;
    try {
      result = matcher.match(new MatchRequest(nickname, recognized.phone(), null, null, AuthContext.username()));
    } catch (CustomerMatchException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ApiException("MATCH_FAILED", "当前微信客户匹配失败，请重试");
    }
    List<CustomerSummary> candidates = result.customers() == null ? List.of() : result.customers().stream()
        .filter(candidate -> candidate != null && candidate.customerId() != null)
        .filter(candidate -> access.canAccess(customers.getById(candidate.customerId())))
        .toList();
    String type = candidates.isEmpty() ? "NONE" : candidates.size() == 1 ? "MATCHED" : "MULTIPLE";
    return new CurrentChatMatchResult(nickname, type, candidates);
  }

  private RecognitionResult recognize(String imageBase64) {
    try {
      return recognition.recognize(Base64.getDecoder().decode(imageBase64), Source.BUTTON_CLICK);
    } catch (IllegalArgumentException ex) {
      throw new ApiException("IMAGE_FORMAT", "截图格式不支持，请重新截取当前微信窗口");
    } catch (ImageRecognitionException ex) {
      throw new ApiException(ex.getErrorCode(), ex.getMessage());
    } catch (RuntimeException ex) {
      throw new ApiException("IMAGE_RECOGNITION_FAILED", "无法识别当前微信客户，请重试");
    }
  }

  private static String first(String first, String second) {
    return first != null && !first.isBlank() ? first.trim() : second == null ? "" : second.trim();
  }
}
