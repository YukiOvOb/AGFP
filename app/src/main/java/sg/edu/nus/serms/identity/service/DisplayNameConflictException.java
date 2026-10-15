package sg.edu.nus.serms.identity.service;

public class DisplayNameConflictException extends RuntimeException {
  public DisplayNameConflictException() {
    super("This account's display name has changed. Refresh the user list and try again.");
  }
}
