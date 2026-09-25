package sg.edu.nus.serms.integration.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationType;
import sg.edu.nus.serms.notification.domain.ReminderPolicy;
import sg.edu.nus.serms.notification.service.LoanReminderGuard;

/** Implements the unmodified upstream long-ID contract using loan.reminder_id. */
public class PostgresLoanReminderGuard implements LoanReminderGuard {
    private final JdbcTemplate jdbc;
    private final ReminderPolicy policy;

    public PostgresLoanReminderGuard(JdbcTemplate jdbc, ReminderPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result check(long loanId, String recipient, Instant expectedDueAt, NotificationType type, Instant now) {
        if (loanId <= 0 || recipient == null || expectedDueAt == null || now == null
                || type == null || type == NotificationType.BUSINESS_EVENT) return Result.NOT_APPLICABLE;
        var equipment = jdbc.query("""
            SELECT r.equipment_id FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            WHERE l.reminder_id=?
            """, (r, n) -> r.getObject(1, UUID.class), loanId);
        if (equipment.isEmpty()) return Result.NOT_APPLICABLE;

        // Resolve without locking first; then acquire shared business locks in the agreed order.
        jdbc.queryForObject("SELECT equipment_id FROM serms.equipment WHERE equipment_id=? FOR UPDATE",
            UUID.class, equipment.get(0));
        var loans = jdbc.query("""
            SELECT l.status, l.due_at, u.notification_principal
            FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            JOIN serms.app_user u ON u.user_id=r.requester_id
            WHERE l.reminder_id=? FOR UPDATE OF l
            """, (r, n) -> new Snapshot(r.getString(1), r.getTimestamp(2).toInstant(), r.getString(3)), loanId);
        if (loans.isEmpty()) return Result.NOT_APPLICABLE;
        var loan = loans.get(0);
        if (!Objects.equals(recipient, loan.principal())) return Result.NOT_APPLICABLE;
        if ("RETURNED".equals(loan.status())) return Result.RETURNED;
        if (!"ACTIVE".equals(loan.status())) return Result.NOT_APPLICABLE;
        if (!expectedDueAt.equals(loan.dueAt())) return Result.DUE_DATE_CHANGED;
        return policy.classify(true, loan.dueAt(), now).filter(type::equals).isPresent()
            ? Result.VALID : Result.NOT_APPLICABLE;
    }

    private record Snapshot(String status, Instant dueAt, String principal) {}
}