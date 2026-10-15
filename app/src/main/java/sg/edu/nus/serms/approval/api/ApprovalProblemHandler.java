package sg.edu.nus.serms.approval.api;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.approval.domain.ApprovalFailure;
import sg.edu.nus.serms.approval.service.ApprovalConflictException;
import sg.edu.nus.serms.approval.service.ApprovalDecisionAlreadyRecordedException;
import sg.edu.nus.serms.approval.service.ApprovalPolicyViolationException;
import sg.edu.nus.serms.approval.service.ApprovalService;
import sg.edu.nus.serms.shared.api.ApiProblem;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ApprovalController.class)
public class ApprovalProblemHandler {
  private final ObjectProvider<ApprovalService> approvals;

  public ApprovalProblemHandler(ObjectProvider<ApprovalService> approvals) {
    this.approvals = approvals;
  }

  @ExceptionHandler(ApprovalDecisionAlreadyRecordedException.class)
  ResponseEntity<ProblemDetail> duplicate(ApprovalDecisionAlreadyRecordedException error) {
    // The proxied decide() has already rolled back. Use a fresh read-only service transaction,
    // not SQL inside the failed transaction, and preserve the required conflict metadata contract.
    var current = approvals.getObject().get(error.reservationId()).reservation();
    return conflict(new ApprovalConflictException(
        ApprovalConflictException.Kind.DUPLICATE, current.status(), current.version()));
  }

  @ExceptionHandler(ApprovalPolicyViolationException.class)
  ResponseEntity<ProblemDetail> policy(ApprovalPolicyViolationException error) {
    if (error.failure() == ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED)
      return ResponseEntity.status(403).body(
          ApiProblem.of(403, "self-approval", "You cannot decide your own reservation."));
    return validation("comment", error.failure() == ApprovalFailure.REJECTION_REASON_REQUIRED
        ? "A rejection reason is required." : "Comment must not exceed 1000 characters.");
  }

  @ExceptionHandler(ApprovalConflictException.class)
  ResponseEntity<ProblemDetail> conflict(ApprovalConflictException error) {
    var problem = ApiProblem.of(409, error.kind().type(), error.kind().detail());
    problem.setProperty("currentStatus", error.currentStatus());
    problem.setProperty("currentVersion", error.currentVersion());
    return ResponseEntity.status(409).body(problem);
  }

  @ExceptionHandler(ApprovalRequestValidationException.class)
  ResponseEntity<ProblemDetail> query(ApprovalRequestValidationException error) {
    return validation(error.field(), error.getMessage());
  }

  private ResponseEntity<ProblemDetail> validation(String field, String message) {
    var problem = ApiProblem.of(400, "validation", "Check the highlighted fields.");
    problem.setProperty("errors", List.of(Map.of("field", field, "message", message)));
    return ResponseEntity.badRequest().body(problem);
  }
}
