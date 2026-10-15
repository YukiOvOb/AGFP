package sg.edu.nus.serms.identity.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sg.edu.nus.serms.audit.api.AuditQueryController;
import sg.edu.nus.serms.audit.domain.AuditEntry;
import sg.edu.nus.serms.audit.repository.AuditQueryRepository;
import sg.edu.nus.serms.audit.service.AuditQueryService;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;
import sg.edu.nus.serms.identity.service.*;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.security.*;

@ActiveProfiles("test")
@WebMvcTest(controllers = {AdminUserController.class, AuditQueryController.class, AuthController.class})
@Import({SecurityConfig.class, IdentityAuthenticationProvider.class, SecurityProblemWriter.class,
    AdminUserService.class, AdminAccess.class, AuditQueryService.class})
class AdminConsoleApiTest {
  @Autowired MockMvc mvc;
  @Autowired AdminUserService users;
  @MockitoBean AdminUserRepository accounts;
  @MockitoBean AuditQueryRepository audit;
  @MockitoBean IdentityService identity;
  final UUID actor = UUID.randomUUID();
  final String body = """
      {"email":"person@example.test","displayName":"Person","initialPassword":"correct horse battery","roles":["BORROWER"]}
      """;

  RequestPostProcessor as(String... roles) {
    return user(new SermsUserPrincipal(actor, "admin@example.test", "Admin", Set.of(roles)));
  }

  @BeforeEach void setup() {
    when(accounts.isActiveAdministrator(actor)).thenReturn(true);
    when(accounts.list(anyString(), anyInt(), anyInt())).thenReturn(new AdminUser.Page(List.of(), 0, 0, 20));
    when(audit.list(any(), anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AuditEntry.Page(List.of(), 0, 0, 20));
  }

  @Test void anonymousReadsAndWritesAre401WithValidCsrf() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/admin/users").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(accounts, audit);
  }

  @ParameterizedTest @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void eachNonAdminRoleCannotListOrCreateOrReadAudit(String role) throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/audit").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/admin/users").with(as(role)).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isForbidden());
    verifyNoInteractions(accounts, audit);
  }

  @Test void adminCanListCreateAndReadAuditWithoutCredentialLeaks() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(as("ADMIN"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
    mvc.perform(get("/api/v1/admin/audit").with(as("ADMIN"))).andExpect(status().isOk());
    String response = mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("BORROWER")).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain("password", "correct horse battery", "{bcrypt}");
    assertThat(new AdminUserController.CreateUserRequest("e", "n", "secret", Set.of()).toString())
        .doesNotContain("secret");
  }

  @Test void missingAndInvalidCsrfCannotCreate() throws Exception {
    for (var request : List.of(post("/api/v1/admin/users").with(as("ADMIN")),
        post("/api/v1/admin/users").with(as("ADMIN")).with(csrf().useInvalidToken())))
      mvc.perform(request.contentType("application/json").content(body)).andExpect(status().isForbidden())
          .andExpect(jsonPath("$.type").value("/problems/csrf"));
    verifyNoInteractions(accounts);
  }

  @ParameterizedTest @ValueSource(strings = {
      "{\"email\":\"not-an-email\",\"displayName\":\"P\",\"initialPassword\":\"correct horse battery\",\"roles\":[\"BORROWER\"]}",
      "{\"email\":\"a@example.test\",\"displayName\":\" \",\"initialPassword\":\"correct horse battery\",\"roles\":[\"BORROWER\"]}",
      "{\"email\":\"a@example.test\",\"displayName\":\"P\",\"initialPassword\":\"short\",\"roles\":[\"BORROWER\"]}",
      "{\"email\":\"a@example.test\",\"displayName\":\"P\",\"initialPassword\":\"correct horse battery\",\"roles\":[]}",
      "{\"email\":\"a@example.test\",\"displayName\":\"P\",\"initialPassword\":\"correct horse battery\",\"roles\":[\"SUPERUSER\"]}"})
  void invalidInputsAre400WithoutWrites(String invalid) throws Exception {
    mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(csrf())
        .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
    verify(accounts, never()).create(any(), any(), any(), any(), any());
  }

  @Test void duplicateEmailIsSafe409() throws Exception {
    doThrow(new EmailAlreadyUsedException()).when(accounts).create(any(), any(), any(), any(), any());
    mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/problems/email-in-use"));
    verify(accounts, never()).auditCreation(any(), any());
  }

  @ParameterizedTest @ValueSource(strings = {
      "/api/v1/admin/users?page=-1", "/api/v1/admin/users?size=101",
      "/api/v1/admin/audit?actorId=bad", "/api/v1/admin/audit?from=bad",
      "/api/v1/admin/audit?from=2026-10-15T00:00:00Z&to=2026-10-14T00:00:00Z"})
  void invalidPagingAndAuditFiltersAre400(String url) throws Exception {
    mvc.perform(get(url).with(as("ADMIN"))).andExpect(status().isBadRequest());
  }

  @Test void staleAdminSessionHasNoAdministrativeAccessAfterDatabaseDemotion() throws Exception {
    when(accounts.isActiveAdministrator(actor)).thenReturn(false);
    mvc.perform(get("/api/v1/admin/users").with(as("ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/audit").with(as("ADMIN"))).andExpect(status().isForbidden());
  }

  @Test void directServiceCallsWithoutAnAdminAuthorityAreDenied() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> users.list(actor, "", 0, 20))
        .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
  }
}
