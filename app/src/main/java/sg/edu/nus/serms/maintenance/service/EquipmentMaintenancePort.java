package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public interface EquipmentMaintenancePort {
  void ensureEquipmentExists(UUID equipmentId);

  void markUnderMaintenance(UUID equipmentId);

  /** Recompute using RETIRED > active maintenance > ACTIVE Loan > AVAILABLE after case save. */
  void recomputeAfterResolvedMaintenance(UUID equipmentId);

  void markRetired(UUID equipmentId);
}
