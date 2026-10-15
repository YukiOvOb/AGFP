package sg.edu.nus.serms.reservation.service;

import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.reservation.domain.PendingReservation;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;

/**
 * Implements contract auth-and-approval.md §4.1. Authorisation, self-approval and the decision
 * record are the approval module's job (§4.2); this class only guards and moves the reservation.
 */
public class DefaultReservationApprovalService implements ReservationApprovalService {
  private final ReservationRepository repository;
  private final Clock clock;

  public DefaultReservationApprovalService(ReservationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<PendingReservation> findPendingApproval(Pageable pageable) {
    var items =
        repository.findPendingApproval(clock.instant(), pageable.getPageSize(), pageable.getOffset());
    return new PageImpl<>(items, pageable, repository.countPendingApproval());
  }

  @Override
  @Transactional(readOnly = true)
  public PendingReservation get(UUID reservationId) {
    return repository
        .findPendingView(reservationId, clock.instant())
        .orElseThrow(ReservationNotFoundException::new);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public PendingReservation confirm(UUID reservationId, long expectedVersion) {
    return decide(reservationId, expectedVersion, ReservationStatus.CONFIRMED);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public PendingReservation reject(UUID reservationId, long expectedVersion) {
    return decide(reservationId, expectedVersion, ReservationStatus.REJECTED);
  }

  private PendingReservation decide(UUID id, long expectedVersion, ReservationStatus target) {
    var now = clock.instant();
    // Lock order: equipment, then reservation — the same order as checkout and return.
    UUID equipmentId = repository.equipmentIdOf(id).orElseThrow(ReservationNotFoundException::new);
    var equipment = repository.lockEquipment(equipmentId).orElseThrow(ReservationNotFoundException::new);
    var current = repository.findByIdForUpdate(id).orElseThrow(ReservationNotFoundException::new);
    if (current.version() != expectedVersion) {
      throw new ReservationVersionConflictException(id, current.status(), current.version());
    }
    if (current.status() != ReservationStatus.PENDING_APPROVAL) {
      throw new ReservationStateConflictException(
          id, current.status(), current.version(), "This reservation is no longer waiting for approval.");
    }
    if (target == ReservationStatus.CONFIRMED) {
      // The trigger only blocks confirming once the END has passed; the START is checked here.
      if (!now.isBefore(current.startAt())) {
        throw new ReservationExpiredException(id, current.status(), current.version());
      }
      boolean usable = "AVAILABLE".equals(equipment.status()) || "ON_LOAN".equals(equipment.status());
      if (!usable || repository.hasOpenMaintenance(equipmentId)) {
        throw new ReservationStateConflictException(
            id,
            current.status(),
            current.version(),
            "The equipment became unavailable (fault, repair or retirement) while this request waited.");
      }
    }
    boolean changed;
    try {
      changed = repository.changeStatus(id, ReservationStatus.PENDING_APPROVAL, target, expectedVersion);
    } catch (DataAccessException e) {
      if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
          && ReservationService.CHECK_VIOLATION.equals(sql.getSQLState())) {
        throw new ReservationStateConflictException(
            id, current.status(), current.version(), "The equipment can no longer be reserved for this time.");
      }
      throw e;
    }
    if (!changed) {
      throw new ReservationVersionConflictException(id, current.status(), current.version());
    }
    return repository.findPendingView(id, now).orElseThrow(ReservationNotFoundException::new);
  }
}
