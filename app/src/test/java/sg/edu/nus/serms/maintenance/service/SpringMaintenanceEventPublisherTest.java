package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;

class SpringMaintenanceEventPublisherTest {
  private final List<Object> published = new ArrayList<>();
  private final ApplicationEventPublisher spring = published::add;
  private final SpringMaintenanceEventPublisher bridge = new SpringMaintenanceEventPublisher(spring);
  private static final UUID ID = new UUID(0, 1);
  private static final Instant NOW = Instant.parse("2026-10-02T01:00:00Z");

  @Test
  void forwardsAssignedEventExactlyOnceWithoutReplacingIt() {
    var event = new MaintenanceAssigned(ID, ID, ID, ID, NOW);
    bridge.publish(event);
    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isSameAs(event);
  }

  @Test
  void forwardsCompletedEventExactlyOnceWithoutReplacingIt() {
    var event = new MaintenanceCompleted(ID, ID, ID, ID, MaintenanceStatus.RESOLVED, NOW);
    bridge.publish(event);
    assertThat(published).hasSize(1);
    assertThat(published.get(0)).isSameAs(event);
  }
}
