package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;
import sg.edu.nus.serms.maintenance.service.security.MaintenanceAccessPolicy;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

class MaintenanceMethodSecurityTest {
  static final UUID ME = new UUID(0, 1), OTHER = new UUID(0, 2), CASE = new UUID(0, 3), EQUIPMENT = new UUID(0, 4);
  final MaintenanceCaseRepository cases = mock(MaintenanceCaseRepository.class);
  final MaintenanceHistoryRepository history = mock(MaintenanceHistoryRepository.class);
  final MaintenanceReportRepository reports = mock(MaintenanceReportRepository.class);
  final EquipmentMaintenancePort equipment = mock(EquipmentMaintenancePort.class);
  final MaintenanceEventPublisher events = mock(MaintenanceEventPublisher.class);
  AnnotationConfigApplicationContext context;
  MaintenanceService service;
  MaintenanceReportService reportService;

  @Configuration
  @EnableMethodSecurity
  static class Security {}

  @BeforeEach
  void setup() {
    context = new AnnotationConfigApplicationContext();
    context.register(Security.class);
    context.registerBean("maintenanceAccess", MaintenanceAccessPolicy.class, () -> new MaintenanceAccessPolicy(cases));
    context.registerBean(MaintenanceService.class, () -> new MaintenanceService(cases, history, equipment,
        mock(TechnicianDirectoryPort.class), events, () -> CASE, Clock.systemUTC()));
    context.registerBean(MaintenanceReportService.class, () -> new MaintenanceReportService(reports));
    context.refresh();
    service = context.getBean(MaintenanceService.class);
    reportService = context.getBean(MaintenanceReportService.class);
    var item = new MaintenanceCase(CASE, EQUIPMENT, OTHER, null, "Fault", Instant.now());
    item.assign(ME);
    when(cases.findById(CASE)).thenReturn(Optional.of(item));
    when(cases.insert(any())).thenAnswer(call -> call.getArgument(0));
    when(cases.update(any())).thenAnswer(call -> call.getArgument(0));
    when(reports.countCasesByStatus()).thenReturn(Map.of());
    when(reports.averageCompletionDurationBetween(any(), any())).thenReturn(Optional.empty());
  }

  void login(String... roles) {
    var me = new SermsUserPrincipal(ME, "me@example.com", "Me", Set.of(roles));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(me, "", me.getAuthorities()));
  }

  @AfterEach
  void close() { SecurityContextHolder.clearContext(); context.close(); }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "MAINTAINER"})
  void directOperatorMethodsRejectUnprivilegedRole(String role) {
    login(role);
    assertThatThrownBy(() -> service.getOpenCases()).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.assignTechnician(CASE, ME, ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getEquipmentCases(EQUIPMENT)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getEquipmentMaintenanceHistory(EQUIPMENT)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(equipment, history, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAINTAINER", "APPROVER", "ADMIN"})
  void directReportingDoesNotGrantImplicitBorrowerPrivileges(String role) {
    login(role);
    assertThatThrownBy(() -> service.reportFault(EQUIPMENT, ME, "Fault")).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.reportFault(EQUIPMENT, ME, "Fault", null)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(equipment, history);
  }

  @Test
  void directCallerCannotSubstituteActorReporterOrTechnicianId() {
    login("BORROWER");
    assertThatThrownBy(() -> service.reportFault(EQUIPMENT, OTHER, "Fault")).isInstanceOf(AccessDeniedException.class);
    login("CUSTODIAN");
    assertThatThrownBy(() -> service.assignTechnician(CASE, OTHER, OTHER)).isInstanceOf(AccessDeniedException.class);
    login("MAINTAINER");
    assertThatThrownBy(() -> service.getAssignedCases(OTHER)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.startMaintenance(CASE, OTHER)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.resolve(CASE, "Done", OTHER)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(equipment, history, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void directReportRequiresAdmin(String role) {
    login(role);
    assertThatThrownBy(() -> reportService.getMaintenanceReport(Instant.EPOCH, Instant.EPOCH.plusSeconds(60)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(reports);
  }

  @Test
  void adminReportAndSeparatelyHeldBorrowerRoleWork() {
    login("ADMIN");
    assertThat(reportService.getMaintenanceReport(Instant.EPOCH, Instant.EPOCH.plusSeconds(60)).completedCaseCount()).isZero();
    login("ADMIN", "BORROWER");
    assertThat(service.reportFault(EQUIPMENT, ME, "Fault").reportedBy()).isEqualTo(ME);
  }

  @Test
  void maintainerReadAccessIsLimitedToAssignedCaseAndOwnList() {
    login("MAINTAINER");
    assertThat(service.getCase(CASE).assignedTo()).isEqualTo(ME);
    service.getCaseHistory(CASE);
    service.getAssignedCases(ME);
    var other = new MaintenanceCase(CASE, EQUIPMENT, OTHER, null, "Fault", Instant.now());
    other.assign(OTHER);
    when(cases.findById(CASE)).thenReturn(Optional.of(other));
    assertThatThrownBy(() -> service.getCase(CASE)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.getCaseHistory(CASE)).isInstanceOf(AccessDeniedException.class);
    clearInvocations(history);
    assertThatThrownBy(() -> service.startMaintenance(CASE, ME)).isInstanceOf(AccessDeniedException.class);
    verify(cases, never()).update(any());
    verifyNoInteractions(history, events);
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair", "note", "resolve", "unrepairable"})
  void directMaintainerCannotWorkOnAnotherTechniciansCase(String action) {
    login("MAINTAINER");
    var item = new MaintenanceCase(CASE, EQUIPMENT, OTHER, null, "Fault", Instant.now());
    item.assign(OTHER);
    if (!action.equals("start")) item.start();
    when(cases.findById(CASE)).thenReturn(Optional.of(item));
    assertThatThrownBy(() -> {
      switch (action) {
        case "start" -> service.startMaintenance(CASE, ME);
        case "diagnosis" -> service.recordDiagnosis(CASE, "Detail", ME);
        case "repair" -> service.recordRepairAction(CASE, "Detail", ME);
        case "note" -> service.addMaintenanceNote(CASE, "Detail", ME);
        case "resolve" -> service.resolve(CASE, "Done", ME);
        case "unrepairable" -> service.markUnrepairable(CASE, "Done", ME);
        default -> throw new AssertionError(action);
      }
    }).isInstanceOf(AccessDeniedException.class);
    verify(cases, never()).update(any());
    verifyNoInteractions(history, events);
    verify(equipment).lockForMaintenance(EQUIPMENT);
    verifyNoMoreInteractions(equipment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "ADMIN"})
  void directTechnicianActionsRequireMaintainer(String role) {
    login(role);
    assertThatThrownBy(() -> service.startMaintenance(CASE, ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.recordDiagnosis(CASE, "Detail", ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.recordRepairAction(CASE, "Detail", ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.addMaintenanceNote(CASE, "Detail", ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.resolve(CASE, "Done", ME)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.markUnrepairable(CASE, "Done", ME)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(equipment, history, events);
  }
}
