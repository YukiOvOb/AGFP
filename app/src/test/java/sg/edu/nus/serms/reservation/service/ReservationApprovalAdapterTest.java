package sg.edu.nus.serms.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import sg.edu.nus.serms.approval.service.ApprovalConflictException;
import sg.edu.nus.serms.approval.service.ApprovalConflictException.Kind;
import sg.edu.nus.serms.reservation.domain.PendingReservation;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

class ReservationApprovalAdapterTest {
  private final ReservationApprovalService service = mock(ReservationApprovalService.class);
  private final ReservationApprovalAdapter adapter = new ReservationApprovalAdapter(service);
  private final UUID id = UUID.randomUUID();

  private PendingReservation view(ReservationStatus status, long version) {
    return new PendingReservation(
        id,
        UUID.randomUUID(),
        "Alice Tan",
        new PendingReservation.EquipmentSummary(UUID.randomUUID(), "EQ-001", "Oscilloscope", "Lab"),
        Instant.parse("2026-11-01T01:00:00Z"),
        Instant.parse("2026-11-01T03:00:00Z"),
        "Lab session",
        status,
        version,
        Instant.parse("2026-10-01T00:00:00Z"),
        false);
  }

  @Test
  void mapsEveryFieldOfTheReservationView() {
    var source = view(ReservationStatus.PENDING_APPROVAL, 4);
    when(service.get(id)).thenReturn(source);

    var snapshot = adapter.get(id);

    assertThat(snapshot.reservationId()).isEqualTo(source.reservationId());
    assertThat(snapshot.requesterId()).isEqualTo(source.requesterId());
    assertThat(snapshot.requesterName()).isEqualTo("Alice Tan");
    assertThat(snapshot.equipment().equipmentId()).isEqualTo(source.equipment().equipmentId());
    assertThat(snapshot.equipment().assetTag()).isEqualTo("EQ-001");
    assertThat(snapshot.equipment().name()).isEqualTo("Oscilloscope");
    assertThat(snapshot.equipment().category()).isEqualTo("Lab");
    assertThat(snapshot.startAt()).isEqualTo(source.startAt());
    assertThat(snapshot.endAt()).isEqualTo(source.endAt());
    assertThat(snapshot.purpose()).isEqualTo("Lab session");
    assertThat(snapshot.status()).isEqualTo("PENDING_APPROVAL");
    assertThat(snapshot.version()).isEqualTo(4);
    assertThat(snapshot.createdAt()).isEqualTo(source.createdAt());
    assertThat(snapshot.expired()).isFalse();
  }

  @Test
  void pendingListDelegatesAndKeepsPaging() {
    var page = PageRequest.of(1, 5);
    when(service.findPendingApproval(page))
        .thenReturn(new PageImpl<>(List.of(view(ReservationStatus.PENDING_APPROVAL, 0)), page, 6));

    var result = adapter.findPendingApproval(page);

    assertThat(result.getContent()).extracting(s -> s.reservationId()).containsExactly(id);
    assertThat(result.getTotalElements()).isEqualTo(6);
    assertThat(result.getNumber()).isEqualTo(1);
  }

  @Test
  void stateConflictBecomesApprovalStateConflict() {
    when(service.confirm(id, 2))
        .thenThrow(new ReservationStateConflictException(id, ReservationStatus.REJECTED, 3, "no"));
    assertConflict(() -> adapter.confirm(id, 2), Kind.STATE, "REJECTED", 3);
  }

  @Test
  void versionConflictBecomesApprovalVersionConflict() {
    when(service.reject(id, 1))
        .thenThrow(
            new ReservationVersionConflictException(id, ReservationStatus.PENDING_APPROVAL, 2));
    assertConflict(() -> adapter.reject(id, 1), Kind.VERSION, "PENDING_APPROVAL", 2);
  }

  @Test
  void expiredBecomesApprovalExpiredConflict() {
    when(service.confirm(id, 5))
        .thenThrow(new ReservationExpiredException(id, ReservationStatus.PENDING_APPROVAL, 5));
    assertConflict(() -> adapter.confirm(id, 5), Kind.EXPIRED, "PENDING_APPROVAL", 5);
  }

  @Test
  void notFoundPassesThroughForTheShared404() {
    when(service.get(id)).thenThrow(new ReservationNotFoundException());
    assertThatThrownBy(() -> adapter.get(id)).isInstanceOf(ReservationNotFoundException.class);
  }

  private static void assertConflict(
      ThrowingCallable call, Kind kind, String status, long version) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            ApprovalConflictException.class,
            e -> {
              assertThat(e.kind()).isEqualTo(kind);
              assertThat(e.currentStatus()).isEqualTo(status);
              assertThat(e.currentVersion()).isEqualTo(version);
            });
  }
}
