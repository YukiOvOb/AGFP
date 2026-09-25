package sg.edu.nus.serms.approval.domain;

import java.util.Objects;
import java.util.Optional;

/** Result from the approval rule chain. */
public record ApprovalEvaluation(Optional<ApprovalFailure> failureReason) {

  public ApprovalEvaluation {
    Objects.requireNonNull(failureReason, "failureReason must not be null");
  }

  public static ApprovalEvaluation allowed() {
    return new ApprovalEvaluation(Optional.empty());
  }

  public static ApprovalEvaluation rejected(ApprovalFailure failure) {
    return new ApprovalEvaluation(
        Optional.of(Objects.requireNonNull(failure, "failure must not be null")));
  }

  public boolean isAllowed() {
    return failureReason.isEmpty();
  }
}
