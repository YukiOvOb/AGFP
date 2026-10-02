package sg.edu.nus.serms.maintenance.repository;

import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;

public interface MaintenanceCaseRepository {
  MaintenanceCase insert(MaintenanceCase maintenanceCase);

  /** Update using the snapshot version, returning the database's new version. */
  MaintenanceCase update(MaintenanceCase maintenanceCase);

  Optional<MaintenanceCase> findById(UUID maintenanceCaseId);
}
