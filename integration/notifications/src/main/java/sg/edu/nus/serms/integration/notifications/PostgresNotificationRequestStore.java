package sg.edu.nus.serms.integration.notifications;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.repository.NotificationRequestStore;

/** PostgreSQL implementation selected as Primary; public requestOnce contract stays unchanged. */
public class PostgresNotificationRequestStore extends NotificationRequestStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresNotificationRequestStore(JdbcTemplate jdbc, Clock clock) {
        super(jdbc, clock);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String requestOnce(NotificationRequest request) {
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
            INSERT INTO serms.notification_request
            (id,dedup_key,recipient,type,loan_id,expected_due_at,content,status,attempts,created_at,next_attempt_at)
            VALUES (?,?,?,?,?,?,?,'PENDING',0,?,?)
            ON CONFLICT (dedup_key) DO NOTHING
            """,
            UUID.randomUUID().toString(), request.dedupKey(), request.recipient(), request.type().name(),
            request.loanId(), request.expectedDueAt() == null ? null : Timestamp.from(request.expectedDueAt()),
            request.content(), now, now);
        return jdbc.queryForObject(
            "SELECT id FROM serms.notification_request WHERE dedup_key=? FOR UPDATE",
            String.class, request.dedupKey());
    }
}