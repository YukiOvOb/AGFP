package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class FirstAdminBootstrapTest {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final PasswordEncoder passwords = mock(PasswordEncoder.class);
  private final FirstAdminBootstrap bootstrap = new FirstAdminBootstrap(jdbc, passwords);

  @Test
  void withoutAnEmailNothingIsReadOrWritten() {
    assertThat(bootstrap.run(new FirstAdminProperties(null, "Admin", "a-long-password"))).isEqualTo(
        FirstAdminBootstrap.Outcome.NOT_REQUESTED);
    assertThat(bootstrap.run(new FirstAdminProperties("  ", "Admin", "a-long-password"))).isEqualTo(
        FirstAdminBootstrap.Outcome.NOT_REQUESTED);
    verifyNoInteractions(jdbc, passwords);
  }

  @ParameterizedTest(name = "{3}")
  @CsvSource(delimiter = '|', value = {
      "not-an-email|Admin|a-long-password-1|email",
      "admin@example|Admin|a-long-password-1|email without a domain dot",
      "admin@example.test|'  '|a-long-password-1|blank display name",
      "admin@example.test|Admin|short-pw|short password",
      "admin@example.test|Admin|'           x'|password padded with spaces"})
  void invalidSettingsStopTheStartWithoutTouchingTheDatabase(String email, String name, String password,
      String reason) {
    assertThatThrownBy(() -> bootstrap.run(new FirstAdminProperties(email, name, password)))
        .as(reason)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("First-admin bootstrap:")
        .hasMessageNotContaining(password.strip());
    verifyNoInteractions(jdbc, passwords);
  }

  @Test
  void passwordsLongerThanBcryptAcceptsAreRefused() {
    String tooLong = "x".repeat(FirstAdminBootstrap.MAX_PASSWORD_BYTES + 1);
    assertThatThrownBy(() -> bootstrap.run(new FirstAdminProperties("admin@example.test", "Admin", tooLong)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at most 72 bytes");
    verifyNoInteractions(jdbc, passwords);
  }

  @Test
  void settingsNeverPrintThePassword() {
    assertThat(new FirstAdminProperties("admin@example.test", "Admin", "secret-value-123").toString())
        .doesNotContain("secret-value-123");
  }
}
