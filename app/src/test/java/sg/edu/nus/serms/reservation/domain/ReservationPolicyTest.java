package sg.edu.nus.serms.reservation.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.reservation.domain.policy.BookingHorizonPolicy;
import sg.edu.nus.serms.reservation.domain.policy.DurationPolicy;
import sg.edu.nus.serms.reservation.domain.policy.OpenReservationQuotaPolicy;
import sg.edu.nus.serms.reservation.domain.policy.OverdueLoanPolicy;
import sg.edu.nus.serms.reservation.domain.policy.StartInFuturePolicy;
import sg.edu.nus.serms.reservation.service.ReservationConfiguration;
import sg.edu.nus.serms.reservation.service.ReservationLimits;

class ReservationPolicyTest {
  static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");

  static ReservationRuleContext ctx(Instant start, Instant end, int open) {
    return ctx(start, end, open, false);
  }

  static ReservationRuleContext ctx(Instant start, Instant end, int open, boolean overdueLoan) {
    return new ReservationRuleContext(
        new ReservationRequest(UUID.randomUUID(), start, end, null), NOW, open, false, overdueLoan, null);
  }

  static ReservationRuleContext ctx(Duration startIn, Duration length) {
    Instant start = NOW.plus(startIn);
    return ctx(start, start.plus(length), 0);
  }

