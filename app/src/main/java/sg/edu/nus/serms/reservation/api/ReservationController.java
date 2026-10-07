package sg.edu.nus.serms.reservation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.reservation.domain.Reservation;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.service.ReservationService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

/** UC01 reservation endpoints. The requester is always the signed-in user, never a body field. */
@RestController
@RequestMapping(value = "/api/v1/reservations", produces = "application/json")
public class ReservationController {

  public record CreateReservationRequest(
      @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull(message = "Choose the equipment.")
          UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull(message = "Enter a start time.")
          Instant startAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) @NotNull(message = "Enter an end time.")
          Instant endAt,
      @Size(max = ReservationRequest.MAX_PURPOSE_LENGTH, message = "Keep the purpose under 500 characters.")
          String purpose) {}

  public record ReservationView(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID reservationId,
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String assetTag,
      @Schema(requiredMode = RequiredMode.REQUIRED) String equipmentName,
      @Schema(requiredMode = RequiredMode.REQUIRED) String location,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant startAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant endAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) ReservationStatus status,
      @Schema(nullable = true) String purpose,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant createdAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) long version,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean cancellable) {
    static ReservationView of(Reservation r) {
      return new ReservationView(
          r.reservationId(),
          r.equipmentId(),
          r.assetTag(),
          r.equipmentName(),
          r.location(),
          r.startAt(),
          r.endAt(),
          r.status(),
          r.purpose(),
          r.createdAt(),
          r.version(),
          r.cancellable());
    }
  }

  public record ReservationPage(
      @Schema(requiredMode = RequiredMode.REQUIRED) List<ReservationView> content,
      @Schema(requiredMode = RequiredMode.REQUIRED) int page,
      @Schema(requiredMode = RequiredMode.REQUIRED) int size,
      @Schema(requiredMode = RequiredMode.REQUIRED) long totalElements,
      @Schema(requiredMode = RequiredMode.REQUIRED) int totalPages) {}

  private final ReservationService service;

  public ReservationController(ReservationService service) {
    this.service = service;
  }

  @PostMapping(consumes = "application/json")
  @Operation(operationId = "createReservation", summary = "Reserve equipment for a time slot")
  @ApiResponse(responseCode = "201", description = "Created")
  public ResponseEntity<ReservationView> create(
      @Valid @RequestBody CreateReservationRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    var created =
        service.submit(
            me.getUserId(),
            new ReservationRequest(body.equipmentId(), body.startAt(), body.endAt(), body.purpose()));
    return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.reservationId()))
        .body(ReservationView.of(created));
  }

  @GetMapping("/mine")
  @Operation(operationId = "myReservations", summary = "The signed-in borrower's reservations")
  public ReservationPage mine(
      @AuthenticationPrincipal SermsUserPrincipal me,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = service.mine(me.getUserId(), status, page, size);
    return new ReservationPage(
        result.items().stream().map(ReservationView::of).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        (int) ((result.totalElements() + result.size() - 1) / result.size()));
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getReservation", summary = "One of the signed-in borrower's reservations")
  public ReservationView get(@PathVariable UUID id, @AuthenticationPrincipal SermsUserPrincipal me) {
    return ReservationView.of(service.get(me.getUserId(), id));
  }

  @PostMapping("/{id}/cancel")
  @Operation(operationId = "cancelReservation", summary = "Cancel a pending or confirmed reservation")
  public ReservationView cancel(
      @PathVariable UUID id, @AuthenticationPrincipal SermsUserPrincipal me) {
    return ReservationView.of(service.cancel(me.getUserId(), id));
  }
}
