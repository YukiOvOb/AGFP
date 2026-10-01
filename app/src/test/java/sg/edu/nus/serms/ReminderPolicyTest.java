package sg.edu.nus.serms;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.notification.domain.*;

class ReminderPolicyTest {
  @Test
  void boundariesAreExplicit() {
    var p = new ReminderPolicy(Duration.ofHours(1));
    var due = Instant.parse("2026-09-24T10:00:00Z");
    assertThat(p.classify(true, due, due.minusSeconds(3601))).isEmpty();
    assertThat(p.classify(true, due, due.minusSeconds(3600))).contains(NotificationType.DUE_SOON);
    assertThat(p.classify(true, due, due)).contains(NotificationType.DUE_SOON);
    assertThat(p.classify(true, due, due.plusNanos(1))).contains(NotificationType.OVERDUE);
    assertThat(p.classify(false, due, due.plusSeconds(1))).isEmpty();
  }

  @Test
  void rejectsInvalidInputs() {
    assertThatThrownBy(() -> new ReminderPolicy(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationRequest(
                    "", NotificationType.OVERDUE, "a", "once", 1L, Instant.now(), "text"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new NotificationRequest(
                    "alice", NotificationType.OVERDUE, "a", "once", null, null, "text"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keyIncludesRecipientTypePeriodAndDueDate() {
    var due = Instant.parse("2026-09-24T10:00:00Z");
    var a =
        new NotificationRequest("alice", NotificationType.OVERDUE, "loan:1", "once", 1L, due, "a");
    var b =
        new NotificationRequest("bob", NotificationType.OVERDUE, "loan:1", "once", 1L, due, "a");
    assertThat(a.dedupKey()).hasSize(64).isNotEqualTo(b.dedupKey());
    assertThat(a.dedupKey())
        .isEqualTo(
            new NotificationRequest(
                    "alice", NotificationType.OVERDUE, "loan:1", "once", 1L, due, "new text")
                .dedupKey());
  }
}
