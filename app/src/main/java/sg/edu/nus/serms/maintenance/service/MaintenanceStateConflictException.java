package sg.edu.nus.serms.maintenance.service;

/** A known UC04 business-state conflict, distinct from unexpected application failures. */
public class MaintenanceStateConflictException extends IllegalStateException {
  public MaintenanceStateConflictException(String message) { super(message); }
}
