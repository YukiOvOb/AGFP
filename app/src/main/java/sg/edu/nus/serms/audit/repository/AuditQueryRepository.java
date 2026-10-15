package sg.edu.nus.serms.audit.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.audit.domain.AuditEntry;

/** Read-only projection of the existing append-only audit log. No update/delete API. */
@Repository
public class AuditQueryRepository {
  private final JdbcTemplate jdbc;
  public AuditQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public AuditEntry.Page list(UUID actor, String action, String entityType, Instant from, Instant to,
      int page, int size) {
    var values = new ArrayList<Object>();
    var where = new StringBuilder(" WHERE 1=1");
    if (actor != null) { where.append(" AND a.actor_id = ?"); values.add(actor); }
    if (!action.isEmpty()) { where.append(" AND a.action = ?"); values.add(action); }
    if (!entityType.isEmpty()) { where.append(" AND a.entity_type = ?"); values.add(entityType); }
    if (from != null) { where.append(" AND a.occurred_at >= ?"); values.add(Timestamp.from(from)); }
    if (to != null) { where.append(" AND a.occurred_at < ?"); values.add(Timestamp.from(to)); }
    long total = jdbc.queryForObject("SELECT count(*) FROM serms.audit_log a" + where,
        Long.class, values.toArray());
    values.add(size); values.add((long) page * size);
    var entries = jdbc.query("""
        SELECT a.audit_id, a.actor_id, u.display_name AS actor_name, a.action, a.entity_type,
          a.entity_id, a.outcome, a.change_summary, a.occurred_at
        FROM serms.audit_log a LEFT JOIN serms.app_user u ON u.user_id = a.actor_id
        """ + where + " ORDER BY a.occurred_at DESC, a.audit_id ASC LIMIT ? OFFSET ?",
        (rs, row) -> new AuditEntry(rs.getObject("audit_id", UUID.class),
            rs.getObject("actor_id", UUID.class), rs.getString("actor_name"), rs.getString("action"),
            rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), rs.getString("outcome"),
            rs.getString("change_summary"), rs.getTimestamp("occurred_at").toInstant()), values.toArray());
    return new AuditEntry.Page(entries, total, page, size);
  }
}
