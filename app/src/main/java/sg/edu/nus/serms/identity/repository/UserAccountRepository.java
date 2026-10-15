package sg.edu.nus.serms.identity.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;
import sg.edu.nus.serms.identity.domain.UserAccount;

@Repository
public class UserAccountRepository {
  private final JdbcTemplate jdbc;
  @Value("${serms.identity.schema:public}")
  private String schema = "public";

  public UserAccountRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserAccount> findByEmail(String email) {
    return find("u.email = ?", email);
  }

  public Optional<UserAccount> findById(UUID id) {
    return find("u.user_id = ?", id);
  }

  private Optional<UserAccount> find(String condition, Object value) {
    if (!schema.equals("public") && !schema.equals("serms"))
      throw new IllegalStateException("Unsupported identity schema");
    // Column contract agreed with the database owner; production DDL is owned by that PR.
    return jdbc.query(
        """
   SELECT u.user_id,u.email,u.display_name,u.password_hash,u.account_status,
          u.notification_principal,r.code
   FROM %1$s.app_user u
   LEFT JOIN %1$s.user_role ur ON ur.user_id=u.user_id
   LEFT JOIN %1$s.role r ON r.role_id=ur.role_id
   WHERE
   """.formatted(schema)
            + condition,
        rs -> {
          if (!rs.next()) return Optional.empty();
          UUID id = rs.getObject("user_id", UUID.class);
          String email = rs.getString("email");
          String name = rs.getString("display_name"),
              hash = rs.getString("password_hash"),
              principal = rs.getString("notification_principal");
          boolean active = "ACTIVE".equals(rs.getString("account_status"));
          Set<String> roles = new TreeSet<>();
          do {
            String role = rs.getString("code");
            if (role != null) roles.add(role);
          } while (rs.next());
          return Optional.of(new UserAccount(id, email, name, hash, active, principal, roles));
        },
        value);
  }
}
