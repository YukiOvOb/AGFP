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
    Set<String> roles) {
  public UserAccount {
    roles = Set.copyOf(roles);
  }

  @Override
  public String toString() {
    return "UserAccount[" + userId + "]";
  }
}
