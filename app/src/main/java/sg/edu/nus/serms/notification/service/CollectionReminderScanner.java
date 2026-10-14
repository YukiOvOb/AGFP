package sg.edu.nus.serms.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Schedules one in-app collection reminder for a confirmed, uncollected reservation. */
@Component
@Profile("serms-postgres")
@ConditionalOnProperty(name = "serms.notifications.scan-enabled", havingValue = "true", matchIfMissing = true)
public class CollectionReminderScanner {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final NotificationRequestService requests;
  private final NotificationText text;
  private final Clock clock;
  private final Duration lead;

  public CollectionReminderScanner(JdbcTemplate jdbc, PlatformTransactionManager manager,
      NotificationRequestService requests, NotificationText text, Clock clock,
      @Value("${serms.notifications.collection-lead:PT24H}") Duration lead) {
    if (lead.isNegative() || lead.isZero()) throw new IllegalArgumentException("Collection lead must be positive");
    this.jdbc=jdbc; this.transactions=new TransactionTemplate(manager);
    this.requests=requests; this.text=text; this.clock=clock; this.lead=lead;
  }

  @Scheduled(fixedDelayString="${serms.notifications.scan-ms:60000}", initialDelayString="${serms.notifications.scan-initial-delay-ms:60000}")
  public void scheduledScan() { scan(); }

  public void scan() {
    Instant now=clock.instant();
    var ids=jdbc.query("""
        SELECT r.reservation_id FROM serms.reservation r
        WHERE r.status='CONFIRMED' AND r.start_at BETWEEN ? AND ?
          AND NOT EXISTS (SELECT 1 FROM serms.loan l WHERE l.reservation_id=r.reservation_id)
        ORDER BY r.start_at, r.reservation_id
        """, (rs,row)->rs.getObject(1,UUID.class), Timestamp.from(now), Timestamp.from(now.plus(lead)));
    for (UUID id: ids) transactions.executeWithoutResult(status->scanOne(id));
  }

  private void scanOne(UUID id) {
    var rows=jdbc.query("""
        SELECT r.requester_id,r.equipment_id,r.start_at,r.status FROM serms.reservation r
        WHERE r.reservation_id=? FOR UPDATE
        """, (rs,row)->new ReservationSnapshot(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
            rs.getTimestamp(3).toInstant(),rs.getString(4)), id);
    if (rows.isEmpty()) return;
    var r=rows.get(0);
    Instant now=clock.instant();
    if (!"CONFIRMED".equals(r.status()) || r.startAt().isBefore(now) || r.startAt().isAfter(now.plus(lead))) return;
    Integer checkedOut=jdbc.queryForObject("SELECT count(*) FROM serms.loan WHERE reservation_id=?",Integer.class,id);
    if (checkedOut!=null && checkedOut>0) return;
    requests.requestOnce(new NotificationRequest(r.requesterId().toString(),NotificationType.BUSINESS_EVENT,
        "reservation-collection:"+id,"once",null,null,
        "Collect " + text.equipment(r.equipmentId()) + " at " + text.time(r.startAt()) + "."));
  }

  private record ReservationSnapshot(UUID requesterId,UUID equipmentId,Instant startAt,String status) {}
}
