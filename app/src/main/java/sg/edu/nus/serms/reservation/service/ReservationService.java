package sg.edu.nus.serms.reservation.service;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.reservation.domain.Reservation;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.domain.RuleViolation;
import sg.edu.nus.serms.reservation.domain.policy.ReservationRules;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;
import sg.edu.nus.serms.reservation.service.event.ReservationCancelled;
import sg.edu.nus.serms.reservation.service.event.ReservationSubmitted;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

/**
 * UC01 write side: submit and cancel reservations. "Check, then insert" runs in one transaction
 * with the equipment row locked; the PostgreSQL exclusion constraint is the final guard, so even a
 * request that slips past the checks cannot double-book a slot.
 */
public class ReservationService {

  /** Exclusion constraint violation, e.g. {@code reservation_no_overlap}. */
  static final String EXCLUSION_VIOLATION = "23P01";

  /** Check violation; the reservation trigger raises it when equipment became unavailable. */
  static final String CHECK_VIOLATION = "23514";

  public record ReservationPage(List<Reservation> items, long totalElements, int page, int size) {}

  private final ReservationRepository repository;
  private final ReservationRules rules;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ReservationService(
      ReservationRepository repository,
      ReservationRules rules,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.rules = Objects.requireNonNull(rules);
    this.events = Objects.requireNonNull(events);
    this.clock = Objects.requireNonNull(clock);
  }

  @PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")
  @Transactional
  public Reservation submit(UUID requesterId, ReservationRequest request) {
    var now = clock.instant();
    var equipment =
        repository.lockEquipment(request.equipmentId()).orElseThrow(RecordNotFoundException::new);
    switch (equipment.status()) {
      case "RETIRED" -> throw new RecordNotFoundException();
      case "UNDER_MAINTENANCE" ->
          throw new ReservationException.EquipmentUnavailable(
              "This equipment is under maintenance and cannot be reserved.");
      default -> {}
    }
    if (repository.hasOpenMaintenance(request.equipmentId())) {
      throw new ReservationException.EquipmentUnavailable(
          "A fault has been reported for this equipment; it cannot be reserved until repaired.");
    }
    String account =
        repository
            .lockRequesterAccountStatus(requesterId)
            .orElseThrow(ReservationException.RequesterNotEligible::new);
    if (!"ACTIVE".equals(account)) throw new ReservationException.RequesterNotEligible();

    List<RuleViolation> violations =
        rules.evaluate(
            new ReservationRuleContext(
                request,
                now,
                repository.countOpen(requesterId, now),
                equipment.requiresApproval(),
                repository.hasOverdueLoan(requesterId, now)));
    if (!violations.isEmpty()) throw new ReservationException.RulesBroken(violations);

    if (repository.overlapsHeldSlot(request.equipmentId(), request.startAt(), request.endAt())) {
      throw new ReservationException.SlotTaken();
    }
    if (!repository.equipmentCanBeReserved(request.equipmentId(), request.startAt())) {
      throw new ReservationException.EquipmentUnavailable(
          "This equipment is still on loan at the requested start time. Choose a later start.");
    }

    var status =
        equipment.requiresApproval() ? ReservationStatus.PENDING_APPROVAL : ReservationStatus.CONFIRMED;
    var id = UUID.randomUUID();
    try {
      repository.insert(
          id,
          requesterId,
          request.equipmentId(),
          request.startAt(),
          request.endAt(),
          status,
          request.purpose());
    } catch (DataAccessException e) {
      throw translate(e);
    }
    repository.audit(
        requesterId,
        "RESERVATION_SUBMIT",
        id,
        "status=" + status + "; equipment=" + request.equipmentId() + "; slot=" + request.startAt()
            + "/" + request.endAt(),
        now);
    events.publishEvent(
        new ReservationSubmitted(
            UUID.randomUUID(),
            id,
            requesterId,
            request.equipmentId(),
            request.startAt(),
            request.endAt(),
            status));
    return repository.findById(id).orElseThrow();
  }

  @PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")
  @Transactional(readOnly = true)
  public ReservationPage mine(UUID requesterId, ReservationStatus status, int page, int size) {
    if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("Invalid page");
    var result = repository.findByRequester(requesterId, status, page, size);
    return new ReservationPage(result.items(), result.totalElements(), page, size);
  }

  @PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")
  @Transactional(readOnly = true)
  public Reservation get(UUID requesterId, UUID reservationId) {
    return repository
        .findById(reservationId)
        .filter(r -> r.requesterId().equals(requesterId))
        .orElseThrow(RecordNotFoundException::new);
  }

  @PreAuthorize("hasRole('BORROWER') and #requesterId.toString() == authentication.name")
  @Transactional
  public Reservation cancel(UUID requesterId, UUID reservationId) {
    // Someone else's reservation looks exactly like a missing one.
    var current =
        repository
            .findByIdForUpdate(reservationId)
            .filter(r -> r.requesterId().equals(requesterId))
            .orElseThrow(RecordNotFoundException::new);
    if (current.checkedOut() || current.status() == ReservationStatus.FULFILLED) {
      throw new ReservationException.NotCancellable(
          "The equipment has already been checked out. Return it instead of cancelling.");
    }
    if (!current.status().isCancellable()) {
      throw new ReservationException.NotCancellable(
          "Only pending or confirmed reservations can be cancelled.");
    }
    if (!repository.changeStatus(
        reservationId, current.status(), ReservationStatus.CANCELLED, current.version())) {
      throw new ReservationException.StaleVersion();
    }
    repository.audit(
        requesterId,
        "RESERVATION_CANCEL",
        reservationId,
        "from=" + current.status() + "; equipment=" + current.equipmentId(),
        clock.instant());
    events.publishEvent(
        new ReservationCancelled(
            UUID.randomUUID(), reservationId, requesterId, current.equipmentId()));
    return repository.findById(reservationId).orElseThrow();
  }

  static RuntimeException translate(DataAccessException e) {
    Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
    if (cause instanceof SQLException sql) {
      if (EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
        return new ReservationException.SlotTaken();
      }
      if (CHECK_VIOLATION.equals(sql.getSQLState())) {
        return new ReservationException.EquipmentUnavailable(
            "This equipment can no longer be reserved for that time. Refresh and try again.");
      }
    }
    return e;
  }
}
