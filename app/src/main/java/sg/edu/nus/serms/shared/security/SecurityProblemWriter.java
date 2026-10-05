package sg.edu.nus.serms.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.shared.api.ApiProblem;

@Component
public class SecurityProblemWriter {
  private final ObjectMapper mapper;

  public SecurityProblemWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void write(HttpServletResponse response, int status, String type, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    mapper.writeValue(response.getOutputStream(), ApiProblem.of(status, type, detail));
  }
}
