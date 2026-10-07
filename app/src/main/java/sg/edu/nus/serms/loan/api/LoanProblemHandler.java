package sg.edu.nus.serms.loan.api;

import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.loan.service.LoanConflictException;
import sg.edu.nus.serms.shared.api.ApiProblem;

@Order(0)
@RestControllerAdvice(assignableTypes=LoanController.class)
public class LoanProblemHandler {
  @ExceptionHandler(LoanConflictException.class)
  public ResponseEntity<ProblemDetail> conflict(LoanConflictException e) {
    return ResponseEntity.status(409).body(ApiProblem.of(409,"loan-conflict",e.getMessage()));
  }
}
