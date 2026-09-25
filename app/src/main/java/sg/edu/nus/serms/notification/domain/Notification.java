package sg.edu.nus.serms.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_request")
public class Notification {
  public enum Status {
    PENDING,
    RETRY,
    DELIVERED,
    CANCELLED,
    FAILED
  }

  @Id
  @Column(length = 36)
  private String id;

  @Column(name = "dedup_key", length = 64, nullable = false, unique = true)
  private String dedupKey;

  @Column(nullable = false, length = 100)
  private String recipient;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NotificationType type;

  private Long loanId;
  private Instant expectedDueAt;

  @Column(nullable = false, length = 2000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  private int attempts;
  private Instant createdAt;
  private Instant nextAttemptAt;
  private Instant deliveredAt;
  private Instant readAt;

  @Column(length = 100)
  private String reason;

  protected Notification() {}

  public String getId() {
    return id;
  }

  public String getRecipient() {
    return recipient;
  }

  public NotificationType getType() {
    return type;
  }

  public Long getLoanId() {
    return loanId;
  }

  public Instant getExpectedDueAt() {
    return expectedDueAt;
  }

  public String getContent() {
    return content;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public String getReason() {
    return reason;
  }

  public boolean eligible(Instant now) {
    return (status == Status.PENDING || status == Status.RETRY) && !nextAttemptAt.isAfter(now);
  }

  public void deliver(Instant now) {
    attempts++;
    status = Status.DELIVERED;
    deliveredAt = now;
    nextAttemptAt = null;
    reason = null;
  }

  public void cancel(String why) {
    status = Status.CANCELLED;
    reason = why;
    nextAttemptAt = null;
  }

  public void fail(Instant now, int limit, long delaySeconds, String why) {
    attempts++;
    reason = why;
    status = attempts >= limit ? Status.FAILED : Status.RETRY;
    nextAttemptAt = status == Status.RETRY ? now.plusSeconds(delaySeconds) : null;
  }

  public void markRead(Instant now) {
    if (status == Status.DELIVERED && readAt == null) readAt = now;
  }
}
