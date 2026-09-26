package sg.edu.nus.serms.shared.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sg.edu.nus.serms.identity.domain.UserAccount;

/** Session identity contains no password hash; authentication credentials stay in the provider. */
public final class SermsUserPrincipal implements UserDetails {
  private final UUID userId;
  private final String email;
  private final String displayName;
  private final Set<String> roles;

  public SermsUserPrincipal(UUID userId, String email, String displayName, Set<String> roles) {
    this.userId = userId;
    this.email = email;
    this.displayName = displayName;
    this.roles = Set.copyOf(roles);
  }

  public static SermsUserPrincipal from(UserAccount u) {
    return new SermsUserPrincipal(u.userId(), u.email(), u.displayName(), u.roles());
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public String getUsername() {
    return userId.toString();
  }

  @JsonIgnore
  public String getPassword() {
    return "";
  }

  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
  }
}
