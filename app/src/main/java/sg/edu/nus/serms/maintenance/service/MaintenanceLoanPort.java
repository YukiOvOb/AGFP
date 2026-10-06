package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

/** Validates only the optional V002 maintenance-case Loan association. */
public interface MaintenanceLoanPort {
  /** Caller must already hold the Equipment lock in the same transaction. */
  void ensureLoanMatchesEquipment(UUID loanId, UUID equipmentId);
}
