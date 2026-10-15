package sg.edu.nus.serms.maintenance.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import sg.edu.nus.serms.identity.api.AuthController;
import sg.edu.nus.serms.identity.service.IdentityService;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;
import sg.edu.nus.serms.maintenance.service.*;
import sg.edu.nus.serms.maintenance.service.security.MaintenanceAccessPolicy;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.security.*;

@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(controllers = {MaintenanceController.class, AuthController.class},
    properties = "serms.security.secure-cookies=false")
@Import({SecurityConfig.class, IdentityAuthenticationProvider.class, SecurityProblemWriter.class,
    MaintenanceService.class, MaintenanceReportService.class, MaintenanceAccessPolicy.class,
    MaintenanceApiTest.Beans.class})
class MaintenanceApiTest {
  static final String ROOT = "/api/v1/maintenance";
  static final UUID ME = new UUID(0, 1), OTHER = new UUID(0, 2), CASE = new UUID(0, 3), EQUIPMENT = new UUID(0, 4);
  static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

  @ParameterizedTest
  @ValueSource(strings = {"CUSTODIAN", "ADMIN"})
  void operatorCanListTechnicianProjection(String role) throws Exception {
    when(technicians.findActiveTechnicians()).thenReturn(
        List.of(new MaintenanceTechnician(OTHER, "tech@example.com", "Technician")));
    mvc.perform(get(ROOT + "/technicians").with(as(role))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(OTHER.toString()))
        .andExpect(jsonPath("$[0].email").value("tech@example.com"))
        .andExpect(jsonPath("$[0].displayName").value("Technician"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "MAINTAINER"})
  void technicianDirectoryDeniedToNonOperators(String role) throws Exception {
    mvc.perform(get(ROOT + "/technicians").with(as(role))).andExpect(status().isForbidden());
    verifyNoInteractions(technicians);
  }

  @Test
  void technicianDirectoryRequiresAuthentication() throws Exception {
    mvc.perform(get(ROOT + "/technicians")).andExpect(status().isUnauthorized());
    verifyNoInteractions(technicians);
  }
  @Autowired MockMvc mvc;
  @MockitoBean IdentityService identity;
  @MockitoBean MaintenanceCaseRepository cases;
  @MockitoBean MaintenanceHistoryRepository history;
  @MockitoBean MaintenanceReportRepository reports;
  @MockitoBean EquipmentMaintenancePort equipment;
  @MockitoBean TechnicianDirectoryPort technicians;
  @MockitoBean MaintenanceLoanPort loans;
  @MockitoBean MaintenanceEventPublisher events;
  @MockitoBean MaintenanceNameDirectory names;

  @TestConfiguration
  static class Beans {
    @Bean("maintenanceClock") Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    @Bean("maintenanceIdentifiers") java.util.function.Supplier<UUID> identifiers() { return () -> CASE; }
  }

  @BeforeEach
  void setup() {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(true);
    when(cases.insert(any())).thenAnswer(call -> call.<MaintenanceCase>getArgument(0).copy());
    when(cases.update(any())).thenAnswer(call -> call.<MaintenanceCase>getArgument(0).copy());
    when(cases.findById(CASE)).thenReturn(Optional.of(item(ME, MaintenanceStatus.IN_PROGRESS)));
    when(reports.countCasesByStatus()).thenReturn(Map.of(MaintenanceStatus.OPEN, 2L));
    when(reports.averageCompletionDurationBetween(any(), any())).thenReturn(Optional.empty());
    when(names.findEquipmentLabels(any())).thenReturn(
        Map.of(EQUIPMENT, new MaintenanceEquipmentLabel("Field Camera", "CAM-001")));
    when(names.findUserDisplayNames(any())).thenReturn(Map.of(ME, "Mia Tech", OTHER, "Omar Reporter"));
  }

  MaintenanceCase item(UUID technician, MaintenanceStatus status) {
    var item = new MaintenanceCase(CASE, EQUIPMENT, OTHER, null, "Fault", NOW);
    if (status != MaintenanceStatus.OPEN) item.assign(technician);
    if (status == MaintenanceStatus.IN_PROGRESS) item.start();
    return item;
  }

  RequestPostProcessor as(String... roles) {
    return user(new SermsUserPrincipal(ME, "me@example.com", "Me", Set.of(roles)));
  }

  RequestPostProcessor realCsrf() throws Exception {
    var token = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    return request -> {
      request.setCookies(token);
      request.addHeader("X-XSRF-TOKEN", token.getValue());
      return request;
    };
  }

  MockHttpServletRequestBuilder mutation(String path, String body, String... roles) throws Exception {
    return post(ROOT + path).with(as(roles)).with(realCsrf()).contentType("application/json").content(body);
  }

  String fault() { return "{\"equipmentId\":\"" + EQUIPMENT + "\",\"faultDescription\":\"Broken\"}"; }
  String assignment() { return "{\"technicianId\":\"" + OTHER + "\"}"; }

  @Test
  void unauthenticatedReadAndCsrfProtectedMutationUseSharedProblems() throws Exception {
    mvc.perform(get(ROOT + "/cases/open")).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    mvc.perform(post(ROOT + "/cases").with(as("BORROWER")).contentType("application/json").content(fault()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/csrf"));
    verifyNoInteractions(cases, history, equipment, events);
  }

  @Test
  void realCookieCsrfAndSessionAuthenticationCanReport() throws Exception {
    var token = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    mvc.perform(post(ROOT + "/cases").with(as("BORROWER")).cookie(token)
        .header("X-XSRF-TOKEN", token.getValue()).contentType("application/json").content(fault()))
        .andExpect(status().isCreated()).andExpect(header().string("Location", ROOT + "/cases/" + CASE))
        .andExpect(jsonPath("$.version").value(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "CUSTODIAN"})
  void reportsBindReporterToAuthenticatedPrincipal(String role) throws Exception {
    String body = fault().substring(0, fault().length() - 1)
        + ",\"reportedBy\":\"" + OTHER + "\",\"actorId\":\"" + OTHER + "\"}";
    mvc.perform(mutation("/cases", body, role)).andExpect(status().isCreated())
        .andExpect(jsonPath("$.reportedBy").value(ME.toString()))
        .andExpect(jsonPath("$.loanId").isEmpty()).andExpect(jsonPath("$.assignedTo").isEmpty());
    verify(cases).insert(argThat(item -> item.reportedBy().equals(ME)));
    verify(history).append(argThat(entry -> entry.actorId().equals(ME)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAINTAINER", "APPROVER", "ADMIN"})
  void reportingRequiresExplicitBorrowerOrCustodianRole(String role) throws Exception {
    mvc.perform(mutation("/cases", fault(), role)).andExpect(status().isForbidden());
    verifyNoInteractions(cases, equipment, history, events);
  }

  @Test
  void reportAcceptsFaultCategoryAndPriorityAndDefaultsMissingOnes() throws Exception {
    String body = fault().substring(0, fault().length() - 1)
        + ",\"faultCategory\":\"ELECTRICAL\",\"priority\":\"URGENT\"}";
    mvc.perform(mutation("/cases", body, "BORROWER")).andExpect(status().isCreated())
        .andExpect(jsonPath("$.faultCategory").value("ELECTRICAL"))
        .andExpect(jsonPath("$.priority").value("URGENT"));
    verify(cases).insert(argThat(item -> item.faultCategory() == FaultCategory.ELECTRICAL
        && item.priority() == MaintenancePriority.URGENT));
    mvc.perform(mutation("/cases", fault(), "BORROWER")).andExpect(status().isCreated())
        .andExpect(jsonPath("$.faultCategory").value("OTHER"))
        .andExpect(jsonPath("$.priority").value("MEDIUM"));
  }

  @Test
  void unknownFaultCategoryOrPriorityIsAValidationProblem() throws Exception {
    for (String extra : new String[] {",\"faultCategory\":\"FIRE\"}", ",\"priority\":\"CRITICAL\"}"}) {
      String body = fault().substring(0, fault().length() - 1) + extra;
      mvc.perform(mutation("/cases", body, "BORROWER")).andExpect(status().isBadRequest());
    }
    verify(cases, never()).insert(any());
  }

  @Test
  void adminWithSeparatelyHeldBorrowerRoleCanReportWithLoanId() throws Exception {
    String body = fault().substring(0, fault().length() - 1) + ",\"loanId\":\"" + OTHER + "\"}";
    mvc.perform(mutation("/cases", body, "ADMIN", "BORROWER")).andExpect(status().isCreated())
        .andExpect(jsonPath("$.loanId").value(OTHER.toString()))
        .andExpect(header().string("Location", ROOT + "/cases/" + CASE));
    verify(loans).ensureLoanMatchesEquipment(OTHER, EQUIPMENT);
  }

  @Test
  void invalidLoanAssociationIsSanitizedValidationProblem() throws Exception {
    doThrow(new InvalidMaintenanceLoanException(OTHER, EQUIPMENT)).when(loans)
        .ensureLoanMatchesEquipment(OTHER, EQUIPMENT);
    String body = fault().substring(0, fault().length() - 1) + ",\"loanId\":\"" + OTHER + "\"}";
    String problem = mvc.perform(mutation("/cases", body, "BORROWER"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("/problems/validation"))
        .andExpect(jsonPath("$.detail").value("The selected loan is not associated with this equipment."))
        .andExpect(jsonPath("$.errors").isArray()).andExpect(jsonPath("$.errors").isEmpty())
        .andReturn().getResponse().getContentAsString();
    assertThat(problem).doesNotContain(OTHER.toString(), EQUIPMENT.toString(), "SQL", "Exception", "sg.edu");
    verify(equipment, never()).markUnderMaintenance(any());
    verify(cases, never()).insert(any());
    verifyNoInteractions(history, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"outage", "unexpected-integrity"})
  void unrelatedDatabaseErrorsStillUseShared503Fallback(String kind) throws Exception {
    org.springframework.dao.DataAccessException failure = kind.equals("outage")
        ? new org.springframework.dao.DataAccessResourceFailureException("SQL secret database unavailable")
        : new org.springframework.dao.DataIntegrityViolationException("SQL secret unrelated integrity constraint");
    doThrow(failure).when(loans).ensureLoanMatchesEquipment(OTHER, EQUIPMENT);
    String body = fault().substring(0, fault().length() - 1) + ",\"loanId\":\"" + OTHER + "\"}";
    String problem = mvc.perform(mutation("/cases", body, "BORROWER"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("/problems/unavailable"))
        .andReturn().getResponse().getContentAsString();
    assertThat(problem).doesNotContain("SQL", "secret", "Exception", "sg.edu");
    verify(equipment, never()).markUnderMaintenance(any());
    verify(cases, never()).insert(any());
    verifyNoInteractions(history, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTODIAN", "ADMIN"})
  void assignmentUsesPrincipalActorUnderProvisionalPolicy(String role) throws Exception {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(null, MaintenanceStatus.OPEN)));
    String body = assignment().substring(0, assignment().length() - 1) + ",\"actorId\":\"" + OTHER + "\"}";
    mvc.perform(mutation("/cases/" + CASE + "/assignment", body, role)).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"));
    verify(events).publish(argThat((MaintenanceAssigned e) -> e.actorId().equals(ME) && e.technicianId().equals(OTHER)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "MAINTAINER"})
  void assignmentDeniedForOtherRoles(String role) throws Exception {
    mvc.perform(mutation("/cases/" + CASE + "/assignment", assignment(), role)).andExpect(status().isForbidden());
    verifyNoInteractions(equipment, history, events);
  }

  @Test
  void myCasesNeverUsesClientTechnicianId() throws Exception {
    when(cases.findByAssignedTo(ME)).thenReturn(List.of(item(ME, MaintenanceStatus.IN_PROGRESS)));
    mvc.perform(get(ROOT + "/cases/assigned").param("technicianId", OTHER.toString()).with(as("MAINTAINER")))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].assignedTo").value(ME.toString()));
    verify(cases).findByAssignedTo(ME);
    verify(cases, never()).findByAssignedTo(OTHER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "CUSTODIAN", "APPROVER", "ADMIN"})
  void myCasesRequiresMaintainer(String role) throws Exception {
    mvc.perform(get(ROOT + "/cases/assigned").with(as(role))).andExpect(status().isForbidden());
  }

  String workBody(String path) {
    return path.equals("start") ? "" : Set.of("resolve", "unrepairable").contains(path)
        ? "{\"resolutionNote\":\"Finished\"}" : "{\"detail\":\"Work detail\"}";
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair-actions", "notes", "resolve", "unrepairable"})
  void assignedMaintainerCanPerformEveryTechnicianAction(String path) throws Exception {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(ME,
        path.equals("start") ? MaintenanceStatus.ASSIGNED : MaintenanceStatus.IN_PROGRESS)));
    mvc.perform(mutation("/cases/" + CASE + "/" + path, workBody(path), "MAINTAINER"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.assignedTo").value(ME.toString()));
    verify(history).append(argThat(entry -> entry.actorId().equals(ME)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair-actions", "notes", "resolve", "unrepairable"})
  void unrelatedMaintainerCannotWriteOrPublish(String path) throws Exception {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(OTHER,
        path.equals("start") ? MaintenanceStatus.ASSIGNED : MaintenanceStatus.IN_PROGRESS)));
    mvc.perform(mutation("/cases/" + CASE + "/" + path, workBody(path), "MAINTAINER"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/forbidden"));
    verify(cases, never()).update(any());
    verifyNoInteractions(history, events);
    verify(equipment).lockForMaintenance(EQUIPMENT);
    verifyNoMoreInteractions(equipment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTODIAN", "ADMIN", "MAINTAINER"})
  void permittedCaseReaderReceivesDtoAndHistory(String role) throws Exception {
    when(history.findByCaseId(CASE)).thenReturn(List.of(new MaintenanceHistoryEntry(CASE, EQUIPMENT,
        MaintenanceHistoryAction.START, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS, ME, NOW, null)));
    mvc.perform(get(ROOT + "/cases/" + CASE).with(as(role))).andExpect(status().isOk())
        .andExpect(jsonPath("$.maintenanceCaseId").value(CASE.toString()))
        .andExpect(jsonPath("$.equipmentId").value(EQUIPMENT.toString()))
        .andExpect(jsonPath("$.equipmentName").value("Field Camera"))
        .andExpect(jsonPath("$.equipmentAssetTag").value("CAM-001"))
        .andExpect(jsonPath("$.reportedByName").value("Omar Reporter"))
        .andExpect(jsonPath("$.assignedToName").value("Mia Tech"))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(get(ROOT + "/cases/" + CASE + "/history").with(as(role))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].actorId").value(ME.toString()))
        .andExpect(jsonPath("$[0].actorName").value("Mia Tech"));
  }

  @Test
  void listResolvesNamesInOneBatchPerKindAndLeavesUnknownOrUnassignedNamesNull() throws Exception {
    UUID unknownEquipment = new UUID(0, 9);
    var open = item(null, MaintenanceStatus.OPEN);
    var other = new MaintenanceCase(new UUID(0, 8), unknownEquipment, ME, null, "Other fault", NOW);
    when(cases.findOpenCases()).thenReturn(List.of(open, other));
    mvc.perform(get(ROOT + "/cases/open").with(as("CUSTODIAN"))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].equipmentName").value("Field Camera"))
        .andExpect(jsonPath("$[0].reportedByName").value("Omar Reporter"))
        .andExpect(jsonPath("$[0].assignedTo").isEmpty())
        .andExpect(jsonPath("$[0].assignedToName").isEmpty())
        .andExpect(jsonPath("$[1].equipmentId").value(unknownEquipment.toString()))
        .andExpect(jsonPath("$[1].equipmentName").isEmpty())
        .andExpect(jsonPath("$[1].equipmentAssetTag").isEmpty())
        .andExpect(jsonPath("$[1].reportedByName").value("Mia Tech"));
    verify(names).findEquipmentLabels(Set.of(EQUIPMENT, unknownEquipment));
    verify(names).findUserDisplayNames(Set.of(OTHER, ME));
    verifyNoMoreInteractions(names);
  }

  @Test
  void mutationResponseCarriesNamesForTheUpdatedCase() throws Exception {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(null, MaintenanceStatus.OPEN)));
    when(names.findUserDisplayNames(any())).thenReturn(Map.of(OTHER, "Omar Reporter"));
    mvc.perform(mutation("/cases/" + CASE + "/assignment", assignment(), "CUSTODIAN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedTo").value(OTHER.toString()))
        .andExpect(jsonPath("$.assignedToName").value("Omar Reporter"))
        .andExpect(jsonPath("$.equipmentName").value("Field Camera"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "unrelated"})
  void caseDetailsAndHistoryRejectUnprivilegedReaders(String role) throws Exception {
    if (role.equals("unrelated")) when(cases.findById(CASE)).thenReturn(Optional.of(item(OTHER, MaintenanceStatus.IN_PROGRESS)));
    String actual = role.equals("unrelated") ? "MAINTAINER" : role;
    mvc.perform(get(ROOT + "/cases/" + CASE).with(as(actual))).andExpect(status().isForbidden());
    mvc.perform(get(ROOT + "/cases/" + CASE + "/history").with(as(actual))).andExpect(status().isForbidden());
    verifyNoInteractions(history, names);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTODIAN", "ADMIN"})
  void operatorQueriesAreAvailableUnderProvisionalPolicy(String role) throws Exception {
    mvc.perform(get(ROOT + "/cases/open").with(as(role))).andExpect(status().isOk());
    mvc.perform(get(ROOT + "/equipment/" + EQUIPMENT + "/cases").with(as(role))).andExpect(status().isOk());
    mvc.perform(get(ROOT + "/equipment/" + EQUIPMENT + "/history").with(as(role))).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "MAINTAINER"})
  void operatorQueriesRejectOtherRoles(String role) throws Exception {
    mvc.perform(get(ROOT + "/cases/open").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(get(ROOT + "/equipment/" + EQUIPMENT + "/cases").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(get(ROOT + "/equipment/" + EQUIPMENT + "/history").with(as(role))).andExpect(status().isForbidden());
    verifyNoInteractions(names);
  }

  MockHttpServletRequestBuilder report(String role) {
    return get(ROOT + "/report").param("from", NOW.toString()).param("to", NOW.plusSeconds(60).toString()).with(as(role));
  }

  @Test
  void adminReportUsesIsoInstantsAndNullableIsoDuration() throws Exception {
    mvc.perform(report("ADMIN")).andExpect(status().isOk())
        .andExpect(jsonPath("$.fromInclusive").value(NOW.toString()))
        .andExpect(jsonPath("$.casesByStatus.OPEN").value(2))
        .andExpect(jsonPath("$.averageCompletionDuration").isEmpty());
    when(reports.countCompletedBetween(any(), any())).thenReturn(1L);
    when(reports.averageCompletionDurationBetween(any(), any())).thenReturn(Optional.of(Duration.ofNanos(3_561_728_500L)));
    mvc.perform(report("ADMIN")).andExpect(status().isOk())
        .andExpect(jsonPath("$.averageCompletionDuration").value("PT3.5617285S"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void reportDeniedForNonAdmin(String role) throws Exception {
    mvc.perform(report(role)).andExpect(status().isForbidden());
    verifyNoInteractions(reports);
  }

  @Test
  void reportRequiresValidOrderedInstantBounds() throws Exception {
    mvc.perform(get(ROOT + "/report").param("from", NOW.toString()).with(as("ADMIN")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"));
    mvc.perform(get(ROOT + "/report").param("from", "bad").param("to", NOW.toString()).with(as("ADMIN")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(ROOT + "/report").param("from", NOW.toString()).param("to", NOW.toString()).with(as("ADMIN")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"));
  }

  @Test
  void reportingValidationRejectsBlankDescriptionAndMissingEquipment() throws Exception {
    mvc.perform(mutation("/cases", "{\"equipmentId\":\"" + EQUIPMENT + "\",\"faultDescription\":\" \"}", "BORROWER"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"));
    mvc.perform(mutation("/cases", "{\"faultDescription\":\"Fault\"}", "BORROWER")).andExpect(status().isBadRequest());
    verifyNoInteractions(equipment, history);
  }

  @ParameterizedTest
  @ValueSource(strings = {"diagnosis", "repair-actions", "notes", "resolve", "unrepairable"})
  void blankWorkInputIsValidationProblem(String path) throws Exception {
    String body = Set.of("resolve", "unrepairable").contains(path)
        ? "{\"resolutionNote\":\" \"}" : "{\"detail\":\" \"}";
    mvc.perform(mutation("/cases/" + CASE + "/" + path, body, "MAINTAINER"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"));
    verifyNoInteractions(equipment, history, events);
  }

  @Test
  void malformedUuidAndMissingTechnicianAreValidationProblems() throws Exception {
    mvc.perform(get(ROOT + "/cases/not-a-uuid").with(as("CUSTODIAN"))).andExpect(status().isBadRequest());
    mvc.perform(mutation("/cases/" + CASE + "/assignment", "{}", "CUSTODIAN")).andExpect(status().isBadRequest());
    mvc.perform(mutation("/cases", "{\"equipmentId\":\"bad\",\"faultDescription\":\"Fault\"}", "BORROWER"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-case", "transition", "work-state", "version", "technician", "missing-equipment", "retired", "unexpected"})
  void businessErrorsAreScopedSanitizedProblems(String kind) throws Exception {
    MockHttpServletRequestBuilder request;
    int status;
    String type;
    switch (kind) {
      case "missing-case" -> {
        when(cases.findById(CASE)).thenReturn(Optional.empty());
        request = get(ROOT + "/cases/" + CASE).with(as("CUSTODIAN"));
        status = 404; type = "not-found";
      }
      case "transition" -> {
        request = mutation("/cases/" + CASE + "/start", "", "MAINTAINER");
        status = 409; type = "state-conflict";
      }
      case "work-state" -> {
        when(cases.findById(CASE)).thenReturn(Optional.of(item(ME, MaintenanceStatus.ASSIGNED)));
        request = mutation("/cases/" + CASE + "/notes", workBody("notes"), "MAINTAINER");
        status = 409; type = "state-conflict";
      }
      case "version" -> {
        when(cases.findById(CASE)).thenReturn(Optional.of(item(ME, MaintenanceStatus.ASSIGNED)));
        doThrow(new MaintenanceVersionConflictException(CASE, 0)).when(cases).update(any());
        request = mutation("/cases/" + CASE + "/start", "", "MAINTAINER");
        status = 409; type = "version-conflict";
      }
      case "technician" -> {
        when(cases.findById(CASE)).thenReturn(Optional.of(item(null, MaintenanceStatus.OPEN)));
        doThrow(new InvalidMaintenanceTechnicianException(OTHER)).when(technicians).ensureTechnician(OTHER);
        request = mutation("/cases/" + CASE + "/assignment", assignment(), "CUSTODIAN");
        status = 400; type = "validation";
      }
      case "missing-equipment" -> {
        doThrow(new MaintenanceEquipmentNotFoundException(EQUIPMENT)).when(equipment).ensureEquipmentExists(EQUIPMENT);
        request = mutation("/cases", fault(), "BORROWER");
        status = 404; type = "not-found";
      }
      case "retired" -> {
        doThrow(new MaintenanceStateConflictException("SQL secret internal.class stack trace"))
            .when(equipment).markUnderMaintenance(EQUIPMENT);
        request = mutation("/cases", fault(), "BORROWER");
        status = 409; type = "state-conflict";
      }
      case "unexpected" -> {
        doThrow(new IllegalStateException("SQL secret internal.class stack trace"))
            .when(equipment).ensureEquipmentExists(EQUIPMENT);
        request = mutation("/cases", fault(), "BORROWER");
        status = 500; type = "internal";
      }
      default -> throw new AssertionError(kind);
    }
    String body = mvc.perform(request).andExpect(status().is(status))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.type").value("/problems/" + type)).andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("SQL", "stack trace", "internal.class", "Exception", "sg.edu");
    verifyNoInteractions(history, events);
  }
}
