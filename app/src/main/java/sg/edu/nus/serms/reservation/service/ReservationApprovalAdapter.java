package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.approval.application.ApprovalReservationSnapshot;
import sg.edu.nus.serms.approval.application.ReservationApprovalPort;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

/**
 * The reservation side of the approval port (UC02). Authorisation stays in ApprovalService; this
 * adapter only performs the version-checked PENDING_APPROVAL → CONFIRMED / REJECTED transition.
 */
public class ReservationApprovalAdapter implements ReservationApprovalPort {
  private final ReservationRepository repository;

  public ReservationApprovalAdapter(ReservationRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public ApprovalReservationSnapshot get(UUID reservationId) {
    var r = repository.findById(reservationId).orElseThrow(RecordNotFoundException::new);
    return new ApprovalReservationSnapshot(r.reservationId(), r.requesterId());
  }

  @Override
  @Transactional
  public ApprovalReservationSnapshot confirm(UUID reservationId, long expectedVersion) {
    return move(reservationId, ReservationStatus.CONFIRMED, expectedVersion);
  }

  @Override
  @Transactional
  public ApprovalReservationSnapshot reject(UUID reservationId, long expectedVersion) {
    return move(reservationId, ReservationStatus.REJECTED, expectedVersion);
  }

  private ApprovalReservationSnapshot move(UUID id, ReservationStatus to, long version) {
    var current = repository.findByIdForUpdate(id).orElseThrow(RecordNotFoundException::new);
    boolean changed;
    try {
      changed =
          repository.changeStatus(id, ReservationStatus.PENDING_APPROVAL, to, version);
    } catch (org.springframework.dao.DataAccessException e) {
      throw ReservationService.translate(e);
    }
    if (!changed) throw new ReservationException.StaleVersion();
    return new ApprovalReservationSnapshot(id, current.requesterId());
  }
}
