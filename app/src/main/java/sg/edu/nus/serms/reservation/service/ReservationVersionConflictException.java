package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

/**
 * The caller decided on an outdated version of the reservation (409). Carries the current status
 * and version so the approver's page can refresh.
 */
public class ReservationVersionConflictException extends RuntimeException {
  private final UUID reservationId;
  private final ReservationStatus currentStatus;
  private final long currentVersion;

  public ReservationVersionConflictException(
      UUID reservationId, ReservationStatus currentStatus, long currentVersion) {
    super("The reservation was changed by someone else. Refresh and try again.");
    this.reservationId = reservationId;
    this.currentStatus = currentStatus;
    this.currentVersion = currentVersion;
  }

  public UUID reservationId() {
    return reservationId;
  }

  public ReservationStatus currentStatus() {
    return currentStatus;
  }

  public long currentVersion() {
    return currentVersion;
  }
}
