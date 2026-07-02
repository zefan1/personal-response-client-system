package com.privateflow.modules.versions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DesktopVersionRepository {

  private final JdbcTemplate jdbcTemplate;

  public DesktopVersionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<DesktopVersion> list(VersionStatus status, DesktopPlatform platform, int page, int size) {
    List<Object> args = new ArrayList<>();
    String where = filter(status, platform, args);
    args.add(size);
    args.add((page - 1) * size);
    return jdbcTemplate.query("""
        SELECT id, version, platform, status, update_strategy, gradual_percent, download_url, file_size,
               changelog, revoked_at, revoke_reason, alternative_version, published_at, created_by, created_at, updated_at
        FROM desktop_versions
        """ + where + """
        ORDER BY COALESCE(published_at, created_at) DESC, id DESC
        LIMIT ? OFFSET ?
        """, this::map, args.toArray());
  }

  public long count(VersionStatus status, DesktopPlatform platform) {
    List<Object> args = new ArrayList<>();
    Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM desktop_versions " + filter(status, platform, args),
        Long.class,
        args.toArray());
    return value == null ? 0L : value;
  }

  public Optional<DesktopVersion> find(long id) {
    return jdbcTemplate.query("""
        SELECT id, version, platform, status, update_strategy, gradual_percent, download_url, file_size,
               changelog, revoked_at, revoke_reason, alternative_version, published_at, created_by, created_at, updated_at
        FROM desktop_versions
        WHERE id = ?
        LIMIT 1
        """, this::map, id).stream().findFirst();
  }

  public Optional<DesktopVersion> findByVersionAndPlatform(String version, DesktopPlatform platform) {
    return jdbcTemplate.query("""
        SELECT id, version, platform, status, update_strategy, gradual_percent, download_url, file_size,
               changelog, revoked_at, revoke_reason, alternative_version, published_at, created_by, created_at, updated_at
        FROM desktop_versions
        WHERE version = ? AND platform = ?
        LIMIT 1
        """, this::map, version, platform.name()).stream().findFirst();
  }

  public Optional<DesktopVersion> latestPublished(DesktopPlatform platform) {
    return jdbcTemplate.query("""
        SELECT id, version, platform, status, update_strategy, gradual_percent, download_url, file_size,
               changelog, revoked_at, revoke_reason, alternative_version, published_at, created_by, created_at, updated_at
        FROM desktop_versions
        WHERE platform = ? AND status = 'PUBLISHED'
        ORDER BY published_at DESC, id DESC
        LIMIT 1
        """, this::map, platform.name()).stream().findFirst();
  }

  public long create(DesktopVersionCreateRequest request, String createdBy) {
    jdbcTemplate.update("""
        INSERT INTO desktop_versions
          (version, platform, status, update_strategy, gradual_percent, download_url, file_size, changelog, created_by)
        VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
        """,
        request.version().trim(),
        request.platform().name(),
        normalizeStrategy(request.updateStrategy()).name(),
        request.gradualPercent(),
        blankToNull(request.downloadUrl()),
        request.fileSize(),
        request.changelog().trim(),
        createdBy);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void update(long id, DesktopVersionUpdateRequest request, DesktopVersion existing) {
    UpdateStrategy nextStrategy = request.updateStrategy() == null ? existing.updateStrategy() : request.updateStrategy();
    Integer nextGradualPercent = nextStrategy == UpdateStrategy.GRADUAL
        ? (request.gradualPercent() == null ? existing.gradualPercent() : request.gradualPercent())
        : null;
    jdbcTemplate.update("""
        UPDATE desktop_versions
        SET version = COALESCE(?, version),
            update_strategy = COALESCE(?, update_strategy),
            gradual_percent = ?,
            download_url = COALESCE(?, download_url),
            file_size = COALESCE(?, file_size),
            changelog = COALESCE(?, changelog),
            updated_at = NOW()
        WHERE id = ?
        """,
        blankToNull(request.version()),
        nextStrategy.name(),
        nextGradualPercent,
        blankToNull(request.downloadUrl()),
        request.fileSize(),
        blankToNull(request.changelog()),
        id);
  }

  public void publish(long id) {
    jdbcTemplate.update("""
        UPDATE desktop_versions
        SET status = 'PUBLISHED', published_at = NOW(), updated_at = NOW()
        WHERE id = ?
        """, id);
  }

  public void revoke(long id, VersionRevokeRequest request) {
    jdbcTemplate.update("""
        UPDATE desktop_versions
        SET status = 'REVOKED',
            revoked_at = NOW(),
            revoke_reason = ?,
            alternative_version = ?,
            updated_at = NOW()
        WHERE id = ?
        """, request.reason().trim(), blankToNull(request.alternativeVersion()), id);
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM desktop_versions WHERE id = ?", id);
  }

  public void report(VersionReportRequest request) {
    jdbcTemplate.update("""
        INSERT INTO desktop_client_versions (client_id, version, platform, os_version, last_reported_at)
        VALUES (?, ?, ?, ?, NOW())
        ON DUPLICATE KEY UPDATE
          version = VALUES(version),
          platform = VALUES(platform),
          os_version = VALUES(os_version),
          last_reported_at = NOW()
        """, request.clientId().trim(), request.version().trim(), request.platform().name(), blankToNull(request.osVersion()));
  }

  private String filter(VersionStatus status, DesktopPlatform platform, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    if (status != null) {
      where.append(" AND status = ? ");
      args.add(status.name());
    }
    if (platform != null) {
      where.append(" AND platform = ? ");
      args.add(platform.name());
    }
    return where.toString();
  }

  private DesktopVersion map(ResultSet rs, int rowNum) throws SQLException {
    return new DesktopVersion(
        rs.getLong("id"),
        rs.getString("version"),
        DesktopPlatform.valueOf(rs.getString("platform")),
        VersionStatus.valueOf(rs.getString("status")),
        UpdateStrategy.valueOf(rs.getString("update_strategy")),
        rs.getObject("gradual_percent", Integer.class),
        rs.getString("download_url"),
        rs.getObject("file_size", Long.class),
        rs.getString("changelog"),
        time(rs, "revoked_at"),
        rs.getString("revoke_reason"),
        rs.getString("alternative_version"),
        time(rs, "published_at"),
        rs.getString("created_by"),
        time(rs, "created_at"),
        time(rs, "updated_at"));
  }

  private LocalDateTime time(ResultSet rs, String column) throws SQLException {
    return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
  }

  private UpdateStrategy normalizeStrategy(UpdateStrategy strategy) {
    return strategy == null ? UpdateStrategy.OPTIONAL : strategy;
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
