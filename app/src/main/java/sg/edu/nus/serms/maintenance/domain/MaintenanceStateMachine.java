package sg.edu.nus.serms.maintenance.domain;

import java.util.Objects;

public final class MaintenanceStateMachine {
  public MaintenanceStatus transition(MaintenanceStatus current, MaintenanceAction action) {
    Objects.requireNonNull(current, "current status");
    Objects.requireNonNull(action, "maintenance action");

    return switch (current) {
      case OPEN -> require(current, action, MaintenanceAction.ASSIGN, MaintenanceStatus.ASSIGNED);
      case ASSIGNED -> require(current, action, MaintenanceAction.START, MaintenanceStatus.IN_PROGRESS);
      case IN_PROGRESS ->
          switch (action) {
            case RESOLVE -> MaintenanceStatus.RESOLVED;
            case MARK_UNREPAIRABLE -> MaintenanceStatus.UNREPAIRABLE;
            default -> throw new InvalidMaintenanceTransitionException(current, action);
          };
      case RESOLVED, UNREPAIRABLE -> throw new InvalidMaintenanceTransitionException(current, action);
    };
  }

  private MaintenanceStatus require(
      MaintenanceStatus current,
      MaintenanceAction action,
      MaintenanceAction expected,
      MaintenanceStatus next) {
    if (action != expected) {
      throw new InvalidMaintenanceTransitionException(current, action);
    }
    return next;
  }
}
