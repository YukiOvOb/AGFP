package sg.edu.nus.serms.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = "application/json")
public class AuthController {
  public record LoginRequest(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    @Override
    public String toString() {
      return "LoginRequest[REDACTED]";
    }
  }

  private final AuthenticationManager authentication;
  private final SecurityContextRepository contexts;
  private final SessionAuthenticationStrategy sessions;
  private final CookieCsrfTokenRepository csrf;

  public AuthController(
      AuthenticationManager authentication,
      SecurityContextRepository contexts,
      SessionAuthenticationStrategy sessions,
      CookieCsrfTokenRepository csrf) {
    this.authentication = authentication;
    this.contexts = contexts;
    this.sessions = sessions;
    this.csrf = csrf;
  }

  @GetMapping("/csrf")
  @Operation(operationId = "csrf")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "204",
      description = "Success")
  public ResponseEntity<Void> csrf(@io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken token) {
    token.getToken();
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  @Operation(operationId = "login")
  public CurrentUser login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var result =
        authentication.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
    sessions.onAuthentication(result, request, response);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return current((SermsUserPrincipal) result.getPrincipal());
  }

  @GetMapping("/me")
  @Operation(operationId = "currentUser")
  public CurrentUser me(@AuthenticationPrincipal SermsUserPrincipal user) {
    return current(user);
  }

  @PostMapping("/logout")
  @Operation(operationId = "logout")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "204",
      description = "Success")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler()
        .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    csrf.saveToken(null, request, response);
    return ResponseEntity.noContent().build();
  }

  private CurrentUser current(SermsUserPrincipal user) {
    return new CurrentUser(
        user.getUserId(), user.getEmail(), user.getDisplayName(), user.getRoles());
  }
}
