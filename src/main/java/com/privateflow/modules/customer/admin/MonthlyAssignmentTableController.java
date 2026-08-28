package com.privateflow.modules.customer.admin;

import com.privateflow.modules.match.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonthlyAssignmentTableController {

  private final MonthlyAssignmentTableService service;

  public MonthlyAssignmentTableController(MonthlyAssignmentTableService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/assignment-tables")
  public ApiResponse<List<AssignmentTableView>> list() {
    return ApiResponse.ok(service.list().stream().map(AssignmentTableView::from).toList());
  }

  @PostMapping("/api/v1/assignment-tables")
  public ApiResponse<AssignmentTableView> create(@RequestBody MonthlyAssignmentTableCreateRequest request) {
    return ApiResponse.ok(AssignmentTableView.from(service.create(request)));
  }

  @PostMapping("/api/v1/assignment-tables/{id}/rebind")
  public ApiResponse<AssignmentTableView> rebind(@PathVariable("id") long id) {
    return ApiResponse.ok(AssignmentTableView.from(service.rebind(id)));
  }

  @DeleteMapping("/api/v1/assignment-tables/{id}")
  public ApiResponse<Map<String, Boolean>> delete(@PathVariable("id") long id) {
    service.delete(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  public record AssignmentTableView(
      long id,
      String tableName,
      String monthKey,
      String documentUrl,
      String status,
      String errorMessage,
      String createdBy,
      LocalDateTime createdAt,
      LocalDateTime activatedAt) {
    static AssignmentTableView from(MonthlyAssignmentTable value) {
      return new AssignmentTableView(value.id(), value.tableName(), value.monthKey(), value.documentUrl(),
          value.status(), value.errorMessage(), value.createdBy(), value.createdAt(), value.activatedAt());
    }
  }
}
