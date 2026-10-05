package sg.edu.nus.serms.notification.repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationRequest;

@Repository
public class NotificationRequestStore {
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public NotificationRequestStore(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Joins the producer transaction: rollback removes both the business change and its request. A
   * unique database key, rather than check-then-insert, serialises competing requests.
   */
  @Transactional
  public String requestOnce(NotificationRequest r) {
    var now = Timestamp.from(clock.instant());
    jdbc.update(
        """
   INSERT INTO notification_request
   (id,dedup_key,recipient,type,loan_id,expected_due_at,content,status,attempts,created_at,next_attempt_at)
   VALUES (?,?,?,?,?,?,?,'PENDING',0,?,?)
   ON DUPLICATE KEY UPDATE dedup_key=VALUES(dedup_key)
   """,
        UUID.randomUUID().toString(),
        r.dedupKey(),
        r.recipient(),
        r.type().name(),
        r.loanId(),
        r.expectedDueAt() == null ? null : Timestamp.from(r.expectedDueAt()),
        r.content(),
        now,
        now);
    return jdbc.queryForObject(
        "SELECT id FROM notification_request WHERE dedup_key=? FOR UPDATE", String.class, r.dedupKey());
  }
}
