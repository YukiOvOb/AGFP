package sg.edu.nus.serms.notification.service;

import java.util.Objects;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Maps the public approval contract without accessing approval or reservation persistence. */
@Component
public class ApprovalNotificationFactory {
  public NotificationRequest from(ReservationDecided event) {
    Objects.requireNonNull(event, "event");
    if (event.comment() != null && event.comment().length() > 1000) {
      throw new IllegalArgumentException("Approval comment exceeds the agreed 1000 characters");
    }
    String content =
        switch (event.decision()) {
          case APPROVED -> "Your reservation " + event.reservationId() + " has been confirmed.";
          case REJECTED -> {
            if (event.comment() == null || event.comment().isBlank()) {
              throw new IllegalArgumentException("A rejected reservation requires a reason");
            }
            yield "Your reservation "
                + event.reservationId()
                + " was rejected. Reason: "
                + event.comment();
          }
        };
    // One final decision per reservation. eventId, decisionId and message text may differ on
    // redelivery but must not change notification identity. Corrections need a separate event type.
    return new NotificationRequest(
        event.requesterId().toString(),
        NotificationType.BUSINESS_EVENT,
        "reservation-decided:" + event.reservationId(),
        "once",
        null,
        null,
        content);
  }
}
