package sg.edu.nus.serms.reservation.service;

import java.util.List;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** Business refusals of UC01. Messages are written for the borrower and are safe to show. */
public abstract sealed class ReservationException extends RuntimeException {
  private final String type;

  ReservationException(String type, String message) {
    super(message);
    this.type = type;
  }

  /** Stable problem type, sent as {@code /problems/<type>}. */
  public String type() {
    return type;
  }

  /** Another reservation already holds part of the requested slot (rules 1 and 12). */
  public static final class SlotTaken extends ReservationException {
    public SlotTaken() {
      super(
          "reservation-conflict",
          "This equipment is already reserved for part of that time. Choose another time slot.");
    }
  }

  /** The equipment is under maintenance, retired or still on loan at the requested start. */
  public static final class EquipmentUnavailable extends ReservationException {
    public EquipmentUnavailable(String message) {
      super("equipment-unavailable", message);
    }
  }

  /** One or more booking rules were broken. */
  public static final class RulesBroken extends ReservationException {
    private final List<RuleViolation> violations;

    public RulesBroken(List<RuleViolation> violations) {
      super("reservation-rule", violations.get(0).message());
      this.violations = List.copyOf(violations);
    }

    public List<RuleViolation> violations() {
      return violations;
    }
  }

  /** The reservation is past the point where the borrower may cancel it (rule 4). */
  public static final class NotCancellable extends ReservationException {
    public NotCancellable(String message) {
      super("not-cancellable", message);
    }
  }

  /** The reservation changed since the caller read it (optimistic version check). */
  public static final class StaleVersion extends ReservationException {
    public StaleVersion() {
      super("stale-reservation", "This reservation was changed by someone else. Refresh and try again.");
    }
  }

  /** The account may not make reservations (disabled). */
  public static final class RequesterNotEligible extends ReservationException {
    public RequesterNotEligible() {
      super("not-eligible", "Your account cannot make reservations. Contact an administrator.");
    }
  }
}
