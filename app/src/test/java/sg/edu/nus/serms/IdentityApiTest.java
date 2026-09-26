package sg.edu.nus.serms;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.serms.identity.api.AuthController;
import sg.edu.nus.serms.identity.domain.UserAccount;
import sg.edu.nus.serms.identity.service.IdentityService;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.security.IdentityAuthenticationProvider;

@WebMvcTest(controllers = AuthController.class, properties = "serms.security.secure-cookies=false")
@Import({
  SecurityConfig.class,
  IdentityAuthenticationProvider.class,
  sg.edu.nus.serms.shared.security.SecurityProblemWriter.class
})
class IdentityApiTest {
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder encoder;
  @MockitoBean IdentityService identity;
  final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

  org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() throws Exception {
    var token =
        mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    return request -> {
      request.setCookies(token);
      request.addHeader("X-XSRF-TOKEN", token.getValue());
      return request;
    };
  }

  @BeforeEach
  void setup() {
    when(identity.findByEmail("wang@example.com"))
        .thenReturn(
            Optional.of(
                new UserAccount(
                    id,
                    "wang@example.com",
                    "Wang",
                    encoder.encode("test-password"),
                    true,
                    id.toString(),
                    Set.of("BORROWER"))));
  }

  String body(String password) {
    return "{\"email\":\"wang@example.com\",\"password\":\"" + password + "\"}";
  }

  @Test
  void loginRequiresCsrf() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content(body("test-password")))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginPersistsUuidIdentityAndLogoutInvalidatesSession() throws Exception {
    var old = new MockHttpSession();
    String oldId = old.getId();
    var response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .session(old)
                    .with(realCsrf())
                    .contentType("application/json")
                    .content(body("test-password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(id.toString()))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
    var session = (MockHttpSession) response.getRequest().getSession(false);
    assertThat(session.getId()).isNotEqualTo(oldId);
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("wang@example.com"));
    mvc.perform(post("/api/v1/auth/logout").session(session).with(realCsrf()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidCredentialsAreUniform() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(realCsrf())
                .contentType("application/json")
                .content(body("wrong")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    when(identity.findByEmail("wang@example.com")).thenReturn(Optional.empty());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(realCsrf())
                .contentType("application/json")
                .content(body("wrong")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void disabledOrMismatchedPrincipalCannotLogin() throws Exception {
    for (var account :
        List.of(
            new UserAccount(
                id,
                "wang@example.com",
                "Wang",
                encoder.encode("test-password"),
                false,
                id.toString(),
                Set.of()),
            new UserAccount(
                id,
                "wang@example.com",
                "Wang",
                encoder.encode("test-password"),
                true,
                "wrong",
                Set.of()))) {
      when(identity.findByEmail("wang@example.com")).thenReturn(Optional.of(account));
      mvc.perform(
              post("/api/v1/auth/login")
                  .with(realCsrf())
                  .contentType("application/json")
                  .content(body("test-password")))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void validationReturnsFieldErrors() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(realCsrf())
                .contentType("application/json")
                .content("{\"email\":\"bad\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void actualCookieAndHeaderCsrfContract() throws Exception {
    var cookie =
        mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    assertThat(cookie.isHttpOnly()).isFalse();
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", cookie.getValue())
                .contentType("application/json")
                .content(body("test-password")))
        .andExpect(status().isOk());
  }
}
