package sg.edu.nus.serms.maintenance.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.maintenance.domain.InvalidMaintenanceTransitionException;
import sg.edu.nus.serms.maintenance.repository.MaintenanceVersionConflictException;
import sg.edu.nus.serms.maintenance.service.*;
import sg.edu.nus.serms.shared.api.ApiProblem;

/** UC04-specific mappings precede the shared fallback; shared security/validation stays shared. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MaintenanceController.class)
public class MaintenanceExceptionHandler {
  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  ResponseEntity<ProblemDetail> missingParameter() {
    var p = ApiProblem.of(400, "validation", "Supply the required request parameters.");
    p.setProperty("errors", java.util.List.of());
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler({MaintenanceCaseNotFoundException.class, MaintenanceEquipmentNotFoundException.class})
  ResponseEntity<ProblemDetail> missing() {
    return problem(404, "not-found", "The requested maintenance case or equipment is unavailable.");
  }

  @ExceptionHandler({InvalidMaintenanceTransitionException.class, MaintenanceStateConflictException.class})
  ResponseEntity<ProblemDetail> stateConflict() {
    return problem(409, "state-conflict", "The current state does not permit this maintenance operation.");
  }

  @ExceptionHandler(MaintenanceVersionConflictException.class)
  ResponseEntity<ProblemDetail> versionConflict() {
    return problem(409, "version-conflict", "The maintenance case changed. Refresh and try again.");
  }

  @ExceptionHandler(InvalidMaintenanceTechnicianException.class)
  ResponseEntity<ProblemDetail> technician() {
    var p = ApiProblem.of(400, "validation", "Select an active maintenance technician.");
    p.setProperty("errors", java.util.List.of());
    return ResponseEntity.badRequest().body(p);
  }

  private ResponseEntity<ProblemDetail> problem(int status, String type, String detail) {
    return ResponseEntity.status(status).body(ApiProblem.of(status, type, detail));
  }
}
