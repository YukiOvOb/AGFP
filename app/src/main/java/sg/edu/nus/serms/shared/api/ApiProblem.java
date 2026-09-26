package sg.edu.nus.serms.shared.api;

import java.net.URI;
import org.springframework.http.*;

public final class ApiProblem {
  private ApiProblem() {}

  public static ProblemDetail of(int status, String type, String detail) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setType(URI.create("/problems/" + type));
    return p;
  }
}
