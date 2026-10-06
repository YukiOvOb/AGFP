package sg.edu.nus.serms.integration.notifications;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import sg.edu.nus.serms.notification.domain.NotificationType;
import sg.edu.nus.serms.notification.domain.ReminderPolicy;
import sg.edu.nus.serms.notification.repository.NotificationRequestStore;

/** Scans persisted loans and creates deduplicated requests; delivery remains the worker's job. */
public final class PostgresLoanReminderScanner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final LoanNotificationRequestFactory requests;
    private final NotificationRequestStore store;
    private final ReminderPolicy policy;
    private final Clock clock;

    public PostgresLoanReminderScanner(JdbcTemplate jdbc, PlatformTransactionManager manager,
            LoanNotificationRequestFactory requests, NotificationRequestStore store,
            ReminderPolicy policy, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.requests = requests;
        this.store = store;
        this.policy = policy;
        this.clock = clock;
    }

    public int scan() {
        Instant now = clock.instant();
        var ids = jdbc.query("""
            SELECT loan_id FROM serms.loan
            WHERE status = 'ACTIVE' AND due_at <= ? ORDER BY due_at, loan_id
            """, (rs, row) -> rs.getObject(1, UUID.class),
            java.sql.Timestamp.from(now.plus(policy.leadTime())));
        int eligible = 0;
        for (UUID id : ids) {
            if (Boolean.TRUE.equals(transactions.execute(status -> scanOne(id)))) eligible++;
        }
        return eligible;
    }

    @Scheduled(fixedDelayString = "${serms.notifications.scan-ms:60000}")
    public void scheduledScan() { scan(); }

    private boolean scanOne(UUID id) {
        var equipmentIds = jdbc.query("""
            SELECT r.equipment_id FROM serms.loan l
            JOIN serms.reservation r USING (reservation_id) WHERE l.loan_id = ?
            """, (rs, row) -> rs.getObject(1, UUID.class), id);
        if (equipmentIds.isEmpty()) return false;
        // Match the Equipment -> Loan lock order used by checkout, return and delivery.
        jdbc.queryForObject("SELECT equipment_id FROM serms.equipment WHERE equipment_id = ? FOR UPDATE",
            UUID.class, equipmentIds.get(0));
        var snapshots = jdbc.query("""
            SELECT status, due_at FROM serms.loan WHERE loan_id = ? FOR UPDATE
            """, (rs, row) -> new Snapshot(rs.getString(1), rs.getTimestamp(2).toInstant()), id);
        if (snapshots.isEmpty() || !"ACTIVE".equals(snapshots.get(0).status())) return false;
        var type = policy.classify(true, snapshots.get(0).dueAt(), clock.instant());
        if (type.isEmpty()) return false;
        NotificationType reminderType = type.get();
        var request = requests.forLoan(id, reminderType, "once",
            reminderType == NotificationType.OVERDUE
                ? "Your loan is overdue. Please return the equipment."
                : "Your loan is due soon. Please return the equipment on time.");
        // The unique dedup key makes repeated scans safe across workers and restarts.
        store.requestOnce(request);
        return true;
    }

    private record Snapshot(String status, Instant dueAt) {}
}
