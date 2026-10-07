package sg.edu.nus.serms.reservation.domain.policy;

import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/**
 * Strategy for one booking rule (Proposal rules 10 and 11). Each rule is a separate class so rules
 * can be added, removed or combined per category without touching the reservation service.
 */
public interface ReservationPolicy {
  Optional<RuleViolation> evaluate(ReservationRuleContext context);
}
