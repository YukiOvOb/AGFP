package sg.edu.nus.serms.maintenance.service.jdbc;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.service.InvalidMaintenanceLoanException;
import sg.edu.nus.serms.maintenance.service.MaintenanceLoanPort;

@Component
public class JdbcMaintenanceLoanAdapter implements MaintenanceLoanPort {
  private final JdbcOperations jdbc;

  public JdbcMaintenanceLoanAdapter(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void ensureLoanMatchesEquipment(UUID loanId, UUID equipmentId) {
    Objects.requireNonNull(loanId, "loanId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    // Equipment is locked first by the service. V002 makes both relationships immutable;
    // retaining this Loan share lock prevents deletion before the case's FK is inserted.
    var equipmentIds = jdbc.query("""
        SELECT r.equipment_id FROM serms.loan l
          JOIN serms.reservation r ON r.reservation_id = l.reservation_id
          WHERE l.loan_id = ? FOR SHARE OF l
        """, (rs, row) -> rs.getObject("equipment_id", UUID.class), loanId);
    if (equipmentIds.isEmpty() || !equipmentId.equals(equipmentIds.get(0))) {
      throw new InvalidMaintenanceLoanException(loanId, equipmentId);
    }
  }
}
