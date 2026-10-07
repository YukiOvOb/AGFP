package sg.edu.nus.serms.reservation.domain;

/** Reservation states stored in {@code serms.reservation.status} (V002). */
public enum ReservationStatus {
  PENDING_APPROVAL,
  CONFIRMED,
  REJECTED,
  CANCELLED,
  FULFILLED;

  /** States that count towards a borrower's quota of open reservations. */
  public boolean isOpen() {
    return this == PENDING_APPROVAL || this == CONFIRMED;
  }

  /** A borrower may cancel until the equipment has been checked out (business rule 4). */
  public boolean isCancellable() {
    return isOpen();
  }
}
