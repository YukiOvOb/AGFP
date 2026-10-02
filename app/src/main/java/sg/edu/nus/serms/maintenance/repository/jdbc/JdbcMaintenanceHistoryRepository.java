package sg.edu.nus.serms.maintenance.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryAction;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceHistoryRepository;

@Repository
public class JdbcMaintenanceHistoryRepository implements MaintenanceHistoryRepository {
  private static final String PREFIX = "MAINTENANCE_";
  private static final String HISTORY = """
      SELECT a.entity_id, m.equipment_id, a.action, a.actor_id, a.occurred_at, a.change_summary
      FROM serms.audit_log a
      JOIN serms.maintenance_case m ON m.maintenance_case_id = a.entity_id
      WHERE a.entity_type = 'MaintenanceCase' AND a.outcome = 'SUCCESS'
        AND a.action IN ('MAINTENANCE_REPORT_FAULT', 'MAINTENANCE_ASSIGN', 'MAINTENANCE_START',
          'MAINTENANCE_RECORD_DIAGNOSIS', 'MAINTENANCE_RECORD_REPAIR_ACTION', 'MAINTENANCE_ADD_NOTE',
          'MAINTENANCE_RESOLVE', 'MAINTENANCE_MARK_UNREPAIRABLE')
      """;
  private static final RowMapper<MaintenanceHistoryEntry> MAPPER = JdbcMaintenanceHistoryRepository::map;
  private final JdbcOperations jdbc;

  public JdbcMaintenanceHistoryRepository(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(MaintenanceHistoryEntry entry) {
    UUID auditId = UUID.randomUUID();
    // Local correlation only. A future REST adapter may supply actual request correlation IDs.
    String requestId = "maintenance:" + auditId;
    jdbc.update("""
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id, change_summary, occurred_at)
        VALUES (?, ?, ?, 'MaintenanceCase', ?, 'SUCCESS', ?, ?, ?)
        """, auditId, entry.actorId(), PREFIX + entry.action().name(), entry.maintenanceCaseId(),
        requestId, entry.detail(), Timestamp.from(entry.occurredAt()));
  }

  @Override
  public List<MaintenanceHistoryEntry> findByCaseId(UUID id) {
    return jdbc.query(HISTORY + " AND a.entity_id = ? ORDER BY a.occurred_at ASC, a.audit_id ASC",
        MAPPER, id);
  }

  @Override
  public List<MaintenanceHistoryEntry> findByEquipmentId(UUID id) {
    return jdbc.query(HISTORY + " AND m.equipment_id = ? ORDER BY a.occurred_at ASC, a.audit_id ASC",
        MAPPER, id);
  }

  private static MaintenanceHistoryEntry map(ResultSet row, int index) throws SQLException {
    var action = MaintenanceHistoryAction.valueOf(row.getString("action").substring(PREFIX.length()));
    MaintenanceStatus before = switch (action) {
      case REPORT_FAULT -> null;
      case ASSIGN -> MaintenanceStatus.OPEN;
      case START -> MaintenanceStatus.ASSIGNED;
      default -> MaintenanceStatus.IN_PROGRESS;
    };
    MaintenanceStatus after = switch (action) {
      case REPORT_FAULT -> MaintenanceStatus.OPEN;
      case ASSIGN -> MaintenanceStatus.ASSIGNED;
      case RESOLVE -> MaintenanceStatus.RESOLVED;
      case MARK_UNREPAIRABLE -> MaintenanceStatus.UNREPAIRABLE;
      default -> MaintenanceStatus.IN_PROGRESS;
    };
    return new MaintenanceHistoryEntry(row.getObject("entity_id", UUID.class),
        row.getObject("equipment_id", UUID.class), action, before, after,
        row.getObject("actor_id", UUID.class), row.getTimestamp("occurred_at").toInstant(),
        row.getString("change_summary"));
  }
}
