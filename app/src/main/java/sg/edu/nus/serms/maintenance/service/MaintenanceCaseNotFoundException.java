package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public final class MaintenanceCaseNotFoundException extends RuntimeException {
  private final UUID maintenanceCaseId;

  public MaintenanceCaseNotFoundException(UUID maintenanceCaseId) {
    super("Maintenance case not found: " + maintenanceCaseId);
    this.maintenanceCaseId = maintenanceCaseId;
  }

  public UUID getMaintenanceCaseId() {
    return maintenanceCaseId;
  }
}
