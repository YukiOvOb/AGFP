package sg.edu.nus.serms.approval.domain;

/** Stable business failure codes that the API layer can map to Problem Details. */
public enum ApprovalFailure {
  SELF_APPROVAL_NOT_ALLOWED,
  REJECTION_REASON_REQUIRED,
  COMMENT_TOO_LONG
}
