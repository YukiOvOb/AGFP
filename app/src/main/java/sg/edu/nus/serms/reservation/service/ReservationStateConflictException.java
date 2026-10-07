package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

/**
 * The reservation is no longer pending, or its equipment became unavailable while it waited (409).
 * Carries the current status and version so the approver's page can refresh.
 */
public class ReservationStateConflictException extends RuntimeException {
  private final UUID reservationId;
  private final ReservationStatus currentStatus;
  private final long currentVersion;

  public ReservationStateConflictException(
      UUID reservationId, ReservationStatus currentStatus, long currentVersion, String message) {
    super(message);
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
