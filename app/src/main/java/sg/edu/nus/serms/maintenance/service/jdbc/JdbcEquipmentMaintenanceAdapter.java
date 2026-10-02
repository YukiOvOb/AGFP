package sg.edu.nus.serms.maintenance.service.jdbc;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.service.EquipmentMaintenancePort;

@Component
public class JdbcEquipmentMaintenanceAdapter implements EquipmentMaintenancePort {
  private final JdbcOperations jdbc;

  public JdbcEquipmentMaintenanceAdapter(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  public void ensureEquipmentExists(UUID id) {
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM serms.equipment WHERE equipment_id = ?)", Boolean.class, id))) {
      throw missing(id);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockForMaintenance(UUID id) { lockStatus(id); }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void markUnderMaintenance(UUID id) {
    String current = lockStatus(id);
    if (current.equals("RETIRED")) throw new IllegalArgumentException("Equipment is RETIRED: " + id);
    changeStatus(id, current, "UNDER_MAINTENANCE");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recomputeAfterResolvedMaintenance(UUID id) {
    String current = lockStatus(id);
    if (current.equals("RETIRED")) return;
    boolean activeCase = Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM serms.maintenance_case
          WHERE equipment_id = ? AND status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS'))
        """, Boolean.class, id));
    String target;
    if (activeCase) {
      target = "UNDER_MAINTENANCE";
    } else {
      boolean activeLoan = Boolean.TRUE.equals(jdbc.queryForObject("""
          SELECT EXISTS (SELECT 1 FROM serms.loan l
            JOIN serms.reservation r USING (reservation_id)
            WHERE r.equipment_id = ? AND l.status = 'ACTIVE')
          """, Boolean.class, id));
      target = activeLoan ? "ON_LOAN" : "AVAILABLE";
    }
    changeStatus(id, current, target);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void markRetired(UUID id) { changeStatus(id, lockStatus(id), "RETIRED"); }

  private String lockStatus(UUID id) {
    // Selecting status as well acquires the same Equipment row lock as selecting its ID.
    return jdbc.query("SELECT status FROM serms.equipment WHERE equipment_id = ? FOR UPDATE",
        (row, index) -> row.getString("status"), id).stream().findFirst().orElseThrow(() -> missing(id));
  }

  private void changeStatus(UUID id, String current, String target) {
    // Every equipment UPDATE bumps version, so a same-state write must be avoided.
    if (!current.equals(target)) {
      jdbc.update("UPDATE serms.equipment SET status = ? WHERE equipment_id = ?", target, id);
    }
  }

  private static IllegalArgumentException missing(UUID id) {
    return new IllegalArgumentException("Equipment does not exist: " + id);
  }
}
