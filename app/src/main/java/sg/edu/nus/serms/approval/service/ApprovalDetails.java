package sg.edu.nus.serms.approval.service;

/** A reservation view and its final decision, or null when no decision has been recorded. */
public record ApprovalDetails(
    ApprovalReservationSnapshot reservation, RecordedApprovalDecision decision) {}
