package sg.edu.nus.serms.approval.domain;

/** Enforces the comment requirements for an approval decision. */
public final class DecisionCommentHandler implements ApprovalHandler {
  public static final int MAX_COMMENT_LENGTH = 1000;

  @Override
  public ApprovalEvaluation evaluate(ApprovalContext context) {
    String comment = context.command().comment();
    if (context.command().decision() == ApprovalDecisionType.REJECTED
        && (comment == null || comment.isBlank())) {
      return ApprovalEvaluation.rejected(ApprovalFailure.REJECTION_REASON_REQUIRED);
    }
    if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
      return ApprovalEvaluation.rejected(ApprovalFailure.COMMENT_TOO_LONG);
    }
    return ApprovalEvaluation.allowed();
  }
}
