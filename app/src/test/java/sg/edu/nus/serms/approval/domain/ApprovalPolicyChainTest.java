package sg.edu.nus.serms.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalPolicyChainTest {
  private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID APPROVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @Test
  void permitsApprovalByAnotherApproverWithoutComment() {
    ApprovalEvaluation result =
        chain().evaluate(context(ApprovalDecisionType.APPROVED, null, REQUESTER_ID, APPROVER_ID));

    assertThat(result.isAllowed()).isTrue();
    assertThat(result.failureReason()).isEmpty();
  }

  @Test
  void rejectsSelfApproval() {
    ApprovalEvaluation result =
        chain().evaluate(context(ApprovalDecisionType.APPROVED, null, REQUESTER_ID, REQUESTER_ID));

    assertThat(result.failureReason()).contains(ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED);
  }

  @Test
  void rejectsRejectionWithoutReason() {
    ApprovalEvaluation result =
        chain().evaluate(context(ApprovalDecisionType.REJECTED, "  \t ", REQUESTER_ID, APPROVER_ID));

    assertThat(result.failureReason()).contains(ApprovalFailure.REJECTION_REASON_REQUIRED);
  }

  @Test
  void acceptsRejectionWithReason() {
    ApprovalEvaluation result =
        chain().evaluate(context(ApprovalDecisionType.REJECTED, "Insufficient purpose", REQUESTER_ID, APPROVER_ID));

    assertThat(result.isAllowed()).isTrue();
  }

  @Test
  void rejectsCommentOverMaximumLength() {
    String tooLong = "x".repeat(DecisionCommentHandler.MAX_COMMENT_LENGTH + 1);

    ApprovalEvaluation result =
        chain().evaluate(context(ApprovalDecisionType.APPROVED, tooLong, REQUESTER_ID, APPROVER_ID));

    assertThat(result.failureReason()).contains(ApprovalFailure.COMMENT_TOO_LONG);
  }

  @Test
  void stopsAtFirstRejectedHandler() {
    int[] laterHandlerCalls = {0};
    ApprovalHandler rejectingHandler =
        context -> ApprovalEvaluation.rejected(ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED);
    ApprovalHandler laterHandler =
        context -> {
          laterHandlerCalls[0]++;
          return ApprovalEvaluation.allowed();
        };

    ApprovalEvaluation result =
        new ApprovalPolicyChain(List.of(rejectingHandler, laterHandler))
            .evaluate(context(ApprovalDecisionType.APPROVED, null, REQUESTER_ID, APPROVER_ID));

    assertThat(result.failureReason()).contains(ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED);
    assertThat(laterHandlerCalls[0]).isZero();
  }

  @Test
  void requiresAtLeastOneHandler() {
    assertThatThrownBy(() -> new ApprovalPolicyChain(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ApprovalPolicyChain chain() {
    return new ApprovalPolicyChain(List.of(new NoSelfApprovalHandler(), new DecisionCommentHandler()));
  }

  private ApprovalContext context(
      ApprovalDecisionType decision, String comment, UUID requesterId, UUID approverId) {
    return new ApprovalContext(
        new ApprovalCommand(RESERVATION_ID, decision, comment, 0L), requesterId, approverId);
  }
}
