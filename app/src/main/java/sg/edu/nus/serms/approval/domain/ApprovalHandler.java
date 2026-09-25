package sg.edu.nus.serms.approval.domain;

/** One rule in the ordered approval Chain of Responsibility. */
@FunctionalInterface
public interface ApprovalHandler {
  ApprovalEvaluation evaluate(ApprovalContext context);
}
