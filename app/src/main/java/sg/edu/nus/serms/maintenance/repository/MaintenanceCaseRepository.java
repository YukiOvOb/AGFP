package sg.edu.nus.serms.maintenance.repository;

import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;

public interface MaintenanceCaseRepository {
  void save(MaintenanceCase maintenanceCase);

  Optional<MaintenanceCase> findById(UUID maintenanceCaseId);
}
