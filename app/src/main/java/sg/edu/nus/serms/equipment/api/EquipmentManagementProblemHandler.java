package sg.edu.nus.serms.equipment.api;

import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import sg.edu.nus.serms.equipment.service.EquipmentManagementException;
import sg.edu.nus.serms.shared.api.ApiProblem;

@RestControllerAdvice(assignableTypes = EquipmentManagementController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class EquipmentManagementProblemHandler {
  @ExceptionHandler(EquipmentManagementException.class)
  ResponseEntity<ProblemDetail> management(EquipmentManagementException e) {
    var problem = ApiProblem.of(e.status(), e.status() == 409 ? "equipment-conflict" : "validation", e.getMessage());
    problem.setProperty("errors", e.field() == null ? List.of() : List.of(Map.of("field", e.field(), "message", e.getMessage())));
    return ResponseEntity.status(e.status()).body(problem);
  }
}
