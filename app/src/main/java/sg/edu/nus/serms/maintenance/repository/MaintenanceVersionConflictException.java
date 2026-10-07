package sg.edu.nus.serms.maintenance.repository;

import java.util.UUID;

/** The expected maintenance snapshot no longer matches a persisted row. */
public class MaintenanceVersionConflictException extends RuntimeException {
  private final UUID maintenanceCaseId;
  private final long expectedVersion;

  public MaintenanceVersionConflictException(UUID maintenanceCaseId, long expectedVersion) {
    super("Maintenance case " + maintenanceCaseId + " no longer has version " + expectedVersion);
    this.maintenanceCaseId = maintenanceCaseId;
    this.expectedVersion = expectedVersion;
  }

  public UUID getMaintenanceCaseId() { return maintenanceCaseId; }
  public long getExpectedVersion() { return expectedVersion; }
}
