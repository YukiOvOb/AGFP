package sg.edu.nus.serms.loan.service;

import java.time.Instant;
import java.util.UUID;

/** Published synchronously inside the business transaction; external effects must wait for commit. */
public record LoanEvent(String action, UUID loanId, UUID reservationId, UUID equipmentId,
    UUID borrowerId, UUID actorId, Instant dueAt, Instant occurredAt,
    boolean late, String returnCondition, UUID maintenanceCaseId) {}
