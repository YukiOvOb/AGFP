package sg.edu.nus.serms.approval.api;

/** Invalid pagination parameters, represented by the same field errors as body validation. */
class ApprovalRequestValidationException extends RuntimeException {
  private final String field;

  ApprovalRequestValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  String field() { return field; }
}
