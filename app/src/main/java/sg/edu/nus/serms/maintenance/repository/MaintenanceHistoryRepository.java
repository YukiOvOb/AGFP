package sg.edu.nus.serms.maintenance.repository;

import java.util.List;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;

/** Logical maintenance activity port; the intended physical source is serms.audit_log. */
public interface MaintenanceHistoryRepository {
  void append(MaintenanceHistoryEntry entry);

  List<MaintenanceHistoryEntry> findByEquipmentId(UUID equipmentId);

  List<MaintenanceHistoryEntry> findByCaseId(UUID maintenanceCaseId);
}
