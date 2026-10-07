package sg.edu.nus.serms.equipment.api;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.equipment.domain.InvalidSearchException;
import sg.edu.nus.serms.shared.api.ApiProblem;

/** Turns correctable search input into a 400 whose detail the user can act on. */
@RestControllerAdvice(assignableTypes = EquipmentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EquipmentProblemHandler {
  @ExceptionHandler(InvalidSearchException.class)
  ResponseEntity<ProblemDetail> invalid(InvalidSearchException e) {
    var p = ApiProblem.of(400, "validation", e.getMessage());
    p.setProperty("errors", List.of());
    return ResponseEntity.badRequest().body(p);
  }
}
