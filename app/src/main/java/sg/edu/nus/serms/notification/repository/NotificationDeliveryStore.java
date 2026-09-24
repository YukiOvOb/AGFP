package sg.edu.nus.serms.notification.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.notification.domain.Notification;

@Repository
public class NotificationDeliveryStore {
  @PersistenceContext private EntityManager entityManager;

  public void refreshLocked(Notification notification) {
    entityManager.refresh(notification, LockModeType.PESSIMISTIC_WRITE);
  }

  public void recordAttempt(Notification notification, Instant now) {
    entityManager
        .createNativeQuery(
            """
      INSERT INTO notification_attempt (id, notification_id, attempted_at, outcome, reason)
      VALUES (:id,:notification,:at,:outcome,:reason)
      """)
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("notification", notification.getId())
        .setParameter("at", Timestamp.from(now))
        .setParameter("outcome", notification.getStatus().name())
        .setParameter("reason", notification.getReason())
        .executeUpdate();
  }
}
