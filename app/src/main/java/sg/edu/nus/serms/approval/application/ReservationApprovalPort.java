package sg.edu.nus.serms.approval.application;

import java.util.UUID;

/** Approval's boundary to the reservation module; an adapter will delegate to its public service. */
public interface ReservationApprovalPort {

  ApprovalReservationSnapshot get(UUID reservationId);

  ApprovalReservationSnapshot confirm(UUID reservationId, long expectedVersion);

  ApprovalReservationSnapshot reject(UUID reservationId, long expectedVersion);
}
