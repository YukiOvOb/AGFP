package sg.edu.nus.serms.identity.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.identity.service.EmailAlreadyUsedException;
import sg.edu.nus.serms.shared.api.ApiProblem;

@Order(-1)
@RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserProblemHandler {
  @ExceptionHandler(EmailAlreadyUsedException.class)
  ResponseEntity<ProblemDetail> duplicateEmail() {
    var problem = ApiProblem.of(409, "email-in-use", "This email address is already in use.");
    return ResponseEntity.status(409).body(problem);
  }
}
