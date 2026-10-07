package sg.edu.nus.serms.equipment.domain;

/** Search input that a user can correct; the message is safe to show. */
public class InvalidSearchException extends IllegalArgumentException {
  public InvalidSearchException(String message) {
    super(message);
  }
}
