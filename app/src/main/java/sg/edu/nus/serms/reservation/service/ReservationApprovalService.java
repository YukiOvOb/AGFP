package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import sg.edu.nus.serms.reservation.domain.PendingReservation;

/**
 * The reservation module's public service for UC02 (contract auth-and-approval.md §3–4). The
 * approval module calls only this, never the reservation repository.
 */
public interface ReservationApprovalService {

  /** Pending list, read-only and unlocked; includes expired pending reservations. */
  Page<PendingReservation> findPendingApproval(Pageable pageable);

  /** One reservation, read-only; throws {@link ReservationNotFoundException}. */
  PendingReservation get(UUID reservationId);

  /** PENDING_APPROVAL → CONFIRMED. Must run inside the caller's transaction. */
  PendingReservation confirm(UUID reservationId, long expectedVersion);

  /** PENDING_APPROVAL → REJECTED, releasing the slot. Must run inside the caller's transaction. */
  PendingReservation reject(UUID reservationId, long expectedVersion);
}
