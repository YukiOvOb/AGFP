package sg.edu.nus.serms.shared.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> authentication() {
    return response(401, "unauthenticated", "Unable to sign in with these credentials.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied() {
    return response(403, "forbidden", "You do not have permission for this action.");
  }

  @ExceptionHandler({RecordNotFoundException.class, NoResourceFoundException.class})
  ResponseEntity<ProblemDetail> missing() {
    return response(404, "not-found", "The requested record is unavailable.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
    var p = ApiProblem.of(400, "validation", "Check the highlighted fields.");
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(
                x ->
                    Map.of(
                        "field",
                        x.getField(),
                        "message",
                        x.getDefaultMessage() == null ? "Invalid value" : x.getDefaultMessage()))
            .toList());
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> malformed() {
    var p = ApiProblem.of(400, "validation", "The request contains an invalid value.");
    p.setProperty("errors", List.of());
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> unavailable() {
    return response(
        503, "unavailable", "This service is temporarily unavailable. Please try again later.");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> method() {
    return response(405, "method-not-allowed", "This HTTP method is not supported.");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> mediaType() {
    return response(415, "unsupported-media-type", "Send a supported content type.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected() {
    return response(500, "internal", "The request could not be completed.");
  }

  private ResponseEntity<ProblemDetail> response(int status, String type, String detail) {
    return ResponseEntity.status(status).body(ApiProblem.of(status, type, detail));
  }
}
