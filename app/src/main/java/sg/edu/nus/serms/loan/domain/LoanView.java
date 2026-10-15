package sg.edu.nus.serms.loan.domain;

import java.time.Instant;
import java.util.UUID;

public record LoanView(UUID loanId, UUID reservationId, UUID equipmentId, String equipmentName, String assetTag,
    UUID borrowerId, String borrowerName, UUID checkoutBy, UUID returnBy,
    Instant checkedOutAt, Instant dueAt, Instant returnedAt, String status,
    String returnCondition, String returnNote, long version, boolean overdue) {}
