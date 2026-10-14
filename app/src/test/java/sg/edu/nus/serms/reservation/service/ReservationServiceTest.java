package sg.edu.nus.serms.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import sg.edu.nus.serms.reservation.domain.Reservation;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;
import sg.edu.nus.serms.reservation.repository.ReservationRepository.LockedEquipment;
import sg.edu.nus.serms.reservation.service.event.ReservationSubmitted;

/** Decision logic of the service with the database replaced; PostgreSQL behaviour is in the IT. */
class ReservationServiceTest {
  static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");
  final ReservationRepository repository = mock(ReservationRepository.class);
  final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  final UUID me = UUID.randomUUID();
  final UUID item = UUID.randomUUID();
  ReservationService service;

  @BeforeEach
  void setUp() {
    service =
        new ReservationService(
            repository,
            ReservationConfiguration.rulesFor(
                new ReservationLimits(Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(90), 3, true)),
            events,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(repository.lockEquipment(item)).thenReturn(Optional.of(new LockedEquipment(item, "AVAILABLE", false)));
    when(repository.lockRequesterAccountStatus(me)).thenReturn(Optional.of("ACTIVE"));
    when(repository.equipmentCanBeReserved(eq(item), any())).thenReturn(true);
  }

  ReservationRequest request() {
    return new ReservationRequest(item, NOW.plusSeconds(3600), NOW.plusSeconds(7200), null);
  }

  Reservation stored(UUID id, ReservationStatus status, boolean checkedOut) {
    return new Reservation(id, me, item, "CAM-1", "Camera", "Lab", NOW, NOW.plusSeconds(60), status,
        null, NOW, 0, checkedOut);
  }

  @Test
  void translatesTheExclusionConstraintIntoASlotConflict() {
    doThrow(new DataIntegrityViolationException("overlap", new SQLException("x", "23P01")))
        .when(repository).insert(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.submit(me, request())).isInstanceOf(ReservationException.SlotTaken.class);
    verifyNoInteractions(events);
  }

  @Test
  void translatesTheTriggerRefusalIntoUnavailable() {
    doThrow(new DataIntegrityViolationException("check", new SQLException("x", "23514")))
        .when(repository).insert(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.submit(me, request()))
        .isInstanceOf(ReservationException.EquipmentUnavailable.class);
  }

  @Test
  void otherDatabaseErrorsPassThrough() {
    var failure = new QueryTimeoutException("slow");
    doThrow(failure).when(repository).insert(any(), any(), any(), any(), any(), any(), any());
    assertThatThrownBy(() -> service.submit(me, request())).isSameAs(failure);
  }

  @Test
  void unknownRequesterIsNotEligible() {
    when(repository.lockRequesterAccountStatus(me)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.submit(me, request()))
        .isInstanceOf(ReservationException.RequesterNotEligible.class);
  }

  @Test
  void onLoanAtStartIsUnavailable() {
    when(repository.equipmentCanBeReserved(eq(item), any())).thenReturn(false);
    assertThatThrownBy(() -> service.submit(me, request()))
        .isInstanceOf(ReservationException.EquipmentUnavailable.class)
        .hasMessageContaining("on loan");
  }

  @Test
  void publishesTheSubmittedEventWithTheStoredStatus() {
    when(repository.lockEquipment(item)).thenReturn(Optional.of(new LockedEquipment(item, "AVAILABLE", true)));
    when(repository.findById(any())).thenAnswer(a -> Optional.of(stored(a.getArgument(0), ReservationStatus.PENDING_APPROVAL, false)));
    var r = service.submit(me, request());
    verify(repository).insert(eq(r.reservationId()), eq(me), eq(item), any(), any(), eq(ReservationStatus.PENDING_APPROVAL), isNull());
    verify(events).publishEvent(argThat((Object e) -> e instanceof ReservationSubmitted s && s.status() == ReservationStatus.PENDING_APPROVAL));
  }

  @Test
  void borrowerWithAnOverdueLoanIsRefusedWithoutWriting() {
    when(repository.hasOverdueLoan(me, NOW)).thenReturn(true);
    assertThatThrownBy(() -> service.submit(me, request()))
        .isInstanceOfSatisfying(
            ReservationException.RulesBroken.class,
            e -> assertThat(e.violations()).extracting(v -> v.code()).containsExactly("overdue-loan"));
    verify(repository, never()).insert(any(), any(), any(), any(), any(), any(), any());
    verify(repository, never()).audit(any(), any(), any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  void submitRecordsWhoBookedWhatAndWhen() {
    when(repository.findById(any())).thenAnswer(a -> Optional.of(stored(a.getArgument(0), ReservationStatus.CONFIRMED, false)));
    var r = service.submit(me, request());
    verify(repository).audit(eq(me), eq("RESERVATION_SUBMIT"), eq(r.reservationId()),
        argThat(s -> s.contains("status=CONFIRMED") && s.contains(item.toString())), eq(NOW));
  }

  @Test
  void cancelRecordsThePreviousStatus() {
    UUID id = UUID.randomUUID();
    when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(stored(id, ReservationStatus.PENDING_APPROVAL, false)));
    when(repository.changeStatus(id, ReservationStatus.PENDING_APPROVAL, ReservationStatus.CANCELLED, 0L)).thenReturn(true);
    when(repository.findById(id)).thenReturn(Optional.of(stored(id, ReservationStatus.CANCELLED, false)));
    service.cancel(me, id);
    verify(repository).audit(eq(me), eq("RESERVATION_CANCEL"), eq(id),
        argThat(s -> s.startsWith("from=PENDING_APPROVAL")), eq(NOW));
  }

  @Test
  void cancelLosingARaceReportsAStaleVersion() {
    UUID id = UUID.randomUUID();
    when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(stored(id, ReservationStatus.CONFIRMED, false)));
    when(repository.changeStatus(id, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, 0L)).thenReturn(false);
    assertThatThrownBy(() -> service.cancel(me, id)).isInstanceOf(ReservationException.StaleVersion.class);
    verify(repository, never()).audit(any(), any(), any(), any(), any());
  }

  @Test
  void pagingIsBounded() {
    assertThatThrownBy(() -> service.mine(me, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.mine(me, null, -1, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.mine(me, null, 0, 51)).isInstanceOf(IllegalArgumentException.class);
  }
}
