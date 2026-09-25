package sg.edu.nus.serms.approval.domain;

import java.util.Objects;
import java.util.UUID;

/** Authenticated actor and reservation facts evaluated by approval rules. */
public record ApprovalContext(ApprovalCommand command, UUID requesterId, UUID approverId) {

  public ApprovalContext {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(requesterId, "requesterId must not be null");
    Objects.requireNonNull(approverId, "approverId must not be null");
  }
}
