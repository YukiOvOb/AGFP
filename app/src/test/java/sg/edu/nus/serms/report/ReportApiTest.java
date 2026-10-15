package sg.edu.nus.serms.report;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sg.edu.nus.serms.identity.api.AuthController;
import sg.edu.nus.serms.identity.service.IdentityService;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportService;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportSnapshot;
import sg.edu.nus.serms.report.api.ReportController;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryReservations;
import sg.edu.nus.serms.report.domain.OperationsReport.OverdueLoan;
import sg.edu.nus.serms.report.repository.ReportRepository;
import sg.edu.nus.serms.report.repository.ReportRepository.LoanedTime;
import sg.edu.nus.serms.report.service.ReportService;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.security.IdentityAuthenticationProvider;
import sg.edu.nus.serms.shared.security.SecurityProblemWriter;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

/** HTTP mapping, authorization and validation of the report endpoint. */
@ActiveProfiles("test")
@WebMvcTest(
    controllers = {ReportController.class, AuthController.class},
    properties = "serms.security.secure-cookies=false")
@Import({
  SecurityConfig.class,
  IdentityAuthenticationProvider.class,
  SecurityProblemWriter.class,
  ReportService.class
})
class ReportApiTest {
  static final String URL = "/api/v1/reports";
  static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
  static final UUID ITEM = new UUID(0, 7);

  @Autowired MockMvc mvc;
  @MockitoBean IdentityService identity;
  @MockitoBean ReportRepository reports;
  @MockitoBean MaintenanceReportService maintenance;
  // Replaces the application's clock bean, which the report service selects by name.
  @MockitoBean(name = "clock") Clock clock;

  RequestPostProcessor as(String... roles) {
    return user(new SermsUserPrincipal(new UUID(0, 1), "me@example.com", "Me", Set.of(roles)));
  }

  @BeforeEach
  void setUp() {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(true);
    when(clock.instant()).thenReturn(NOW);
    when(maintenance.getMaintenanceReport(any(), any()))
        .thenAnswer(
            a ->
                new MaintenanceReportSnapshot(
                    a.getArgument(0), a.getArgument(1), Map.of(MaintenanceStatus.OPEN, 1L), 2, 0, Optional.empty()));
    when(reports.reservationsByCategory(any(), any()))
        .thenReturn(List.of(new CategoryReservations("Audio", 4, 1, 1, 1, 0, 1)));
    when(reports.loanedTime(any(), any()))
        .thenReturn(
            List.of(new LoanedTime(ITEM, "MIC-1", "Microphone", "Audio", "ON_LOAN", 2, Duration.ofHours(36))));
    when(reports.overdueAt(NOW))
        .thenReturn(
            List.of(
                new OverdueLoan(
                    new UUID(0, 8), ITEM, "MIC-1", "Microphone", "Audio", new UUID(0, 9), "Alex",
                    NOW.minus(Duration.ofMinutes(90)), Duration.ofMinutes(90))));
  }

  String period() {
    return "?from=" + NOW.minus(Duration.ofDays(3)) + "&to=" + NOW;
  }

  @Test
  void administratorReceivesEverySectionWithDurationsInHours() throws Exception {
    mvc.perform(get(URL + period()).with(as("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measuredUntil").value(NOW.toString()))
        .andExpect(jsonPath("$.reservationsByCategory[0].category").value("Audio"))
        .andExpect(jsonPath("$.reservationsByCategory[0].cancelled").value(1))
        .andExpect(jsonPath("$.equipmentUtilisation[0].loanedHours").value(36.0))
        .andExpect(jsonPath("$.equipmentUtilisation[0].utilisation").value(0.5))
        .andExpect(jsonPath("$.utilisationByCategory[0].utilisation").value(0.5))
        .andExpect(jsonPath("$.overdueNow[0].borrowerName").value("Alex"))
        .andExpect(jsonPath("$.overdueNow[0].overdueHours").value(1.5))
        .andExpect(jsonPath("$.maintenance.casesByStatus.OPEN").value(1))
        .andExpect(jsonPath("$.maintenance.averageCompletionHours").doesNotExist())
        .andExpect(jsonPath("$.overdueByCategory").isArray())
        .andExpect(jsonPath("$.maintenanceByEquipment").isArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER"})
  void otherRolesAreRefused(String role) throws Exception {
    mvc.perform(get(URL + period()).with(as(role))).andExpect(status().isForbidden());
    verifyNoInteractions(reports);
  }

  @Test
  void signInIsRequired() throws Exception {
    mvc.perform(get(URL + period())).andExpect(status().isUnauthorized());
    verifyNoInteractions(reports);
  }

  @Test
  void invalidPeriodsAreRejected() throws Exception {
    mvc.perform(get(URL + "?from=" + NOW + "&to=" + NOW.minusSeconds(60)).with(as("ADMIN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/problems/validation"));
    mvc.perform(get(URL + "?from=" + NOW.minus(Duration.ofDays(400)) + "&to=" + NOW).with(as("ADMIN")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(URL + "?from=yesterday&to=" + NOW).with(as("ADMIN")))
        .andExpect(status().isBadRequest());
    mvc.perform(get(URL).with(as("ADMIN"))).andExpect(status().isBadRequest());
    verifyNoInteractions(reports);
  }
}
