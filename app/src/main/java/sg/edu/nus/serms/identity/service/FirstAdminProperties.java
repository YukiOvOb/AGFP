package sg.edu.nus.serms.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One-off settings for creating the first administrator of an empty production database. Set
 * them for a single start and remove them afterwards; a blank email means "not requested".
 */
@ConfigurationProperties("serms.bootstrap.admin")
public record FirstAdminProperties(String email, String displayName, String password) {
  boolean requested() {
    return email != null && !email.isBlank();
  }

  /** Never includes the password. */
  @Override
  public String toString() {
    return "FirstAdminProperties[email=" + email + ", displayName=" + displayName + "]";
  }
}
