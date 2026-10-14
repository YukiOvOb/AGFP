package sg.edu.nus.serms.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.integration.notifications.LoanNotificationRequestFactory;
import sg.edu.nus.serms.notification.domain.NotificationType;
import sg.edu.nus.serms.notification.domain.ReminderPolicy;

/** Creates durable requests from persisted ACTIVE loans; the worker delivers them after commit. */
@Component
@Profile("serms-postgres")
@ConditionalOnProperty(name = "serms.notifications.scan-enabled", havingValue = "true", matchIfMissing = true)
public class LoanReminderScanner {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final LoanNotificationRequestFactory factory;
  private final NotificationRequestService requests;
  private final ReminderPolicy policy;
  private final Clock clock;
  private final NotificationText text;

  public LoanReminderScanner(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      LoanNotificationRequestFactory factory,
      NotificationRequestService requests,
      @Value("${serms.notifications.due-soon-lead:PT24H}") java.time.Duration leadTime,
      Clock clock, NotificationText text) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(manager);
    this.factory = factory;
    this.requests = requests;
    this.policy = new ReminderPolicy(leadTime);
    this.clock = clock;
    this.text = text;
  }

  @Scheduled(fixedDelayString = "${serms.notifications.scan-ms:60000}", initialDelayString = "${serms.notifications.scan-initial-delay-ms:60000}")
  public void scheduledScan() {
    scan();
  }

  public void scan() {
    Instant cutoff = clock.instant().plus(policy.leadTime());
    var loanIds =
        jdbc.query(
            """
            SELECT loan_id FROM serms.loan
            WHERE status = 'ACTIVE' AND due_at <= ? ORDER BY due_at, loan_id
            """,
            (rs, row) -> rs.getObject(1, UUID.class),
            Timestamp.from(cutoff));
    for (UUID loanId : loanIds) {
      transactions.executeWithoutResult(status -> scanOne(loanId));
    }
  }

  private void scanOne(UUID loanId) {
    var equipmentIds =
        jdbc.query(
            """
            SELECT r.equipment_id FROM serms.loan l
            JOIN serms.reservation r USING (reservation_id) WHERE l.loan_id = ?
            """,
            (rs, row) -> rs.getObject(1, UUID.class),
            loanId);
    if (equipmentIds.isEmpty()) return;
    // Return and delivery use the same Equipment -> Loan lock order.
    jdbc.queryForObject(
        "SELECT equipment_id FROM serms.equipment WHERE equipment_id = ? FOR UPDATE",
        UUID.class,
        equipmentIds.get(0));
    var snapshots =
        jdbc.query(
            "SELECT status, due_at FROM serms.loan WHERE loan_id = ? FOR UPDATE",
            (rs, row) -> new LoanSnapshot(rs.getString(1), rs.getTimestamp(2).toInstant()),
            loanId);
    if (snapshots.isEmpty() || !"ACTIVE".equals(snapshots.get(0).status())) return;
    var type = policy.classify(true, snapshots.get(0).dueAt(), clock.instant());
    if (type.isEmpty()) return;
    NotificationType reminderType = type.get();
    String equipmentName = text.reservationEquipment(
        jdbc.queryForObject("SELECT reservation_id FROM serms.loan WHERE loan_id=?", UUID.class, loanId));
    var request =
        factory.forLoan(
            loanId,
            reminderType,
            "once",
            reminderType == NotificationType.OVERDUE
                ? "Your loan of " + equipmentName + " was due at " + text.time(snapshots.get(0).dueAt()) + ". Please return it."
                : "Your loan of " + equipmentName + " is due at " + text.time(snapshots.get(0).dueAt()) + ".");
    requests.requestOnce(request);
  }

  private record LoanSnapshot(String status, Instant dueAt) {}
}
