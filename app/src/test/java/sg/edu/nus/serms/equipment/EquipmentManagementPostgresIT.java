package sg.edu.nus.serms.equipment;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import sg.edu.nus.serms.equipment.api.EquipmentManagementController.EquipmentInput;
import sg.edu.nus.serms.equipment.service.EquipmentManagementException;
import sg.edu.nus.serms.equipment.service.EquipmentManagementService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import sg.edu.nus.serms.testsupport.SermsFixtures;

@SpringBootTest(properties = {"serms.notifications.worker-enabled=false", "springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc
class EquipmentManagementPostgresIT {
  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("SERMS_EQUIPMENT_TEST_JDBC_URL");
    String user = System.getenv("SERMS_EQUIPMENT_TEST_DB_USER");
    String password = System.getenv("SERMS_EQUIPMENT_TEST_DB_PASSWORD");
    if (url == null || url.isBlank()) {
      var postgres = new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("serms_test_equipment");
      postgres.start(); url = postgres.getJdbcUrl(); user = postgres.getUsername(); password = postgres.getPassword();
    } else if (!url.matches("jdbc:postgresql://[^/]+/serms_test_equipment[a-z0-9_]*(\\?.*)?")) {
      throw new IllegalStateException("Equipment tests require an isolated serms_test_equipment database");
    }
    String finalUrl = url, finalUser = user, finalPassword = password;
    properties.add("spring.datasource.url", () -> finalUrl);
    properties.add("spring.datasource.username", () -> finalUser);
    properties.add("spring.datasource.password", () -> finalPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired EquipmentManagementService management;
  @Autowired MockMvc mvc;
  SermsFixtures fixtures;
  UUID custodian;
  SermsUserPrincipal operator;

  @BeforeEach void setUp() {
    fixtures = new SermsFixtures(jdbc);
    custodian = fixtures.user("CUSTODIAN");
    operator = principal(custodian, "CUSTODIAN");
    authenticate(operator);
  }

  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  private static SermsUserPrincipal principal(UUID id, String role) {
    return new SermsUserPrincipal(id, id + "@example.invalid", role, Set.of(role));
  }

  private static void authenticate(SermsUserPrincipal principal) {
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.getAuthorities()));
  }

  private EquipmentInput input(String tag, String serial, String category, Long version) {
    return new EquipmentInput(tag, serial, "Managed camera", "Studio camera", category, "Store room",
        "GOOD", false, "PT72H", version);
  }

  @Test void registerUpdateRetireAndCategoryRenameAreAuditedAndSearchCompatible() {
    String category = "Managed-" + UUID.randomUUID();
    management.createCategory(category);
    var created = management.register(input("TAG-" + UUID.randomUUID(), "SER-" + UUID.randomUUID(), category, null));
    assertThat(created.status()).isEqualTo("AVAILABLE");
    assertThat(created.maxBorrowDuration()).isEqualTo("PT72H");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE entity_id=?", Long.class,
        created.equipmentId())).isEqualTo(1);
    String renamed = category + "-new";
    management.renameCategory(category, renamed);
    assertThat(jdbc.queryForObject("SELECT category FROM serms.equipment WHERE equipment_id=?", String.class,
        created.equipmentId())).isEqualTo(renamed);
    var current = management.list(0, 100).stream().filter(e -> e.equipmentId().equals(created.equipmentId()))
        .findFirst().orElseThrow();
    var updated = management.update(created.equipmentId(), input(created.assetTag(), created.serialNumber(), renamed,
        current.version()));
    assertThat(updated.version()).isGreaterThan(current.version());
    var retired = management.retire(created.equipmentId(), updated.version());
    assertThat(retired.status()).isEqualTo("RETIRED");
    assertThatThrownBy(() -> management.update(created.equipmentId(), input(created.assetTag(), null, renamed,
        retired.version()))).isInstanceOf(EquipmentManagementException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE entity_id=?", Long.class,
        created.equipmentId())).isEqualTo(3);
  }

  @Test void duplicateAssetAndSerialReturnFieldConflictsAndRollbackAudit() throws Exception {
    String category = "Managed-" + UUID.randomUUID();
    management.createCategory(category);
    String tag = "TAG-" + UUID.randomUUID(), serial = "SER-" + UUID.randomUUID();
    management.register(input(tag, serial, category, null));
    String body = "{\"assetTag\":\"" + tag + "\",\"serialNumber\":\"other\",\"name\":\"Camera\","
        + "\"category\":\"" + category + "\",\"location\":\"Store\",\"condition\":\"GOOD\"}";
    mvc.perform(post("/api/v1/equipment/manage").with(user(operator)).with(csrf())
        .contentType("application/json").content(body))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].field").value("assetTag"));
    mvc.perform(post("/api/v1/equipment/manage").with(user(operator)).with(csrf())
        .contentType("application/json").content(body.replace(tag, "other-tag").replace("other", serial)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].field").value("serialNumber"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE action='EQUIPMENT_REGISTER' AND actor_id=?",
        Long.class, custodian)).isEqualTo(1);
  }

  @Test void activeLoanBlocksRetirementAndOtherRolesCannotManage() throws Exception {
    String category = "Managed-" + UUID.randomUUID();
    management.createCategory(category);
    var created = management.register(input("TAG-" + UUID.randomUUID(), null, category, null));
    UUID borrower = fixtures.user("BORROWER");
    UUID reservation = fixtures.reservation(borrower, created.equipmentId(),
        java.time.Instant.now().minusSeconds(60), java.time.Instant.now().plusSeconds(3600), "CONFIRMED");
    jdbc.update("INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at,status) "
        + "SELECT ?,reservation_id,?,now(),end_at,'ACTIVE' FROM serms.reservation WHERE reservation_id=?",
        UUID.randomUUID(), custodian, reservation);
    assertThatThrownBy(() -> management.retire(created.equipmentId(), created.version()))
        .isInstanceOf(EquipmentManagementException.class).hasMessageContaining("active loan");
    var borrowerPrincipal = principal(borrower, "BORROWER");
    mvc.perform(get("/api/v1/equipment/manage").with(user(borrowerPrincipal))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/equipment/manage").with(user(borrowerPrincipal)).with(csrf())
        .contentType("application/json").content("{\"assetTag\":\"FORBIDDEN-" + UUID.randomUUID()
            + "\",\"name\":\"Camera\",\"category\":\"" + category
            + "\",\"location\":\"Store\",\"condition\":\"GOOD\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/equipment/manage").with(anonymous())).andExpect(status().isUnauthorized());
  }

  @Test void invalidFieldsAndRevokedRoleAreRejected() throws Exception {
    mvc.perform(post("/api/v1/equipment/manage").with(user(operator)).with(csrf())
        .contentType("application/json").content("{\"assetTag\":\" \"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
    jdbc.update("DELETE FROM serms.user_role WHERE user_id=?", custodian);
    authenticate(operator);
    assertThatThrownBy(() -> management.list(0, 20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test void twoConcurrentRegistrationsOfOneTagHaveOneWinner() throws Exception {
    String category = "Managed-" + UUID.randomUUID();
    management.createCategory(category);
    String tag = "TAG-" + UUID.randomUUID();
    var pool = Executors.newFixedThreadPool(2);
    var barrier = new CyclicBarrier(2);
    Callable<Boolean> attempt = () -> {
      authenticate(operator);
      try {
        barrier.await(10, TimeUnit.SECONDS);
        management.register(input(tag, null, category, null));
        return true;
      } catch (EquipmentManagementException e) {
        assertThat(e.status()).isEqualTo(409);
        return false;
      } finally { SecurityContextHolder.clearContext(); }
    };
    try {
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      assertThat(Set.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.equipment WHERE asset_tag=?", Long.class, tag)).isEqualTo(1);
    } finally { pool.shutdownNow(); }
  }

  @Test void openApiContainsManagementOperations() throws Exception {
    String json = mvc.perform(get("/api/v1/openapi.json").with(user(operator)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(json).contains("/api/v1/equipment/manage", "registerEquipment", "retireEquipment");
    java.nio.file.Files.writeString(java.nio.file.Path.of("target", "equipment-openapi.json"), json);
  }
}
