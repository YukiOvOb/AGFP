package sg.edu.nus.serms.notification.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import sg.edu.nus.serms.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, String> {
  org.springframework.data.domain.Page<Notification> findByRecipientAndStatus(
      String recipient, Notification.Status status, Pageable pageable);

  long countByRecipientAndStatusAndReadAtIsNull(String recipient, Notification.Status status);

  Optional<Notification> findByDedupKey(String key);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select n from Notification n where n.id=:id")
  Optional<Notification> lock(@Param("id") String id);

  @Query(
      "select n.id from Notification n where n.status in (sg.edu.nus.serms.notification.domain.Notification.Status.PENDING, sg.edu.nus.serms.notification.domain.Notification.Status.RETRY) and n.nextAttemptAt<=:now order by n.nextAttemptAt,n.id")
  List<String> pending(@Param("now") Instant now, Pageable page);

  List<Notification> findByRecipientAndStatusOrderByDeliveredAtDesc(
      String recipient, Notification.Status status, Pageable page);
}
