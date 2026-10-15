package sg.edu.nus.serms.identity.domain;

import java.util.Set;
import java.util.UUID;

public record UserAccount(
    UUID userId,
    String email,
    String displayName,
    String passwordHash,
    boolean active,
    String notificationPrincipal,
    Set<String> roles,
    long sessionVersion) {
  public UserAccount(UUID userId, String email, String displayName, String passwordHash,
      boolean active, String notificationPrincipal, Set<String> roles) {
    this(userId, email, displayName, passwordHash, active, notificationPrincipal, roles, 0L);
  }
  public UserAccount {
    roles = Set.copyOf(roles);
  }

  @Override
  public String toString() {
    return "UserAccount[" + userId + "]";
  }
}
