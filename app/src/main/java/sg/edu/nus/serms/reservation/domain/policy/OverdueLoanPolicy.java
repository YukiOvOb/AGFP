package sg.edu.nus.serms.reservation.domain.policy;

import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/**
 * A borrower who still holds equipment past its return time cannot reserve more until it is back
 * (Proposal rule 11). Switched off with {@code serms.reservation.block-overdue-borrowers=false}.
 */
public final class OverdueLoanPolicy implements ReservationPolicy {
  @Override
  public Optional<RuleViolation> evaluate(ReservationRuleContext c) {
    if (!c.hasOverdueLoan()) return Optional.empty();
    return Optional.of(
        new RuleViolation(
            "overdue-loan",
            "You have equipment that is past its return time. Return it before making a new"
                + " reservation."));
  }
}
