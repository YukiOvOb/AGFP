package sg.edu.nus.serms.approval.application;

import java.util.Objects;
import sg.edu.nus.serms.approval.domain.ApprovalFailure;

/** A domain policy rejection; the API layer will map it to the agreed problem response. */
public final class ApprovalPolicyViolationException extends RuntimeException {

  private final ApprovalFailure failure;

  public ApprovalPolicyViolationException(ApprovalFailure failure) {
    super("Approval request violates policy: " + Objects.requireNonNull(failure, "failure"));
    this.failure = failure;
  }

  public ApprovalFailure failure() {
    return failure;
  }
}
