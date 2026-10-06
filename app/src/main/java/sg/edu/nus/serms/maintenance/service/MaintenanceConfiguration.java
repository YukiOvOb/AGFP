package sg.edu.nus.serms.maintenance.service;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceConfiguration {
  @Bean("maintenanceClock")
  Clock maintenanceClock() { return Clock.systemUTC(); }

  @Bean("maintenanceIdentifiers")
  Supplier<UUID> maintenanceIdentifiers() { return UUID::randomUUID; }
}
