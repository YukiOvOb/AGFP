package sg.edu.nus.serms.integration.notifications;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Producer helper: derives recipient/long-ID/due-time from persisted business facts. */
public class LoanNotificationRequestFactory {
    private final JdbcTemplate jdbc;
    public LoanNotificationRequestFactory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationRequest forLoan(UUID loanId, NotificationType type, String period, String content) {
        if (type == null || type == NotificationType.BUSINESS_EVENT)
            throw new IllegalArgumentException("Expected a reminder type");
        return jdbc.queryForObject("""
            SELECT l.reminder_id, l.due_at, u.notification_principal
            FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
            JOIN serms.app_user u ON u.user_id=r.requester_id WHERE l.loan_id=?
            """, (r, n) -> new NotificationRequest(r.getString(3), type, "loan:" + loanId, period,
                r.getLong(1), r.getTimestamp(2).toInstant(), content), loanId);
    }
}