package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public interface EquipmentMaintenancePort {
  void ensureEquipmentExists(UUID equipmentId);

  void markUnderMaintenance(UUID equipmentId);

  void markAvailable(UUID equipmentId);

  void markRetired(UUID equipmentId);
}
