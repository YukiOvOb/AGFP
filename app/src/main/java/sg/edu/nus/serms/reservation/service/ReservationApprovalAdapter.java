package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import sg.edu.nus.serms.approval.application.ApprovalReservationSnapshot;
import sg.edu.nus.serms.approval.application.ReservationApprovalPort;
import sg.edu.nus.serms.reservation.domain.PendingReservation;

/**
 * Adapts {@link ReservationApprovalService} to the port the approval module's ApprovalService
 * (UC02 branch) depends on, so approval never touches the reservation repository.
 */
public class ReservationApprovalAdapter implements ReservationApprovalPort {
  private final ReservationApprovalService reservations;

  public ReservationApprovalAdapter(ReservationApprovalService reservations) {
    this.reservations = reservations;
  }

  @Override
  public ApprovalReservationSnapshot get(UUID reservationId) {
    return snapshot(reservations.get(reservationId));
  }

  @Override
  public ApprovalReservationSnapshot confirm(UUID reservationId, long expectedVersion) {
    return snapshot(reservations.confirm(reservationId, expectedVersion));
  }

  @Override
  public ApprovalReservationSnapshot reject(UUID reservationId, long expectedVersion) {
    return snapshot(reservations.reject(reservationId, expectedVersion));
  }

  private static ApprovalReservationSnapshot snapshot(PendingReservation r) {
    return new ApprovalReservationSnapshot(r.reservationId(), r.requesterId());
  }
}
