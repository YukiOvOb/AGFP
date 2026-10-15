package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sg.edu.nus.serms.approval.service.ApprovalConflictException;
import sg.edu.nus.serms.approval.service.ApprovalConflictException.Kind;
import sg.edu.nus.serms.approval.service.ApprovalReservationSnapshot;
import sg.edu.nus.serms.approval.service.ReservationApprovalPort;
import sg.edu.nus.serms.reservation.domain.PendingReservation;

/**
 * Adapts {@link ReservationApprovalService} to the port the approval module depends on, so approval
 * never touches the reservation repository. Reservation conflicts are translated into the approval
 * module's {@link ApprovalConflictException} with the current status and version taken under the
 * row lock; {@link ReservationNotFoundException} passes through and keeps the shared 404.
 */
public class ReservationApprovalAdapter implements ReservationApprovalPort {
  private final ReservationApprovalService reservations;

  public ReservationApprovalAdapter(ReservationApprovalService reservations) {
    this.reservations = reservations;
  }

  @Override
  public Page<ApprovalReservationSnapshot> findPendingApproval(Pageable pageable) {
    return reservations.findPendingApproval(pageable).map(ReservationApprovalAdapter::snapshot);
  }

  @Override
  public ApprovalReservationSnapshot get(UUID reservationId) {
    return snapshot(reservations.get(reservationId));
  }

  @Override
  public ApprovalReservationSnapshot confirm(UUID reservationId, long expectedVersion) {
    return translated(() -> reservations.confirm(reservationId, expectedVersion));
  }

  @Override
  public ApprovalReservationSnapshot reject(UUID reservationId, long expectedVersion) {
    return translated(() -> reservations.reject(reservationId, expectedVersion));
  }

  private static ApprovalReservationSnapshot translated(Supplier<PendingReservation> decision) {
    try {
      return snapshot(decision.get());
    } catch (ReservationStateConflictException e) {
      throw conflict(Kind.STATE, e.currentStatus().name(), e.currentVersion());
    } catch (ReservationVersionConflictException e) {
      throw conflict(Kind.VERSION, e.currentStatus().name(), e.currentVersion());
    } catch (ReservationExpiredException e) {
      throw conflict(Kind.EXPIRED, e.currentStatus().name(), e.currentVersion());
    }
  }

  private static ApprovalConflictException conflict(Kind kind, String status, long version) {
    return new ApprovalConflictException(kind, status, version);
  }

  static ApprovalReservationSnapshot snapshot(PendingReservation r) {
    var e = r.equipment();
    return new ApprovalReservationSnapshot(
        r.reservationId(),
        r.requesterId(),
        r.requesterName(),
        new ApprovalReservationSnapshot.EquipmentSummary(
            e.equipmentId(), e.assetTag(), e.name(), e.category()),
        r.startAt(),
        r.endAt(),
        r.purpose(),
        r.status().name(),
        r.version(),
        r.createdAt(),
        r.expired());
  }
}
