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
  final UUID target = UUID.randomUUID();
  final String renameBody = "{\"displayName\":\"Renamed\",\"expectedDisplayName\":\"Original\"}";

  String renameUrl() { return "/api/v1/admin/users/" + target + "/display-name"; }
  final String body = """
      {"email":"person@example.test","displayName":"Person","initialPassword":"correct horse battery","roles":["BORROWER"]}
      """;

  RequestPostProcessor as(String... roles) {
    return user(new SermsUserPrincipal(actor, "admin@example.test", "Admin", Set.of(roles)));
  }

  RequestPostProcessor cookieCsrf(boolean valid) throws Exception {
    var cookie = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
        .andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return request -> {
      request.setCookies(cookie);
      request.addHeader("X-XSRF-TOKEN", valid ? cookie.getValue() : "invalid-token");
      return request;
    };
  }

  @BeforeEach void setup() {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(true);
    when(accounts.isActiveAdministrator(actor)).thenReturn(true);
    when(accounts.list(anyString(), anyInt(), anyInt())).thenReturn(new AdminUser.Page(List.of(), 0, 0, 20));
    when(audit.list(any(), anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new AuditEntry.Page(List.of(), 0, 0, 20));
  }

  @ParameterizedTest @ValueSource(strings={"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void securityMutationsDenyEveryNonAdministratorBeforeLookup(String role) throws Exception {
    for (String operation : List.of("status", "roles", "password-reset")) {
      var request = operation.equals("password-reset") ? post("/api/v1/admin/users/"+target+"/"+operation)
          : patch("/api/v1/admin/users/"+target+"/"+operation);
      mvc.perform(request.with(as(role)).with(cookieCsrf(true)).contentType("application/json")
          .content(securityBody(operation))).andExpect(status().isForbidden());
    }
    verifyNoInteractions(accounts);
  }

  String securityBody(String operation) {
    return switch(operation) {
      case "status" -> "{\"active\":false,\"expectedVersion\":0}";
      case "roles" -> "{\"roles\":[\"APPROVER\"],\"expectedVersion\":0}";
      default -> "{\"newPassword\":\"replacement-test-password\",\"expectedVersion\":0}";
    };
  }

  @ParameterizedTest @ValueSource(strings={"status", "roles", "password-reset"})
  void securityMutationsRequireAuthenticationCsrfAndValidVersion(String operation) throws Exception {
    String path="/api/v1/admin/users/"+target+"/"+operation;
    java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> request =
        () -> operation.equals("password-reset") ? post(path) : patch(path);
    mvc.perform(request.get().with(cookieCsrf(true)).contentType("application/json").content(securityBody(operation)))
        .andExpect(status().isUnauthorized());
    mvc.perform(request.get().with(as("ADMIN")).contentType("application/json").content(securityBody(operation)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/csrf"));
    for (String version : List.of("-1", "null", "\"0\"", "0.5"))
      mvc.perform(request.get().with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json")
          .content(securityBody(operation).replace("\"expectedVersion\":0", "\"expectedVersion\":"+version)))
          .andExpect(status().isBadRequest());
    verifyNoInteractions(accounts);
  }

  @Test void securityMutationsReturnSafeSummaryAndConflictProblems() throws Exception {
    when(accounts.lockForDisplayNameUpdate(target)).thenReturn(
        new AdminUser(target, "p@example.test", "P", "ACTIVE", Set.of(AdminUser.Role.BORROWER)));
    mvc.perform(patch("/api/v1/admin/users/"+target+"/roles").with(as("ADMIN")).with(cookieCsrf(true))
        .contentType("application/json").content(securityBody("roles"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.roles[0]").value("APPROVER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    when(accounts.lockForDisplayNameUpdate(target)).thenReturn(
        new AdminUser(target, "p@example.test", "P", "ACTIVE", Set.of(AdminUser.Role.APPROVER), 1));
    mvc.perform(post("/api/v1/admin/users/"+target+"/password-reset").with(as("ADMIN")).with(cookieCsrf(true))
        .contentType("application/json").content(securityBody("password-reset")))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/account-version-conflict"));
  }

  @Test void versionMismatchInvalidatesSessionBeforeAdminPermissionChecks() throws Exception {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(false);
    mvc.perform(get("/api/v1/admin/users").with(as("ADMIN"))).andExpect(status().isUnauthorized());
    verifyNoInteractions(accounts);
  }

  @Test void anonymousReadsAndWritesAre401WithValidCsrf() throws Exception {
    mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/admin/users").with(cookieCsrf(true)).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch(renameUrl()).with(cookieCsrf(true)).contentType("application/json").content(renameBody))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(accounts, audit);
  }

  @ParameterizedTest @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void eachNonAdminRoleCannotListOrCreateOrReadAudit(String role) throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/audit").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/admin/users").with(as(role)).with(cookieCsrf(true))
        .contentType("application/json").content(body)).andExpect(status().isForbidden());
    mvc.perform(patch(renameUrl()).with(as(role)).with(cookieCsrf(true))
        .contentType("application/json").content(renameBody)).andExpect(status().isForbidden());
    verifyNoInteractions(accounts, audit);
  }

  @Test void adminCanListCreateAndReadAuditWithoutCredentialLeaks() throws Exception {
    mvc.perform(get("/api/v1/admin/users").with(as("ADMIN"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
    mvc.perform(get("/api/v1/admin/audit").with(as("ADMIN"))).andExpect(status().isOk());
    String response = mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(cookieCsrf(true))
        .contentType("application/json").content(body)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("BORROWER")).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain("password", "correct horse battery", "{bcrypt}");
    assertThat(new AdminUserController.CreateUserRequest("e", "n", "secret", Set.of()).toString())
        .doesNotContain("secret");
  }

  @Test void missingAndInvalidCsrfCannotCreate() throws Exception {
    for (var request : List.of(post("/api/v1/admin/users").with(as("ADMIN")),
        post("/api/v1/admin/users").with(as("ADMIN")).with(cookieCsrf(false))))
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
    mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(cookieCsrf(true))
        .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
    verify(accounts, never()).create(any(), any(), any(), any(), any());
  }

  @Test void duplicateEmailIsSafe409() throws Exception {
    doThrow(new EmailAlreadyUsedException()).when(accounts).create(any(), any(), any(), any(), any());
    mvc.perform(post("/api/v1/admin/users").with(as("ADMIN")).with(cookieCsrf(true))
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
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json").content(renameBody))
        .andExpect(status().isForbidden());
  }

  @Test void directServiceCallsWithoutAnAdminAuthorityAreDenied() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> users.list(actor, "", 0, 20))
        .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
  }

  @Test void adminUpdatesOnlyTheDisplayNameWithRealCsrfCookieAndSafeResponse() throws Exception {
    var cookie = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    when(accounts.lockForDisplayNameUpdate(target)).thenReturn(
        new AdminUser(target, "target@example.test", "Original", "DISABLED", Set.of(AdminUser.Role.BORROWER)));
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
        .contentType("application/json").content(renameBody)).andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Renamed"))
        .andExpect(jsonPath("$.accountStatus").value("DISABLED"))
        .andExpect(jsonPath("$.roles[0]").value("BORROWER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    verify(accounts).updateDisplayName(target, "Original", "Renamed");
    verify(accounts).auditDisplayNameChange(actor, target);
  }

  @Test void missingAndInvalidCsrfCannotRename() throws Exception {
    for (var request : List.of(patch(renameUrl()).with(as("ADMIN")),
        patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(false))))
      mvc.perform(request.contentType("application/json").content(renameBody))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/csrf"));
    verifyNoInteractions(accounts);
  }

  @ParameterizedTest @ValueSource(strings = {
      "{}", "{\"displayName\":\"New\"}", "{\"displayName\":\" \",\"expectedDisplayName\":\"Old\"}",
      "{\"displayName\":\"New\",\"expectedDisplayName\":null}",
      "{\"displayName\":\"New\",\"expectedDisplayName\":\" \"}"})
  void invalidRenameInputsAre400(String invalid) throws Exception {
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json").content(invalid))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"));
    verifyNoInteractions(accounts);
  }

  @Test void unknownTargetAndStaleNameHaveDistinctSafeProblems() throws Exception {
    when(accounts.lockForDisplayNameUpdate(target)).thenThrow(new sg.edu.nus.serms.shared.domain.RecordNotFoundException());
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json").content(renameBody))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value("/problems/not-found"));
    doReturn(new AdminUser(target, "p@example.test", "Another name", "ACTIVE", Set.of(AdminUser.Role.ADMIN)))
        .when(accounts).lockForDisplayNameUpdate(target);
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json").content(renameBody))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/display-name-conflict"));
    verify(accounts, never()).updateDisplayName(any(), any(), any());
    verify(accounts, never()).auditDisplayNameChange(any(), any());
  }

  @Test void oversizedNameAndMalformedTargetAre400() throws Exception {
    mvc.perform(patch(renameUrl()).with(as("ADMIN")).with(cookieCsrf(true)).contentType("application/json")
        .content("{\"displayName\":\"" + "x".repeat(201) + "\",\"expectedDisplayName\":\"Old\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/v1/admin/users/invalid/display-name").with(as("ADMIN")).with(cookieCsrf(true))
        .contentType("application/json").content(renameBody)).andExpect(status().isBadRequest());
    verifyNoInteractions(accounts);
  }
}
