package sg.edu.nus.serms.approval.domain;

/** Prevents a requester from deciding their own reservation, regardless of other roles. */
public final class NoSelfApprovalHandler implements ApprovalHandler {

  @Override
  public ApprovalEvaluation evaluate(ApprovalContext context) {
    if (context.requesterId().equals(context.approverId())) {
      return ApprovalEvaluation.rejected(ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED);
    }
    return ApprovalEvaluation.allowed();
  }
}
