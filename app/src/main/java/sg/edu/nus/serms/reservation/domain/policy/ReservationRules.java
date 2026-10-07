package sg.edu.nus.serms.reservation.domain.policy;

import java.util.List;
import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** Runs every policy and reports all violations at once, so the user can fix them together. */
public final class ReservationRules {
  private final List<ReservationPolicy> policies;

  public ReservationRules(List<ReservationPolicy> policies) {
    this.policies = List.copyOf(policies);
  }

  public List<RuleViolation> evaluate(ReservationRuleContext context) {
    return policies.stream().map(p -> p.evaluate(context)).flatMap(Optional::stream).toList();
  }
}
