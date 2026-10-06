package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public class InvalidMaintenanceLoanException extends IllegalArgumentException {
  private final UUID loanId;
  private final UUID equipmentId;

  public InvalidMaintenanceLoanException(UUID loanId, UUID equipmentId) {
    super("The selected loan is not associated with this equipment.");
    this.loanId = loanId;
    this.equipmentId = equipmentId;
  }

  public UUID getLoanId() { return loanId; }
  public UUID getEquipmentId() { return equipmentId; }
}
