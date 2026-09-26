package sg.edu.nus.serms.shared.security;

import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.identity.service.IdentityService;

@Component
public class IdentityAuthenticationProvider implements AuthenticationProvider {
  private final IdentityService identity;
  private final PasswordEncoder passwords;
  private final String dummyHash;

  public IdentityAuthenticationProvider(IdentityService identity, PasswordEncoder passwords) {
    this.identity = identity;
    this.passwords = passwords;
    this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
  }

  public Authentication authenticate(Authentication request) {
    var user = identity.findByEmail(request.getName());
    String hash = user.map(u -> u.passwordHash()).orElse(dummyHash);
    boolean matches;
    try {
      matches = passwords.matches(String.valueOf(request.getCredentials()), hash);
    } catch (IllegalArgumentException e) {
      matches = false;
    }
    if (user.isEmpty()
        || !matches
        || !user.get().active()
        || !user.get().userId().toString().equals(user.get().notificationPrincipal()))
      throw new BadCredentialsException("Unable to sign in with these credentials.");
    var principal = SermsUserPrincipal.from(user.get());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities());
  }

  public boolean supports(Class<?> type) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
  }
}
