package sg.edu.nus.serms.reservation.domain.policy;

import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** Reservations are for the future: the start may not lie before "now", and end after start. */
public final class StartInFuturePolicy implements ReservationPolicy {
  @Override
  public Optional<RuleViolation> evaluate(ReservationRuleContext c) {
    if (!c.request().startAt().isBefore(c.request().endAt())) {
      return Optional.of(new RuleViolation("invalid-interval", "The end time must be after the start time."));
    }
    if (c.request().startAt().isBefore(c.now())) {
      return Optional.of(new RuleViolation("start-in-past", "The start time has already passed."));
    }
    return Optional.empty();
  }
}
