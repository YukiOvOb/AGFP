package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.loan.service.LoanEvent;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Saves borrower updates in the same transaction as physical checkout or return. */
@Component
public class LoanNotificationListener {
  private final NotificationRequestService requests;
  private final NotificationText text;
  private final JdbcTemplate jdbc;

  public LoanNotificationListener(NotificationRequestService requests, NotificationText text, JdbcTemplate jdbc) {
    this.requests = requests;
    this.text = text;
    this.jdbc = jdbc;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(LoanEvent event) {
    if ("CHECKED_OUT".equals(event.action())) {
      // The loan event is synchronous inside the Equipment -> Reservation -> Loan transaction.
      // Cancel only requests not yet delivered; a completed collection notice is historical.
      var collection = new NotificationRequest(event.borrowerId().toString(),
          NotificationType.BUSINESS_EVENT, "reservation-collection:" + event.reservationId(),
          "once", null, null, "collection cancelled");
      jdbc.update("""
          UPDATE serms.notification_request SET status='CANCELLED', next_attempt_at=NULL,
            reason='RESERVATION_COLLECTED'
          WHERE dedup_key=? AND status IN ('PENDING','RETRY')
          """, collection.dedupKey());
    }
    String equipmentName = text.equipment(event.equipmentId());
    String content =
        switch (event.action()) {
          case "CHECKED_OUT" ->
              "Your loan of " + equipmentName + " is due at " + text.time(event.dueAt()) + ".";
          case "RETURNED" ->
              "Your loan of "
                  + equipmentName
                  + (event.late() ? " was returned late" : " was returned")
                  + ("DAMAGED".equals(event.returnCondition()) ? " with reported damage." : ".");
          default -> throw new IllegalArgumentException("Unknown loan event: " + event.action());
        };
    requests.requestOnce(
        new NotificationRequest(
            event.borrowerId().toString(),
            NotificationType.BUSINESS_EVENT,
            "loan:" + event.loanId() + ":" + event.action(),
            "once",
            null,
            null,
            content));
  }
}
