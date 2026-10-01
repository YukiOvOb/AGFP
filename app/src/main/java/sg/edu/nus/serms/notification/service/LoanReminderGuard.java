package sg.edu.nus.serms.notification.service;

import java.time.Instant;
import sg.edu.nus.serms.notification.domain.NotificationType;

/**
 * UC-03 adapter must lock Equipment then Loan in the current transaction and recheck active
 * possession, recipient ownership, due date and reminder window. No browser input.
 */
public interface LoanReminderGuard {
  enum Result {
    VALID,
    RETURNED,
    DUE_DATE_CHANGED,
    NOT_APPLICABLE,
    UNAVAILABLE
  }

  Result check(
      long loanId, String recipient, Instant expectedDueAt, NotificationType type, Instant now);
}
