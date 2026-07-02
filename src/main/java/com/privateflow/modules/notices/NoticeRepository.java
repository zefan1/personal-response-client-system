package com.privateflow.modules.notices;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NoticeRepository {

  private final JdbcTemplate jdbcTemplate;

  public NoticeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<SystemNotice> list(NoticeStatus status, NoticeLevel level, NoticeSource source, Boolean stopped, int page, int size) {
    List<Object> args = new ArrayList<>();
    String where = filter(status, level, source, stopped, args);
    args.add(size);
    args.add((page - 1) * size);
    return jdbcTemplate.query("""
        SELECT id, notice_id, title, content, level, source, status, is_stopped, publish_at, pushed_at,
               expire_at, stopped_at, created_by, created_at, updated_at
        FROM system_notices
        """ + where + """
        ORDER BY created_at DESC, id DESC
        LIMIT ? OFFSET ?
        """, this::map, args.toArray());
  }

  public long count(NoticeStatus status, NoticeLevel level, NoticeSource source, Boolean stopped) {
    List<Object> args = new ArrayList<>();
    Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_notices " + filter(status, level, source, stopped, args),
        Long.class,
        args.toArray());
    return value == null ? 0L : value;
  }

  public Optional<SystemNotice> find(long id) {
    return jdbcTemplate.query("""
        SELECT id, notice_id, title, content, level, source, status, is_stopped, publish_at, pushed_at,
               expire_at, stopped_at, created_by, created_at, updated_at
        FROM system_notices
        WHERE id = ?
        LIMIT 1
        """, this::map, id).stream().findFirst();
  }

  public List<SystemNotice> active() {
    return jdbcTemplate.query("""
        SELECT id, notice_id, title, content, level, source, status, is_stopped, publish_at, pushed_at,
               expire_at, stopped_at, created_by, created_at, updated_at
        FROM system_notices
        WHERE status = 'PUBLISHED'
          AND is_stopped = 0
          AND expire_at > NOW()
        ORDER BY created_at DESC, id DESC
        """, this::map);
  }

  public List<SystemNotice> dueScheduled(int limit) {
    return jdbcTemplate.query("""
        SELECT id, notice_id, title, content, level, source, status, is_stopped, publish_at, pushed_at,
               expire_at, stopped_at, created_by, created_at, updated_at
        FROM system_notices
        WHERE status = 'SCHEDULED'
          AND is_stopped = 0
          AND publish_at <= NOW()
        ORDER BY publish_at ASC, id ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """, this::map, limit);
  }

  public boolean activeAutoContentExists(String content) {
    Long count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM system_notices
        WHERE source = 'AUTO'
          AND is_stopped = 0
          AND status = 'PUBLISHED'
          AND content = ?
          AND expire_at > NOW()
        """, Long.class, content);
    return count != null && count > 0;
  }

  public long create(SystemNotice notice) {
    jdbcTemplate.update("""
        INSERT INTO system_notices
          (notice_id, title, content, level, source, status, is_stopped, publish_at, pushed_at, expire_at, created_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        notice.noticeId(),
        notice.title(),
        notice.content(),
        notice.level().name(),
        notice.source().name(),
        notice.status().name(),
        notice.isStopped(),
        notice.publishAt(),
        notice.pushedAt(),
        notice.expireAt(),
        notice.createdBy());
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0L : id;
  }

  public void updateScheduled(long id, NoticeUpdateRequest request, LocalDateTime expireAt) {
    jdbcTemplate.update("""
        UPDATE system_notices
        SET title = ?,
            content = ?,
            level = ?,
            publish_at = ?,
            expire_at = ?,
            updated_at = NOW()
        WHERE id = ?
        """,
        request.title().trim(),
        request.content().trim(),
        request.level().name(),
        request.publishAt(),
        expireAt,
        id);
  }

  public void markPublished(long id) {
    jdbcTemplate.update("""
        UPDATE system_notices
        SET status = 'PUBLISHED', pushed_at = NOW(), updated_at = NOW()
        WHERE id = ? AND status = 'SCHEDULED' AND is_stopped = 0
        """, id);
  }

  public int stop(long id) {
    return jdbcTemplate.update("""
        UPDATE system_notices
        SET is_stopped = 1, stopped_at = NOW(), updated_at = NOW()
        WHERE id = ? AND is_stopped = 0
        """, id);
  }

  public int stopAutoByContentKeyword(String keyword) {
    return jdbcTemplate.update("""
        UPDATE system_notices
        SET is_stopped = 1, stopped_at = NOW(), updated_at = NOW()
        WHERE source = 'AUTO'
          AND is_stopped = 0
          AND content LIKE ?
        """, "%" + keyword + "%");
  }

  public int delete(long id) {
    return jdbcTemplate.update("DELETE FROM system_notices WHERE id = ? AND is_stopped = 1", id);
  }

  public int countForDatePrefix(String prefix) {
    Integer count = jdbcTemplate.queryForObject("""
        SELECT COUNT(*) FROM system_notices WHERE notice_id LIKE ?
        """, Integer.class, prefix + "%");
    return count == null ? 0 : count;
  }

  private String filter(NoticeStatus status, NoticeLevel level, NoticeSource source, Boolean stopped, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    if (status != null) {
      where.append(" AND status = ? ");
      args.add(status.name());
    }
    if (level != null) {
      where.append(" AND level = ? ");
      args.add(level.name());
    }
    if (source != null) {
      where.append(" AND source = ? ");
      args.add(source.name());
    }
    if (stopped != null) {
      where.append(" AND is_stopped = ? ");
      args.add(stopped);
    }
    return where.toString();
  }

  private SystemNotice map(ResultSet rs, int rowNum) throws SQLException {
    return new SystemNotice(
        rs.getLong("id"),
        rs.getString("notice_id"),
        rs.getString("title"),
        rs.getString("content"),
        NoticeLevel.valueOf(rs.getString("level")),
        NoticeSource.valueOf(rs.getString("source")),
        NoticeStatus.valueOf(rs.getString("status")),
        rs.getBoolean("is_stopped"),
        time(rs, "publish_at"),
        time(rs, "pushed_at"),
        time(rs, "expire_at"),
        time(rs, "stopped_at"),
        rs.getString("created_by"),
        time(rs, "created_at"),
        time(rs, "updated_at"));
  }

  private LocalDateTime time(ResultSet rs, String column) throws SQLException {
    return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
  }
}
