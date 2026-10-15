package sg.edu.nus.serms.identity.service;

public class EmailAlreadyUsedException extends RuntimeException {
  public EmailAlreadyUsedException() { super("This email address is already in use."); }
}
