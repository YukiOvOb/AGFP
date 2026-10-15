package sg.edu.nus.serms.notification.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.service.event.ReservationCancelled;
import sg.edu.nus.serms.reservation.service.event.ReservationSubmitted;

/** Persists reservation notices in the same transaction as the reservation change. */
@Component
@Profile("serms-postgres")
public class ReservationNotificationListener {
  private final JdbcTemplate jdbc;
  private final NotificationRequestService requests;
  private final NotificationText text;

  public ReservationNotificationListener(JdbcTemplate jdbc, NotificationRequestService requests, NotificationText text) {
    this.jdbc = jdbc;
    this.requests = requests;
    this.text = text;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(ReservationSubmitted event) {
    String name = text.equipment(event.equipmentId());
    if (event.status() == ReservationStatus.PENDING_APPROVAL) {
      var approvers = jdbc.query("""
          SELECT u.user_id FROM serms.app_user u
          JOIN serms.user_role ur USING (user_id) JOIN serms.role r USING (role_id)
          WHERE r.code='APPROVER' AND u.account_status='ACTIVE' ORDER BY u.user_id
          """, (rs, row) -> rs.getObject(1, UUID.class));
      for (UUID approver : approvers) {
        save(approver, "reservation-submitted:" + event.reservationId(),
            "Approval requested for " + name + ", " + text.time(event.startAt()) + " to " + text.time(event.endAt()) + ".");
      }
    } else if (event.status() == ReservationStatus.CONFIRMED) {
      save(event.requesterId(), "reservation-submitted:" + event.reservationId(),
          "Your reservation for " + name + " is confirmed. Collect it from " + text.time(event.startAt()) + ".");
    }
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(ReservationCancelled event) {
    String name = text.equipment(event.equipmentId());
    save(event.requesterId(), "reservation-cancelled:" + event.reservationId(),
        "Your reservation for " + name + " was cancelled.");
    var collection = request(event.requesterId(), "reservation-collection:" + event.reservationId(), "");
    jdbc.update("""
        UPDATE serms.notification_request SET status='CANCELLED', next_attempt_at=NULL, reason='RESERVATION_CANCELLED'
        WHERE dedup_key=? AND status IN ('PENDING','RETRY')
        """, collection.dedupKey());
  }

  private void save(UUID recipient, String source, String content) {
    requests.requestOnce(request(recipient, source, content));
  }

  private NotificationRequest request(UUID recipient, String source, String content) {
    return new NotificationRequest(recipient.toString(), NotificationType.BUSINESS_EVENT,
        source, "once", null, null, content.isEmpty() ? "collection cancelled" : content);
  }
}