  @Test
  void startMustBeInTheFutureAndBeforeTheEnd() {
    var policy = new StartInFuturePolicy();
    assertThat(policy.evaluate(ctx(Duration.ofHours(1), Duration.ofHours(2)))).isEmpty();
    assertThat(policy.evaluate(ctx(Duration.ZERO, Duration.ofHours(2)))).isEmpty();
    assertThat(policy.evaluate(ctx(Duration.ofMinutes(-1), Duration.ofHours(2))))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("start-in-past"));
    assertThat(policy.evaluate(ctx(NOW.plusSeconds(60), NOW.plusSeconds(60), 0)))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("invalid-interval"));
    assertThat(policy.evaluate(ctx(NOW.plusSeconds(120), NOW.plusSeconds(60), 0)))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("invalid-interval"));
  }

  @Test
  void anItemsOwnMaximumReplacesTheGlobalMaximum() {
    var policy = new DurationPolicy(Duration.ofMinutes(30), Duration.ofDays(7));
    Instant start = NOW.plus(Duration.ofHours(1));
    java.util.function.BiFunction<Duration, Duration, ReservationRuleContext> withLimit = (length, limit) ->
        new ReservationRuleContext(new ReservationRequest(UUID.randomUUID(), start, start.plus(length), null),
            NOW, 0, false, false, limit);

    assertThat(policy.evaluate(withLimit.apply(Duration.ofHours(2), Duration.ofHours(2)))).isEmpty();
    assertThat(policy.evaluate(withLimit.apply(Duration.ofHours(2).plusMinutes(1), Duration.ofHours(2))))
        .hasValueSatisfying(v -> {
          assertThat(v.code()).isEqualTo("too-long");
          assertThat(v.message()).isEqualTo("This equipment may be reserved for at most 2 hours.");
        });
    // A longer item limit also replaces the global seven days.
    assertThat(policy.evaluate(withLimit.apply(Duration.ofDays(8), Duration.ofDays(10)))).isEmpty();
    // The minimum still applies to items with their own maximum.
    assertThat(policy.evaluate(withLimit.apply(Duration.ofMinutes(10), Duration.ofHours(2))))
        .hasValueSatisfying(v -> assertThat(v.code()).isEqualTo("too-short"));
  }

  @Test
  void durationLimitsAreInclusive() {
    var policy = new DurationPolicy(Duration.ofMinutes(30), Duration.ofDays(7));
    assertThat(policy.evaluate(ctx(Duration.ofHours(1), Duration.ofMinutes(30)))).isEmpty();
    assertThat(policy.evaluate(ctx(Duration.ofHours(1), Duration.ofDays(7)))).isEmpty();
    assertThat(policy.evaluate(ctx(Duration.ofHours(1), Duration.ofMinutes(29))))
        .hasValueSatisfying(
            v -> {
              assertThat(v.code()).isEqualTo("too-short");
              assertThat(v.message()).contains("30 minutes");
            });
    assertThat(policy.evaluate(ctx(Duration.ofHours(1), Duration.ofDays(7).plusSeconds(1))))
        .hasValueSatisfying(
            v -> {
              assertThat(v.code()).isEqualTo("too-long");
              assertThat(v.message()).contains("7 days");
            });
    // Reversed intervals are StartInFuturePolicy's to report, not a duration problem.
    assertThat(policy.evaluate(ctx(NOW.plusSeconds(120), NOW.plusSeconds(60), 0))).isEmpty();
  }

  @Test
  void durationLimitsMustBeConsistent() {
    assertThatThrownBy(() -> new DurationPolicy(Duration.ofHours(2), Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DurationPolicy(Duration.ofHours(-1), Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void humanReadableDurations() {
    var oneDay = new DurationPolicy(Duration.ZERO, Duration.ofDays(1));
    assertThat(oneDay.evaluate(ctx(Duration.ofHours(1), Duration.ofDays(2))))
        .hasValueSatisfying(v -> assertThat(v.message()).contains("1 day."));
    var hours = new DurationPolicy(Duration.ofHours(1), Duration.ofHours(4));
    assertThat(hours.evaluate(ctx(Duration.ofHours(1), Duration.ofMinutes(10))))
        .hasValueSatisfying(v -> assertThat(v.message()).contains("1 hour."));
    assertThat(hours.evaluate(ctx(Duration.ofHours(1), Duration.ofHours(5))))
        .hasValueSatisfying(v -> assertThat(v.message()).contains("4 hours."));
  }

  @Test
  void startMayNotBeBeyondTheBookingHorizon() {
    var policy = new BookingHorizonPolicy(Duration.ofDays(90));
    assertThat(policy.evaluate(ctx(Duration.ofDays(90), Duration.ofHours(1)))).isEmpty();
    assertThat(policy.evaluate(ctx(Duration.ofDays(90).plusSeconds(1), Duration.ofHours(1))))
        .hasValueSatisfying(
            v -> {
              assertThat(v.code()).isEqualTo("too-far-ahead");
              assertThat(v.message()).contains("90 days");
            });
  }

  @Test
  void quotaLimitsOpenReservations() {
    var policy = new OpenReservationQuotaPolicy(3);
    Instant start = NOW.plusSeconds(3600);
    assertThat(policy.evaluate(ctx(start, start.plusSeconds(3600), 2))).isEmpty();
    assertThat(policy.evaluate(ctx(start, start.plusSeconds(3600), 3)))
        .hasValueSatisfying(
            v -> {
              assertThat(v.code()).isEqualTo("quota-exceeded");
              assertThat(v.message()).contains("limit is 3");
            });
    assertThatThrownBy(() -> new OpenReservationQuotaPolicy(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void borrowerWithOverdueEquipmentIsRefused() {
    var policy = new OverdueLoanPolicy();
    Instant start = NOW.plusSeconds(3600);
    assertThat(policy.evaluate(ctx(start, start.plusSeconds(3600), 0, false))).isEmpty();
    assertThat(policy.evaluate(ctx(start, start.plusSeconds(3600), 0, true)))
        .hasValueSatisfying(
            v -> {
              assertThat(v.code()).isEqualTo("overdue-loan");
              assertThat(v.message()).contains("past its return time");
            });
  }

  static ReservationLimits limits(boolean blockOverdueBorrowers) {
    return new ReservationLimits(
        Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(90), 3, blockOverdueBorrowers);
  }

  @Test
  void standardRulesReportEveryViolationInOrder() {
    var rules = ReservationConfiguration.rulesFor(limits(true));
    Instant start = NOW.plus(Duration.ofDays(100));
    assertThat(rules.evaluate(ctx(start, start.plus(Duration.ofDays(8)), 3, true)))
        .extracting(RuleViolation::code)
        .containsExactly("too-long", "too-far-ahead", "quota-exceeded", "overdue-loan");
    assertThat(rules.evaluate(ctx(Duration.ofHours(1), Duration.ofHours(2)))).isEmpty();
  }

  @Test
  void theOverdueRuleCanBeSwitchedOff() {
    var rules = ReservationConfiguration.rulesFor(limits(false));
    Instant start = NOW.plusSeconds(3600);
    assertThat(rules.evaluate(ctx(start, start.plusSeconds(3600), 0, true))).isEmpty();
  }

  @Test
  void requestNormalisesPurposeAndRejectsOverlongText() {
    var id = UUID.randomUUID();
    assertThat(new ReservationRequest(id, NOW, NOW.plusSeconds(1), "  shoot  ").purpose()).isEqualTo("shoot");
    assertThat(new ReservationRequest(id, NOW, NOW.plusSeconds(1), "   ").purpose()).isNull();
    assertThatThrownBy(() -> new ReservationRequest(id, NOW, NOW.plusSeconds(1), "x".repeat(501)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReservationRequest(null, NOW, NOW, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void statusRules() {
    assertThat(ReservationStatus.PENDING_APPROVAL.isCancellable()).isTrue();
    assertThat(ReservationStatus.CONFIRMED.isCancellable()).isTrue();
    assertThat(ReservationStatus.FULFILLED.isCancellable()).isFalse();
    assertThat(ReservationStatus.REJECTED.isOpen()).isFalse();
    assertThat(ReservationStatus.CANCELLED.isOpen()).isFalse();
  }
}
