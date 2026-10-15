package sg.edu.nus.serms.reservation.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.serms.equipment.api.EquipmentController;
import sg.edu.nus.serms.equipment.domain.EquipmentStatus;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.equipment.domain.SearchResult;
import sg.edu.nus.serms.equipment.domain.TimeWindow;
import sg.edu.nus.serms.equipment.domain.BusySlot;
import sg.edu.nus.serms.equipment.service.EquipmentSearchService;
import sg.edu.nus.serms.identity.service.IdentityService;
import sg.edu.nus.serms.reservation.domain.Reservation;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.domain.RuleViolation;
import sg.edu.nus.serms.reservation.service.ReservationException;
import sg.edu.nus.serms.reservation.service.ReservationService;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;
import sg.edu.nus.serms.shared.security.IdentityAuthenticationProvider;
import sg.edu.nus.serms.shared.security.SecurityProblemWriter;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(
    controllers = {ReservationController.class, EquipmentController.class},
    properties = "serms.security.secure-cookies=false")
@Import({SecurityConfig.class, IdentityAuthenticationProvider.class, SecurityProblemWriter.class})
class ReservationApiTest {
  @org.junit.jupiter.api.BeforeEach void currentIdentitySession() {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(true);
  }
  static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  static final UUID ITEM = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  static final UUID RES = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  static final SermsUserPrincipal BORROWER =
      new SermsUserPrincipal(ME, "borrower@example.com", "Demo Borrower", Set.of("BORROWER"));

  @Autowired MockMvc mvc;
  @MockitoBean ReservationService reservations;
  @MockitoBean EquipmentSearchService search;
  @MockitoBean IdentityService identity;

  static String body(String start, String end) {
    return "{\"equipmentId\":\"" + ITEM + "\",\"startAt\":\"" + start + "\",\"endAt\":\"" + end
        + "\",\"purpose\":\"Shoot\"}";
  }

  static Reservation reservation(ReservationStatus status) {
    return new Reservation(RES, ME, ITEM, "CAM-001", "Canon EOS R6", "COM1 Lab",
        Instant.parse("2026-10-10T01:00:00Z"), Instant.parse("2026-10-10T03:00:00Z"), status,
        "Shoot", Instant.parse("2026-10-05T01:00:00Z"), 0, false);
  }

