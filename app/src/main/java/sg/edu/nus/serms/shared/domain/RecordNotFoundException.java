package sg.edu.nus.serms.shared.domain;

public class RecordNotFoundException extends RuntimeException {
  public RecordNotFoundException() {
    super("The requested record is unavailable.");
  }
}
