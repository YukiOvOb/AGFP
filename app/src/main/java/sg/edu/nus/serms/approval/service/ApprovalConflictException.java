package sg.edu.nus.serms.approval.service;

import java.util.Objects;

/** Adapter-level conflict with safe, authoritative metadata for the API response. */
public class ApprovalConflictException extends RuntimeException {
  public enum Kind {
    STATE("state-conflict", "The reservation cannot be decided in its current state."),
    VERSION("version-conflict", "The reservation changed. Refresh before deciding."),
    EXPIRED("reservation-expired", "This reservation has already started and cannot be approved."),
    DUPLICATE("state-conflict", "A final decision has already been recorded for this reservation.");

    private final String type;
    private final String detail;

    Kind(String type, String detail) {
      this.type = type;
      this.detail = detail;
    }

    public String type() { return type; }
    public String detail() { return detail; }
  }

  private final Kind kind;
  private final String currentStatus;
  private final long currentVersion;

  public ApprovalConflictException(Kind kind, String currentStatus, long currentVersion) {
    super(Objects.requireNonNull(kind).detail());
    this.kind = kind;
    this.currentStatus = Objects.requireNonNull(currentStatus);
    if (currentVersion < 0) throw new IllegalArgumentException("currentVersion must not be negative");
    this.currentVersion = currentVersion;
  }

  public Kind kind() { return kind; }
  public String currentStatus() { return currentStatus; }
  public long currentVersion() { return currentVersion; }
}
