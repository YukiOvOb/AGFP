package sg.edu.nus.serms.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.repository.EquipmentSearchRepository;
import sg.edu.nus.serms.equipment.service.EquipmentSearchService;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.policy.ReservationRules;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;

/** Server-side permission checks: a role in the UI is never enough. */
@SpringJUnitConfig(ReservationServiceSecurityTest.Config.class)
class ReservationServiceSecurityTest {
  static final String ME = "00000000-0000-0000-0000-00000000000a";
  static final UUID ME_ID = UUID.fromString(ME);

  @TestConfiguration
  @EnableMethodSecurity
  static class Config {
    @Bean
    ReservationRepository repository() {
      return mock(ReservationRepository.class);
    }

    @Bean
    ReservationService service(ReservationRepository repository) {
      return new ReservationService(
          repository,
          new ReservationRules(java.util.List.of()),
          mock(ApplicationEventPublisher.class),
          Clock.systemUTC());
    }

    @Bean
    EquipmentSearchService search() {
      return new EquipmentSearchService(mock(EquipmentSearchRepository.class));
    }
  }

  @Autowired ReservationService service;
  @Autowired EquipmentSearchService search;

  ReservationRequest request() {
    return new ReservationRequest(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(60), null);
  }

  @Test
  @WithMockUser(username = ME, roles = "APPROVER")
  void nonBorrowersCannotReserveOrCancel() {
    assertThatThrownBy(() -> service.submit(ME_ID, request())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.cancel(ME_ID, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.mine(ME_ID, null, 0, 10)).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @WithMockUser(username = ME, roles = "BORROWER")
  void aBorrowerCannotActForSomeoneElse() {
    UUID other = UUID.randomUUID();
    assertThatThrownBy(() -> service.submit(other, request())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.cancel(other, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.get(other, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void anonymousCallersAreRejected() {
    assertThatThrownBy(() -> search.filterOptions())
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    assertThatThrownBy(
            () -> search.search(new EquipmentSearchCriteria(null, null, null, null, null, false, 0, 20)))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  @Test
  @WithMockUser(username = ME, roles = "APPROVER")
  void anySignedInUserMaySearch() {
    assertThatCode(() -> search.filterOptions()).doesNotThrowAnyException();
  }
}
