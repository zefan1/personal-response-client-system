package com.privateflow.modules.arrival;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.match.CustomerMatchException;
import com.privateflow.modules.match.CustomerMatchErrorCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ArrivalReportStorage {
  private static final long MAX_SIZE = 10L * 1024 * 1024;
  private final Path root;
  private final ObjectMapper json;
  public ArrivalReportStorage(@Value("${arrival-report.storage.root:${ARRIVAL_REPORT_STORAGE_ROOT:uploads/arrival-reports}}") String root, ObjectMapper json) {
    this.root = Path.of(root).toAbsolutePath().normalize(); this.json = json;
  }
  public ArrivalReportAttachment store(long taskId, MultipartFile file) {
    if (file == null || file.isEmpty()) throw bad("请选择客户报告图片");
    if (file.getSize() > MAX_SIZE) throw bad("客户报告单张不能超过 10MB");
    String original = file.getOriginalFilename() == null ? "report" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._()\\p{IsHan}-]", "_");
    String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
    if (!List.of(".jpg", ".jpeg", ".png", ".webp", ".pdf").contains(ext)) throw bad("客户报告仅支持 JPG、PNG、WEBP 或 PDF");
    String id = UUID.randomUUID().toString().replace("-", "");
    try { Files.createDirectories(root); Files.copy(file.getInputStream(), root.resolve(taskId + "-" + id + ext)); }
    catch (IOException ex) { throw bad("客户报告保存失败，请重试"); }
    return new ArrivalReportAttachment(id + ext, original);
  }
  public String encode(List<ArrivalReportAttachment> files) { try { return json.writeValueAsString(files == null ? List.of() : files); } catch (IOException ex) { throw new IllegalStateException(ex); } }
  public List<ArrivalReportAttachment> decode(String value) { if (value == null || value.isBlank()) return List.of(); try { return json.readValue(value, new TypeReference<List<ArrivalReportAttachment>>() {}); } catch (IOException ex) { return List.of(); } }
  public Resource load(long taskId, String attachmentId) {
    String safe = attachmentId == null ? "" : attachmentId.replaceAll("[^A-Za-z0-9.]", "");
    try { Resource resource = new UrlResource(root.resolve(taskId + "-" + safe).normalize().toUri()); if (resource.exists() && resource.isReadable()) return resource; } catch (Exception ignored) { }
    throw bad("客户报告不存在");
  }
  private CustomerMatchException bad(String message) { return new CustomerMatchException(CustomerMatchErrorCodes.BAD_REQUEST, message); }
}
