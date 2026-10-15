package sg.edu.nus.serms.identity.service;

public final class AccountChangeConflictException extends RuntimeException {
  public enum Reason { STALE_ACCOUNT, SELF_DEACTIVATION, LAST_ACTIVE_ADMIN }
  private final Reason reason;
  public AccountChangeConflictException(Reason reason) {
    super("Administrator account change was refused");
    this.reason = reason;
  }
  public Reason reason() { return reason; }
}
