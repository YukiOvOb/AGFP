package sg.edu.nus.serms.reservation.api;

import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.reservation.service.ReservationException;
import sg.edu.nus.serms.shared.api.ApiProblem;

/**
 * Maps UC01 refusals to Problem Details: 409 for slot and state conflicts, 422 for broken booking
 * rules (with every violation listed), 403 for ineligible accounts.
 */
@RestControllerAdvice(assignableTypes = ReservationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationProblemHandler {

  @ExceptionHandler(ReservationException.class)
  ResponseEntity<ProblemDetail> refused(ReservationException e) {
    int status =
        e instanceof ReservationException.RulesBroken
            ? 422
            : e instanceof ReservationException.RequesterNotEligible ? 403 : 409;
    var p = ApiProblem.of(status, e.type(), e.getMessage());
    if (e instanceof ReservationException.RulesBroken broken) {
      p.setProperty(
          "violations",
          broken.violations().stream()
              .map(v -> Map.of("code", v.code(), "message", v.message()))
              .toList());
    } else {
      p.setProperty("violations", List.of());
    }
    return ResponseEntity.status(status).body(p);
  }
}
