package sg.edu.nus.serms.loan.service;

public class LoanConflictException extends RuntimeException {
  public LoanConflictException(String message) { super(message); }
}
