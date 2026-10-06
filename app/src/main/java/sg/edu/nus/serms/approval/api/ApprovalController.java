package sg.edu.nus.serms.approval.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.approval.api.ApprovalApiDtos.*;
import sg.edu.nus.serms.approval.service.ApprovalService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@RestController
@RequestMapping(value = "/api/v1/approvals", produces = "application/json")
@ConditionalOnProperty(name = "serms.approval.enabled", havingValue = "true")
@PreAuthorize("hasRole('APPROVER')")
public class ApprovalController {
  private final ApprovalService approvals;

  public ApprovalController(ApprovalService approvals) { this.approvals = approvals; }

  @GetMapping("/pending")
  @Operation(operationId = "listPendingApprovals")
  public PendingPageResponse pending(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "startAt,asc") String sort) {
    if (page < 0) throw new ApprovalRequestValidationException("page", "Page must not be negative.");
    if (size < 1 || size > 100)
      throw new ApprovalRequestValidationException("size", "Page size must be between 1 and 100.");
    if (!"startAt,asc".equals(sort))
      throw new ApprovalRequestValidationException("sort", "Supported order is startAt,asc.");
    return PendingPageResponse.from(
        approvals.findPendingApproval(PageRequest.of(page, size, Sort.by("startAt").ascending())));
  }

  @GetMapping("/{reservationId}")
  @Operation(operationId = "getApprovalReservation")
  public DetailResponse get(@PathVariable UUID reservationId) {
    return DetailResponse.from(approvals.get(reservationId));
  }

  @PostMapping("/{reservationId}/decision")
  @Operation(operationId = "decideReservation")
  public DecisionOutcomeResponse decide(
      @PathVariable UUID reservationId, @Valid @RequestBody DecisionRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return DecisionOutcomeResponse.from(approvals.decide(me.getUserId(), body.command(reservationId)));
  }
}
