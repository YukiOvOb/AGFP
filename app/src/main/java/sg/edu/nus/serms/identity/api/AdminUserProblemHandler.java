package sg.edu.nus.serms.identity.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.identity.service.EmailAlreadyUsedException;
import sg.edu.nus.serms.identity.service.DisplayNameConflictException;
import sg.edu.nus.serms.identity.service.AccountChangeConflictException;
import sg.edu.nus.serms.shared.api.ApiProblem;

@Order(-1)
@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserProblemHandler {
  @ExceptionHandler(AccountChangeConflictException.class)
  ResponseEntity<ProblemDetail> accountConflict(AccountChangeConflictException failure) {
    String type = switch (failure.reason()) {
      case STALE_ACCOUNT -> "account-version-conflict";
      case SELF_DEACTIVATION -> "self-deactivation";
      case LAST_ACTIVE_ADMIN -> "last-active-admin";
    };
    String detail = switch (failure.reason()) {
      case STALE_ACCOUNT -> "This account was changed. Refresh the user list and try again.";
      case SELF_DEACTIVATION -> "You cannot deactivate your own account.";
      case LAST_ACTIVE_ADMIN -> "The last active administrator must be retained.";
    };
    return ResponseEntity.status(409).body(ApiProblem.of(409, type, detail));
  }
  @ExceptionHandler(EmailAlreadyUsedException.class)
  ResponseEntity<ProblemDetail> duplicateEmail() {
    var problem = ApiProblem.of(409, "email-in-use", "This email address is already in use.");
    return ResponseEntity.status(409).body(problem);
  }

  @ExceptionHandler(DisplayNameConflictException.class)
  ResponseEntity<ProblemDetail> displayNameConflict() {
    return ResponseEntity.status(409).body(ApiProblem.of(409, "display-name-conflict",
        "This account's display name has changed. Refresh the user list and try again."));
  }
}
