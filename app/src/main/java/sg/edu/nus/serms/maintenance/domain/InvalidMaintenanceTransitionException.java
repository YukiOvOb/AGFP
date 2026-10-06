package sg.edu.nus.serms.maintenance.domain;

public final class InvalidMaintenanceTransitionException extends RuntimeException {
  private final MaintenanceStatus currentStatus;
  private final MaintenanceAction attemptedAction;

  public InvalidMaintenanceTransitionException(
      MaintenanceStatus currentStatus, MaintenanceAction attemptedAction) {
    super("Cannot perform " + attemptedAction + " while maintenance is " + currentStatus);
    this.currentStatus = currentStatus;
    this.attemptedAction = attemptedAction;
  }

  public MaintenanceStatus getCurrentStatus() {
    return currentStatus;
  }

  public MaintenanceAction getAttemptedAction() {
    return attemptedAction;
  }
}
