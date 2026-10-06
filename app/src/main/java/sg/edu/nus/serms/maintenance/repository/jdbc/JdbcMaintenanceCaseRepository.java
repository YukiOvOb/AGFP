package sg.edu.nus.serms.maintenance.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCaseSnapshot;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.maintenance.repository.MaintenanceVersionConflictException;

@Repository
public class JdbcMaintenanceCaseRepository implements MaintenanceCaseRepository {
  private static final String COLUMNS = "maintenance_case_id, equipment_id, reported_by, assigned_to, "
      + "loan_id, status, fault_description, resolution_note, reported_at, resolved_at, version";
  private static final RowMapper<MaintenanceCase> MAPPER = JdbcMaintenanceCaseRepository::map;
  private final JdbcOperations jdbc;

  public JdbcMaintenanceCaseRepository(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public MaintenanceCase insert(MaintenanceCase item) {
    // Version defaults to zero in V002. All persistence uses the caller's Spring transaction.
    return jdbc.query("""
        INSERT INTO serms.maintenance_case
          (maintenance_case_id, equipment_id, reported_by, assigned_to, loan_id, status,
           fault_description, resolution_note, reported_at, resolved_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING
        """ + COLUMNS, MAPPER, item.maintenanceCaseId(), item.equipmentId(), item.reportedBy(),
        item.assignedTo(), item.loanId(), item.status().name(), item.faultDescription(),
        item.resolutionNote(), Timestamp.from(item.reportedAt()), timestamp(item.resolvedAt()))
        .stream().findFirst().orElseThrow(() -> new IllegalStateException("Maintenance insert returned no row"));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public MaintenanceCase update(MaintenanceCase item) {
    // Never set version: maintenance_version supplies OLD.version + 1 in RETURNING.
    return jdbc.query("""
        UPDATE serms.maintenance_case
        SET assigned_to = ?, loan_id = ?, status = ?, fault_description = ?,
            resolution_note = ?, resolved_at = ?
        WHERE maintenance_case_id = ? AND version = ?
        RETURNING
        """ + COLUMNS, MAPPER, item.assignedTo(), item.loanId(), item.status().name(),
        item.faultDescription(), item.resolutionNote(), timestamp(item.resolvedAt()),
        item.maintenanceCaseId(), item.version()).stream().findFirst()
        .orElseThrow(() -> new MaintenanceVersionConflictException(item.maintenanceCaseId(), item.version()));
  }

  @Override
  public Optional<MaintenanceCase> findById(UUID id) {
    // Non-locking snapshot: the service takes the Equipment lock before any case write.
    return jdbc.query("SELECT " + COLUMNS + " FROM serms.maintenance_case WHERE maintenance_case_id = ?",
        MAPPER, id).stream().findFirst();
  }

  @Override
  public List<MaintenanceCase> findOpenCases() {
    return List.copyOf(jdbc.query("SELECT " + COLUMNS + """
         FROM serms.maintenance_case WHERE status = 'OPEN'
        ORDER BY reported_at ASC, maintenance_case_id ASC
        """, MAPPER));
  }

  @Override
  public List<MaintenanceCase> findByAssignedTo(UUID technicianId) {
    return List.copyOf(jdbc.query("SELECT " + COLUMNS + """
         FROM serms.maintenance_case WHERE assigned_to = ?
        AND status IN ('ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'UNREPAIRABLE')
        ORDER BY reported_at DESC, maintenance_case_id ASC
        """, MAPPER, technicianId));
  }

  @Override
  public List<MaintenanceCase> findByEquipmentId(UUID equipmentId) {
    return List.copyOf(jdbc.query("SELECT " + COLUMNS + """
         FROM serms.maintenance_case WHERE equipment_id = ?
        ORDER BY reported_at DESC, maintenance_case_id ASC
        """, MAPPER, equipmentId));
  }

  private static MaintenanceCase map(ResultSet row, int index) throws SQLException {
    var resolvedAt = row.getTimestamp("resolved_at");
    return MaintenanceCase.restore(new MaintenanceCaseSnapshot(
        row.getObject("maintenance_case_id", UUID.class), row.getObject("equipment_id", UUID.class),
        row.getObject("reported_by", UUID.class), row.getObject("assigned_to", UUID.class),
        row.getObject("loan_id", UUID.class), MaintenanceStatus.valueOf(row.getString("status")),
        row.getString("fault_description"), row.getString("resolution_note"),
        row.getTimestamp("reported_at").toInstant(), resolvedAt == null ? null : resolvedAt.toInstant(),
        row.getInt("version")));
  }

  private static Timestamp timestamp(java.time.Instant value) {
    return value == null ? null : Timestamp.from(value);
  }
}