  @Test
  void anonymousUsersGet401() throws Exception {
    mvc.perform(get("/api/v1/equipment"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    mvc.perform(get("/api/v1/reservations/mine")).andExpect(status().isUnauthorized());
  }

  @Test
  void writesWithoutCsrfAreRejected() throws Exception {
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).contentType("application/json")
            .content(body("2026-10-10T01:00:00Z", "2026-10-10T03:00:00Z")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/csrf"));
    verifyNoInteractions(reservations);
  }

  @Test
  void createUsesTheSessionUserAndReturns201() throws Exception {
    when(reservations.submit(eq(ME), any())).thenReturn(reservation(ReservationStatus.CONFIRMED));
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
            .contentType("application/json")
            .content(body("2026-10-10T01:00:00Z", "2026-10-10T03:00:00Z")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/reservations/" + RES))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.cancellable").value(true))
        .andExpect(jsonPath("$.requesterId").doesNotExist());
    verify(reservations).submit(ME, new ReservationRequest(ITEM,
        Instant.parse("2026-10-10T01:00:00Z"), Instant.parse("2026-10-10T03:00:00Z"), "Shoot"));
  }

  @Test
  void missingFieldsGiveFieldErrors() throws Exception {
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
            .contentType("application/json").content("{\"purpose\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/problems/validation"))
        .andExpect(jsonPath("$.errors[?(@.field=='equipmentId')].message").value("Choose the equipment."))
        .andExpect(jsonPath("$.errors[?(@.field=='startAt')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='endAt')]").exists());
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
            .contentType("application/json").content(body("not-a-time", "2026-10-10T03:00:00Z")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(reservations);
  }

  @Test
  void slotConflictIs409() throws Exception {
    when(reservations.submit(eq(ME), any())).thenThrow(new ReservationException.SlotTaken());
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
            .contentType("application/json")
            .content(body("2026-10-10T01:00:00Z", "2026-10-10T03:00:00Z")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/problems/reservation-conflict"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("another time slot")));
  }

  @Test
  void brokenRulesAre422WithEveryViolation() throws Exception {
    when(reservations.submit(eq(ME), any())).thenThrow(new ReservationException.RulesBroken(List.of(
        new RuleViolation("too-long", "A reservation may last at most 7 days."),
        new RuleViolation("quota-exceeded", "Limit reached."))));
    mvc.perform(post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
            .contentType("application/json")
            .content(body("2026-10-10T01:00:00Z", "2026-10-20T03:00:00Z")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.type").value("/problems/reservation-rule"))
        .andExpect(jsonPath("$.violations[0].code").value("too-long"))
        .andExpect(jsonPath("$.violations[1].code").value("quota-exceeded"));
  }

  @Test
  void ineligibleAccountIs403AndUnavailableEquipmentIs409() throws Exception {
    when(reservations.submit(eq(ME), any()))
        .thenThrow(new ReservationException.RequesterNotEligible())
        .thenThrow(new ReservationException.EquipmentUnavailable("Under maintenance."));
    var request = post("/api/v1/reservations").with(user(BORROWER)).with(csrf())
        .contentType("application/json").content(body("2026-10-10T01:00:00Z", "2026-10-10T03:00:00Z"));
    mvc.perform(request).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/not-eligible"));
    mvc.perform(request).andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/problems/equipment-unavailable"));
  }

  @Test
  void cancelAndNotCancellable() throws Exception {
    when(reservations.cancel(ME, RES)).thenReturn(reservation(ReservationStatus.CANCELLED))
        .thenThrow(new ReservationException.NotCancellable("Already checked out."));
    mvc.perform(post("/api/v1/reservations/" + RES + "/cancel").with(user(BORROWER)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(post("/api/v1/reservations/" + RES + "/cancel").with(user(BORROWER)).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/problems/not-cancellable"));
  }

  @Test
  void someoneElsesReservationIs404() throws Exception {
    when(reservations.get(ME, RES)).thenThrow(new RecordNotFoundException());
    mvc.perform(get("/api/v1/reservations/" + RES).with(user(BORROWER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/problems/not-found"));
  }

  @Test
  void listsMyReservations() throws Exception {
    when(reservations.mine(ME, ReservationStatus.CONFIRMED, 0, 20)).thenReturn(
        new ReservationService.ReservationPage(List.of(reservation(ReservationStatus.CONFIRMED)), 21, 0, 20));
    mvc.perform(get("/api/v1/reservations/mine?status=CONFIRMED").with(user(BORROWER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].equipmentName").value("Canon EOS R6"))
        .andExpect(jsonPath("$.totalPages").value(2));
    mvc.perform(get("/api/v1/reservations/mine?status=NOPE").with(user(BORROWER)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchPassesFiltersAndReportsWindowAvailability() throws Exception {
    when(search.search(any())).thenReturn(new SearchResult(List.of(new EquipmentSummary(ITEM, "CAM-001",
        "Canon EOS R6", "Camera", "COM1 Lab", EquipmentStatus.AVAILABLE, false, true,
        "Full-frame mirrorless camera", java.time.Duration.ofHours(48))), 1, 0, 20));
    mvc.perform(get("/api/v1/equipment").with(user(BORROWER))
            .param("q", "canon").param("category", "Camera")
            .param("from", "2026-10-10T01:00:00Z").param("to", "2026-10-10T03:00:00Z")
            .param("availableOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].availableInWindow").value(true))
        .andExpect(jsonPath("$.content[0].description").value("Full-frame mirrorless camera"))
        .andExpect(jsonPath("$.content[0].maxBorrowDuration").value("PT48H"))
        .andExpect(jsonPath("$.totalPages").value(1));
    verify(search).search(argThat(c -> "canon".equals(c.keyword()) && "Camera".equals(c.category())
        && c.availableOnly() && Instant.parse("2026-10-10T01:00:00Z").equals(c.from())));
  }

  @Test
  void invalidSearchExplainsWhatToFix() throws Exception {
    mvc.perform(get("/api/v1/equipment").with(user(BORROWER)).param("availableOnly", "true"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Choose a time window to show only available equipment"));
    mvc.perform(get("/api/v1/equipment").with(user(BORROWER))
            .param("from", "2026-10-10T03:00:00Z").param("to", "2026-10-10T01:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The time window must end after it starts"));
    verifyNoInteractions(search);
  }

  @Test
  void filtersAndAvailability() throws Exception {
    when(search.filterOptions()).thenReturn(
        new EquipmentSearchService.FilterOptions(List.of("Camera"), List.of("COM1 Lab")));
    mvc.perform(get("/api/v1/equipment/filters").with(user(BORROWER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0]").value("Camera"));
    var from = Instant.parse("2026-10-10T00:00:00Z");
    var to = Instant.parse("2026-10-11T00:00:00Z");
    when(search.availability(ITEM, new TimeWindow(from, to))).thenReturn(new EquipmentSearchService.Availability(
        new EquipmentSummary(ITEM, "CAM-001", "Canon EOS R6", "Camera", "COM1 Lab",
            EquipmentStatus.AVAILABLE, false, false, null, null),
        new TimeWindow(from, to), false,
        List.of(new BusySlot(Instant.parse("2026-10-10T01:00:00Z"), Instant.parse("2026-10-10T03:00:00Z"), "RESERVED"))));
    mvc.perform(get("/api/v1/equipment/" + ITEM + "/availability").with(user(BORROWER))
            .param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.equipment.description").isEmpty())
        .andExpect(jsonPath("$.equipment.maxBorrowDuration").isEmpty())
        .andExpect(jsonPath("$.busySlots[0].reason").value("RESERVED"))
        .andExpect(jsonPath("$.busySlots[0].requesterId").doesNotExist());
  }
}
