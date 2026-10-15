package sg.edu.nus.serms.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class AdminUserRules {
  private AdminUserRules() {}

  public static String email(String email) {
    String normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    if (normalized.length() > 254 || !normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
      throw new IllegalArgumentException("Invalid email");
    return normalized;
  }

  public static String displayName(String name) {
    if (name == null || name.isBlank() || name.strip().length() > 200)
      throw new IllegalArgumentException("Invalid display name");
    return name.strip();
  }

  public static void password(String password) {
    // Match the existing first-admin strength/BCrypt byte limits; do not trim passwords.
    if (password == null || password.strip().length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Password must contain at least 12 characters and at most 72 UTF-8 bytes");
  }

  public static void roles(Set<AdminUser.Role> roles) {
    if (roles == null || roles.isEmpty() || roles.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Select at least one role");
  }

  public static void page(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
  }

  public static String search(String search) {
    String value = search == null ? "" : search.strip();
    if (value.length() > 200) throw new IllegalArgumentException("Search is too long");
    return value;
  }
}
