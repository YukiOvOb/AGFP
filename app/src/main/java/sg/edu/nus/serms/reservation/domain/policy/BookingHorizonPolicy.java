package sg.edu.nus.serms.reservation.domain.policy;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** How far ahead a reservation may start, so equipment is not blocked indefinitely. */
public final class BookingHorizonPolicy implements ReservationPolicy {
  private final Duration horizon;

  public BookingHorizonPolicy(Duration horizon) {
    this.horizon = Objects.requireNonNull(horizon);
  }

  @Override
  public Optional<RuleViolation> evaluate(ReservationRuleContext c) {
    if (c.request().startAt().isAfter(c.now().plus(horizon))) {
      return Optional.of(
          new RuleViolation(
              "too-far-ahead",
              "Reservations can start at most " + DurationPolicy.human(horizon) + " from now."));
    }
    return Optional.empty();
  }
}
