package sg.edu.nus.serms.notification.service;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;
import sg.edu.nus.serms.notification.domain.Notification;
import sg.edu.nus.serms.notification.repository.NotificationRepository;

/** Temporary compatibility tests on the existing test profile, not PostgreSQL acceptance. */
@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:approval_notification_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ApprovalNotificationTransactionTest {
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ApprovalNotificationFactory factory;
  @Autowired NotificationRepository repository;
  @Autowired NotificationInboxService inbox;
  @Autowired NotificationDeliveryService delivery;

  private ReservationDecided event(ApprovalDecisionType decision, String comment) {
    return new ReservationDecided(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        decision,
        comment);
  }

  private String key(ReservationDecided e) {
    return factory.from(e).dedupKey();
  }

  private Notification saved(ReservationDecided e) {
    return repository.findByDedupKey(key(e)).orElseThrow();
  }

  private void publish(ReservationDecided e) {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> events.publishEvent(e));
  }

  @Test
  void approvedAndRejectedEventsPersistPendingRequests() {
    for (var decision : ApprovalDecisionType.values()) {
      var e =
          event(
              decision, decision == ApprovalDecisionType.REJECTED ? "Calibration required" : null);
      publish(e);
      var n = saved(e);
      assertThat(n.getRecipient()).isEqualTo(e.requesterId().toString());
      assertThat(n.getStatus()).isEqualTo(Notification.Status.PENDING);
      assertThat(n.getAttempts()).isZero();
      assertThat(inbox.inbox(e.requesterId().toString(), 0)).isEmpty();
    }
  }

  @Test
  void synchronousRequestIsVisibleBeforeProducerCommitsAndRollsBackWithIt() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              events.publishEvent(e);
              assertThat(repository.findByDedupKey(key(e))).isPresent();
              tx.setRollbackOnly();
            });
    assertThat(repository.findByDedupKey(key(e))).isEmpty();
  }

  @Test
  void failureAfterPublishingRemovesRequest() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          events.publishEvent(e);
                          throw new IllegalStateException("simulated producer or audit failure");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(repository.findByDedupKey(key(e))).isEmpty();
  }

  @Test
  void invalidNotificationMarksProducerTransactionRollbackOnlyEvenIfCaught() {
    var valid = event(ApprovalDecisionType.APPROVED, null);
    var invalid = event(ApprovalDecisionType.REJECTED, " ");
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          events.publishEvent(valid);
                          try {
                            events.publishEvent(invalid);
                          } catch (IllegalArgumentException ignored) {
                            /* transaction must still fail */
                          }
                        }))
        .isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
    assertThat(repository.findByDedupKey(key(valid))).isEmpty();
  }

  @Test
  void eventWithoutBusinessTransactionFails() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    assertThatThrownBy(() -> events.publishEvent(e))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(repository.findByDedupKey(key(e))).isEmpty();
  }

  @Test
  void identicalAndRegeneratedEventsKeepOneOriginalRequest() {
    var e = event(ApprovalDecisionType.REJECTED, "Original reason");
    publish(e);
    String id = saved(e).getId();
    publish(e);
    var replay =
        new ReservationDecided(
            UUID.randomUUID(),
            UUID.randomUUID(),
            e.reservationId(),
            e.requesterId(),
            e.approverId(),
            e.decision(),
            "Updated replay text");
    publish(replay);
    assertThat(saved(replay).getId()).isEqualTo(id);
    assertThat(saved(replay).getContent())
        .contains("Original reason")
        .doesNotContain("Updated replay text");
  }

  @Test
  void concurrentEventsForOneDecisionKeepOneRequest() throws Exception {
    var e = event(ApprovalDecisionType.APPROVED, null);
    var executor = Executors.newFixedThreadPool(4);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        tasks.add(
            () -> {
              publish(
                  new ReservationDecided(
                      UUID.randomUUID(),
                      e.approvalDecisionId(),
                      e.reservationId(),
                      e.requesterId(),
                      e.approverId(),
                      e.decision(),
                      null));
              return saved(e).getId();
            });
      Set<String> ids = new HashSet<>();
      for (var f : executor.invokeAll(tasks)) ids.add(f.get(15, TimeUnit.SECONDS));
      assertThat(ids).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void workerDeliveryAndReadAreScopedToRequester() {
    var e = event(ApprovalDecisionType.REJECTED, "Maintenance");
    publish(e);
    var n = saved(e);
    delivery.deliver(n.getId());
    delivery.deliver(n.getId());
    assertThat(inbox.inbox(e.requesterId().toString(), 0))
        .extracting(Notification::getId)
        .containsExactly(n.getId());
    assertThat(inbox.inbox(e.approverId().toString(), 0)).isEmpty();
    assertThat(saved(e).getAttempts()).isEqualTo(1);
    inbox.markRead(n.getId(), e.requesterId().toString());
    assertThat(saved(e).getReadAt()).isNotNull();
  }
}
