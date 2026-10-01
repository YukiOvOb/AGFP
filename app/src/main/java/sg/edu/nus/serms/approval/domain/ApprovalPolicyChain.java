package sg.edu.nus.serms.approval.domain;

import java.util.List;
import java.util.Objects;

/** Runs approval rules in order and stops at the first rejection. */
public final class ApprovalPolicyChain {
  private final List<ApprovalHandler> handlers;

  public ApprovalPolicyChain(List<ApprovalHandler> handlers) {
    Objects.requireNonNull(handlers, "handlers must not be null");
    if (handlers.isEmpty()) {
      throw new IllegalArgumentException("At least one approval handler is required");
    }
    this.handlers = List.copyOf(handlers);
  }

  public ApprovalEvaluation evaluate(ApprovalContext context) {
    Objects.requireNonNull(context, "context must not be null");
    for (ApprovalHandler handler : handlers) {
      ApprovalEvaluation evaluation =
          Objects.requireNonNull(handler.evaluate(context), "handler result must not be null");
      if (!evaluation.isAllowed()) {
        return evaluation;
      }
    }
    return ApprovalEvaluation.allowed();
  }
}
