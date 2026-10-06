package sg.edu.nus.serms.maintenance.service.jdbc;

import java.util.UUID;
import java.util.List;
import sg.edu.nus.serms.maintenance.service.MaintenanceTechnician;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.maintenance.service.InvalidMaintenanceTechnicianException;
import sg.edu.nus.serms.maintenance.service.TechnicianDirectoryPort;

@Component
public class JdbcTechnicianDirectoryAdapter implements TechnicianDirectoryPort {
  private final JdbcOperations jdbc;

  public JdbcTechnicianDirectoryAdapter(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  public List<MaintenanceTechnician> findActiveTechnicians() {
    return List.copyOf(jdbc.query("""
        SELECT u.user_id, u.email, u.display_name FROM serms.app_user u
          JOIN serms.user_role ur ON ur.user_id = u.user_id
          JOIN serms.role r ON r.role_id = ur.role_id
          WHERE u.account_status = 'ACTIVE' AND r.code = 'MAINTAINER'
          ORDER BY u.display_name ASC, u.user_id ASC
        """, (rs, row) -> new MaintenanceTechnician(
            rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("display_name"))));
  }

  @Override
  public void ensureTechnician(UUID userId) {
    Boolean valid = jdbc.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM serms.app_user u
          JOIN serms.user_role ur ON ur.user_id = u.user_id
          JOIN serms.role r ON r.role_id = ur.role_id
          WHERE u.user_id = ? AND u.account_status = 'ACTIVE' AND r.code = 'MAINTAINER')
        """, Boolean.class, userId);
    if (!Boolean.TRUE.equals(valid)) throw new InvalidMaintenanceTechnicianException(userId);
  }
}
