package sg.edu.nus.serms.approval.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.approval.domain.ApprovalCommand;
import sg.edu.nus.serms.approval.domain.ApprovalContext;
import sg.edu.nus.serms.approval.domain.ApprovalEvaluation;
import sg.edu.nus.serms.approval.domain.ApprovalPolicyChain;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;

/** Coordinates policy evaluation, the reservation transition, decision persistence, and eventing. */
public class ApprovalService {

  private final ReservationApprovalPort reservations;
  private final ApprovalDecisionPort decisions;
  private final ApprovalPolicyChain policyChain;
  private final ApplicationEventPublisher events;

  public ApprovalService(
      ReservationApprovalPort reservations,
      ApprovalDecisionPort decisions,
      ApprovalPolicyChain policyChain,
      ApplicationEventPublisher events) {
    this.reservations = Objects.requireNonNull(reservations, "reservations must not be null");
    this.decisions = Objects.requireNonNull(decisions, "decisions must not be null");
    this.policyChain = Objects.requireNonNull(policyChain, "policyChain must not be null");
    this.events = Objects.requireNonNull(events, "events must not be null");
  }

  @Transactional
  public ApprovalOutcome decide(UUID actorId, ApprovalCommand command) {
    Objects.requireNonNull(actorId, "actorId must not be null");
    Objects.requireNonNull(command, "command must not be null");

    ApprovalReservationSnapshot current = reservations.get(command.reservationId());
    ApprovalEvaluation evaluation =
        policyChain.evaluate(new ApprovalContext(command, current.requesterId(), actorId));
    if (!evaluation.isAllowed()) {
      throw new ApprovalPolicyViolationException(
          evaluation
              .failureReason()
              .orElseThrow(() -> new IllegalStateException("Rejected evaluation must have a reason")));
    }

    ApprovalReservationSnapshot updated =
        switch (command.decision()) {
          case APPROVED -> reservations.confirm(command.reservationId(), command.expectedVersion());
          case REJECTED -> reservations.reject(command.reservationId(), command.expectedVersion());
        };

    UUID decisionId =
        decisions.record(command.reservationId(), actorId, command.decision(), command.comment());
    events.publishEvent(
        new ReservationDecided(
            UUID.randomUUID(),
            decisionId,
            command.reservationId(),
            current.requesterId(),
            actorId,
            command.decision(),
            command.comment()));
    return new ApprovalOutcome(updated, decisionId);
  }
}
