package sg.edu.nus.serms.maintenance.repository;

import java.util.List;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;

public interface MaintenanceHistoryRepository {
  void append(MaintenanceHistoryEntry entry);

  List<MaintenanceHistoryEntry> findByEquipmentId(UUID equipmentId);

  List<MaintenanceHistoryEntry> findByCaseId(UUID maintenanceCaseId);
}
