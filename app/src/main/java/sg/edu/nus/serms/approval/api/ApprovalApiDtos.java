package sg.edu.nus.serms.approval.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import sg.edu.nus.serms.approval.domain.ApprovalCommand;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.*;

public final class ApprovalApiDtos {
  private ApprovalApiDtos() {}

  @RejectionCommentRequired
  public record DecisionRequest(
      @NotNull ApprovalDecisionType decision,
      @Size(max = 1000) String comment,
      @NotNull @PositiveOrZero @Schema(type = "integer", format = "int64")
      @JsonDeserialize(using = Int64VersionDeserializer.class) Long expectedVersion) {
    public ApprovalCommand command(UUID reservationId) {
      return new ApprovalCommand(reservationId, decision, comment, expectedVersion);
    }
  }

  public record DecisionResponse(
      UUID approvalId, UUID reservationId, UUID approverId, ApprovalDecisionType decision,
      String comment, Instant decidedAt) {
    static DecisionResponse from(RecordedApprovalDecision item) {
      return item == null ? null : new DecisionResponse(item.approvalDecisionId(),
          item.reservationId(), item.approverId(), item.decision(), item.comment(), item.decidedAt());
    }
  }

  public record DecisionOutcomeResponse(
      ApprovalReservationSnapshot reservation, DecisionResponse decision) {
    static DecisionOutcomeResponse from(ApprovalOutcome result) {
      return new DecisionOutcomeResponse(result.reservation(), DecisionResponse.from(result.decision()));
    }
  }

  /** GET detail keeps the reservation fields at the top level, with an optional decision. */
  public record DetailResponse(
      UUID reservationId, UUID requesterId, String requesterName,
      ApprovalReservationSnapshot.EquipmentSummary equipment,
      Instant startAt, Instant endAt, String purpose, String status, long version,
      Instant createdAt, boolean expired, DecisionResponse decision) {
    static DetailResponse from(ApprovalDetails item) {
      var r = item.reservation();
      return new DetailResponse(r.reservationId(), r.requesterId(), r.requesterName(), r.equipment(),
          r.startAt(), r.endAt(), r.purpose(), r.status(), r.version(), r.createdAt(), r.expired(),
          DecisionResponse.from(item.decision()));
    }
  }

  /** Stable page JSON, independent of Spring Data's internal PageImpl serialization. */
  public record PendingPageResponse(
      List<ApprovalReservationSnapshot> content, int number, int size, long totalElements,
      int totalPages) {
    static PendingPageResponse from(Page<ApprovalReservationSnapshot> page) {
      return new PendingPageResponse(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
          page.getTotalElements(), page.getTotalPages());
    }
  }
}
