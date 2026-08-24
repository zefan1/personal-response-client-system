package com.privateflow.modules.arrival;

import com.privateflow.modules.match.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/arrival-handover")
public class ArrivalHandoverController {
  private final ArrivalHandoverService service; private final ArrivalReportStorage reports;
  public ArrivalHandoverController(ArrivalHandoverService service, ArrivalReportStorage reports){this.service=service;this.reports=reports;}
  @GetMapping("/tasks") public ApiResponse<List<ArrivalHandoverTaskView>> tasks(){return ApiResponse.ok(service.pendingTasks());}
  @GetMapping("/options") public ApiResponse<Map<String,List<String>>> options(){return ApiResponse.ok(service.options());}
  @PostMapping(value="/tasks/{id}/reports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ApiResponse<ArrivalReportAttachment> upload(@PathVariable("id") long id,@RequestParam("file") MultipartFile file){return ApiResponse.ok(service.upload(id,file));}
  @PostMapping("/tasks/{id}/complete") public ApiResponse<ArrivalHandoverCompletionResult> complete(@PathVariable("id") long id,@RequestBody ArrivalHandoverCompleteRequest request){return ApiResponse.ok(service.completeAndSync(id,request));}
  @PostMapping("/tasks/{id}/remind") public ApiResponse<Void> remind(@PathVariable("id") long id){service.remind(id);return ApiResponse.ok(null);}
  @GetMapping("/tasks/{id}/reports/{attachmentId:.+}") public ResponseEntity<Resource> report(@PathVariable("id") long id,@PathVariable("attachmentId") String attachmentId){service.requireTask(id);Resource file=reports.load(id,attachmentId);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=report").contentType(MediaType.APPLICATION_OCTET_STREAM).body(file);}
}
