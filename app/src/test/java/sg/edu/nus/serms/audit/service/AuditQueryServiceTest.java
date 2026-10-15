package sg.edu.nus.serms.audit.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.audit.repository.AuditQueryRepository;
import sg.edu.nus.serms.identity.service.AdminAccess;

class AuditQueryServiceTest {
  final AuditQueryRepository repository = mock(AuditQueryRepository.class);
  final AdminAccess access = mock(AdminAccess.class);
  final AuditQueryService service = new AuditQueryService(repository, access);
  final UUID operator = UUID.randomUUID();

  @Test void supportsAllOptionalFiltersAndTrimsExactValues() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z"), to = from.plusSeconds(3600);
    service.list(operator, operator, " USER_CREATED ", " User ", from, to, 1, 10);
    verify(access).requireAdministrator(operator);
    verify(repository).list(operator, "USER_CREATED", "User", from, to, 1, 10);
    service.list(operator, null, null, null, null, null, 0, 20);
    verify(repository).list(null, "", "", null, null, 0, 20);
  }

  @Test void rejectsInvertedEqualPeriodsAndOversizedFilters() {
    Instant now = Instant.now();
    for (Instant to : new Instant[]{now, now.minusSeconds(1)})
      assertThatThrownBy(() -> service.list(operator, null, "", "", now, to, 0, 20))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(operator, null, "x".repeat(101), "", null, null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(operator, null, "", "x".repeat(101), null, null, 0, 20))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }
}
