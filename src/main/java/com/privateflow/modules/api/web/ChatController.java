package com.privateflow.modules.api.web;

import com.privateflow.modules.api.chat.ChatOrchestrationService;
import com.privateflow.modules.api.chat.AiUsageRequest;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.chat.ChatTaskRuntimeConfigResponse;
import com.privateflow.modules.api.chat.ChatRecognizeRequest;
import com.privateflow.modules.api.chat.ChatResponse;
import com.privateflow.modules.api.chat.GenerateRequest;
import com.privateflow.modules.api.chat.PendingReplyTaskSelectRequest;
import com.privateflow.modules.api.chat.PendingReplyTaskView;
import com.privateflow.modules.api.chat.RegenerateRequest;
import com.privateflow.modules.api.chat.RecognitionJobService;
import com.privateflow.modules.api.chat.RecognitionJobView;
import com.privateflow.modules.api.chat.SendConfirmRequest;
import com.privateflow.modules.match.ApiResponse;
import com.privateflow.modules.supervision.SupervisionConfig;
import com.privateflow.modules.supervision.SupervisionEventService;
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
  private final SupervisionEventService supervisionEventService;
  private final RecognitionJobService recognitionJobService;
  private final SupervisionConfig supervisionConfig;

  public ChatController(
      ChatOrchestrationService orchestrationService,
      SupervisionEventService supervisionEventService,
      RecognitionJobService recognitionJobService,
      SupervisionConfig supervisionConfig) {
    this.orchestrationService = orchestrationService;
    this.supervisionEventService = supervisionEventService;
    this.recognitionJobService = recognitionJobService;
    this.supervisionConfig = supervisionConfig;
  }

  @PostMapping("/recognize")
  public ApiResponse<ChatResponse> recognize(@RequestBody ChatRecognizeRequest request) {
    return ApiResponse.ok(orchestrationService.recognize(request));
  }

  @PostMapping("/recognition-jobs")
  public ApiResponse<RecognitionJobView> submitRecognitionJob(
      @RequestBody ChatRecognizeRequest request) {
    return ApiResponse.ok(recognitionJobService.submit(AuthContext.username(), request));
  }

  @GetMapping("/recognition-jobs/{jobId}")
  public ApiResponse<RecognitionJobView> getRecognitionJob(@PathVariable("jobId") String jobId) {
    return ApiResponse.ok(recognitionJobService.getOwned(jobId, AuthContext.username()));
  }

  @PostMapping("/recognition-jobs/{jobId}/cancel")
  public ApiResponse<RecognitionJobView> cancelRecognitionJob(@PathVariable("jobId") String jobId) {
    return ApiResponse.ok(recognitionJobService.cancelOwned(jobId, AuthContext.username()));
  }

  @GetMapping("/task-runtime-config")
  public ApiResponse<ChatTaskRuntimeConfigResponse> taskRuntimeConfig() {
    return ApiResponse.ok(new ChatTaskRuntimeConfigResponse(
        supervisionConfig.unfinishedTaskCap(),
        supervisionConfig.recentTaskDisplayCap()));
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

  @PostMapping("/ai-usage")
  public ApiResponse<Map<String, Object>> recordAiUsage(@RequestBody AiUsageRequest request) {
    return ApiResponse.ok(supervisionEventService.recordAiUsage(request));
  }
}
