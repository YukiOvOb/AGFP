package sg.edu.nus.serms.notification.service;

import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.Notification;
import sg.edu.nus.serms.notification.repository.NotificationRepository;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

@Service
public class NotificationInboxService {
  private final NotificationRepository repository;
  private final Clock clock;

  public NotificationInboxService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Notification> inbox(String recipient, int page) {
    return repository.findByRecipientAndStatusOrderByDeliveredAtDesc(
        recipient,
        Notification.Status.DELIVERED,
        PageRequest.of(Math.max(0, Math.min(page, 10000)), 20));
  }

  @org.springframework.security.access.prepost.PreAuthorize(
      "isAuthenticated() and #actorId.toString() == authentication.name")
  @Transactional(readOnly = true)
  public NotificationPage page(java.util.UUID actorId, int page, int size, String sort) {
    if (page < 0 || page > 10000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
    String[] parts = sort.split(",", -1);
    if (parts.length != 2
        || !java.util.Set.of("deliveredAt", "readAt").contains(parts[0])
        || !java.util.Set.of("asc", "desc").contains(parts[1]))
      throw new IllegalArgumentException("Invalid sort");
    var order =
        org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.fromString(parts[1]), parts[0])
            .and(org.springframework.data.domain.Sort.by("id"));
    var result =
        repository.findByRecipientAndStatus(
            actorId.toString(), Notification.Status.DELIVERED, PageRequest.of(page, size, order));
    return new NotificationPage(
        result.getContent().stream().map(NotificationView::from).toList(),
        page,
        size,
        result.getTotalElements(),
        result.getTotalPages());
  }

  @org.springframework.security.access.prepost.PreAuthorize(
      "isAuthenticated() and #actorId.toString() == authentication.name")
  @Transactional(readOnly = true)
  public long unread(java.util.UUID actorId) {
    return repository.countByRecipientAndStatusAndReadAtIsNull(
        actorId.toString(), Notification.Status.DELIVERED);
  }

  @org.springframework.security.access.prepost.PreAuthorize(
      "isAuthenticated() and #actorId.toString() == authentication.name")
  @Transactional
  public void markReadForUser(java.util.UUID id, java.util.UUID actorId) {
    markRead(id.toString(), actorId.toString());
  }

  @Transactional
  public void markRead(String id, String recipient) {
    var n =
        repository
            .lock(id)
            .filter(
                x ->
                    x.getRecipient().equals(recipient)
                        && x.getStatus() == Notification.Status.DELIVERED)
            .orElseThrow(() -> new RecordNotFoundException());
    n.markRead(clock.instant());
  }
}
