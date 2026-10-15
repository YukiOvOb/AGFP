package sg.edu.nus.serms.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.edu.nus.serms.identity.service.IdentityService;

/** Lazy, cross-instance session revocation; runs after context loading and before CSRF/authorization. */
public final class SessionVersionFilter extends OncePerRequestFilter {
  private static final Set<String> PUBLIC_PATHS = Set.of(
      "/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/health", "/error");
  private final IdentityService identity;
  private final SecurityProblemWriter problems;

  public SessionVersionFilter(IdentityService identity, SecurityProblemWriter problems) {
    this.identity = identity;
    this.problems = problems;
  }

  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    return PUBLIC_PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()));
  }

  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof SermsUserPrincipal principal
        && !identity.isSessionCurrent(principal.getUserId(), principal.getSessionVersion())) {
      new SecurityContextLogoutHandler().logout(request, response, authentication);
      problems.write(response, 401, "unauthenticated", "Sign in to continue.");
      return;
    }
    chain.doFilter(request, response);
  }
}
