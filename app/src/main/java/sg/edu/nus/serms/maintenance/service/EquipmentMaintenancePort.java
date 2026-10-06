package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public interface EquipmentMaintenancePort {
  void ensureEquipmentExists(UUID equipmentId);

  /** Acquire the first write lock of the maintenance transaction, before any case write. */
  void lockForMaintenance(UUID equipmentId);

  void markUnderMaintenance(UUID equipmentId);

  /** Recompute using RETIRED > active maintenance > ACTIVE Loan > AVAILABLE after case update. */
  void recomputeAfterResolvedMaintenance(UUID equipmentId);

  void markRetired(UUID equipmentId);
}
