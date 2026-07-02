package com.privateflow.modules.api.web;

import com.privateflow.modules.api.chat.ChatOrchestrationService;
import com.privateflow.modules.api.chat.ChatRecognizeRequest;
import com.privateflow.modules.api.chat.ChatResponse;
import com.privateflow.modules.api.chat.GenerateRequest;
import com.privateflow.modules.api.chat.RegenerateRequest;
import com.privateflow.modules.api.chat.SendConfirmRequest;
import com.privateflow.modules.match.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

  private final ChatOrchestrationService orchestrationService;

  public ChatController(ChatOrchestrationService orchestrationService) {
    this.orchestrationService = orchestrationService;
  }

  @PostMapping("/recognize")
  public ApiResponse<ChatResponse> recognize(@RequestBody ChatRecognizeRequest request) {
    return ApiResponse.ok(orchestrationService.recognize(request));
  }

  @PostMapping("/generate")
  public ApiResponse<ChatResponse> generate(@RequestBody GenerateRequest request) {
    return ApiResponse.ok(orchestrationService.generate(request));
  }

  @PostMapping("/regenerate")
  public ApiResponse<ChatResponse> regenerate(@RequestBody RegenerateRequest request) {
    return ApiResponse.ok(orchestrationService.regenerate(request));
  }

  @PostMapping("/send-confirm")
  public ApiResponse<Map<String, Object>> sendConfirm(@RequestBody SendConfirmRequest request) {
    return ApiResponse.ok(orchestrationService.sendConfirm(request));
  }
}
