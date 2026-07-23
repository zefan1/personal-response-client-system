package com.privateflow.modules.api.web;

import com.privateflow.modules.api.chat.ChatOrchestrationService;
import com.privateflow.modules.api.chat.ChatRecognizeRequest;
import com.privateflow.modules.api.chat.ChatResponse;
import com.privateflow.modules.api.chat.GenerateRequest;
import com.privateflow.modules.api.chat.PendingReplyTaskSelectRequest;
import com.privateflow.modules.api.chat.PendingReplyTaskView;
import com.privateflow.modules.api.chat.RegenerateRequest;
import com.privateflow.modules.api.chat.SendConfirmRequest;
import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping("/reply-tasks")
  public ApiResponse<List<PendingReplyTaskView>> listReplyTasks() {
    return ApiResponse.ok(orchestrationService.listPendingReplyTasks());
  }

  @GetMapping("/reply-tasks/{taskId}")
  public ApiResponse<PendingReplyTaskView> getReplyTask(@PathVariable("taskId") String taskId) {
    return ApiResponse.ok(orchestrationService.getPendingReplyTask(taskId));
  }

  @PostMapping("/reply-tasks/{taskId}/confirm")
  public ApiResponse<ChatResponse> confirmReplyTask(
      @PathVariable("taskId") String taskId,
      @RequestBody PendingReplyTaskSelectRequest request) {
    return ApiResponse.ok(orchestrationService.confirmPendingReplyTask(taskId, request));
  }

  @PostMapping("/reply-tasks/{taskId}/retry")
  public ApiResponse<ChatResponse> retryReplyTask(@PathVariable("taskId") String taskId) {
    return ApiResponse.ok(orchestrationService.retryPendingReplyTask(taskId));
  }

  @PostMapping("/reply-tasks/{taskId}/cancel")
  public ApiResponse<PendingReplyTaskView> cancelReplyTask(@PathVariable("taskId") String taskId) {
    return ApiResponse.ok(orchestrationService.cancelPendingReplyTask(taskId));
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
