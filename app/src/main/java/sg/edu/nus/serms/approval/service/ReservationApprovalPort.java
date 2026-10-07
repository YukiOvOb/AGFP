package sg.edu.nus.serms.approval.service;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Approval's boundary to the reservation module; an adapter will delegate to its public service. */
public interface ReservationApprovalPort {

  Page<ApprovalReservationSnapshot> findPendingApproval(Pageable pageable);

  ApprovalReservationSnapshot get(UUID reservationId);

  ApprovalReservationSnapshot confirm(UUID reservationId, long expectedVersion);

  ApprovalReservationSnapshot reject(UUID reservationId, long expectedVersion);
}
