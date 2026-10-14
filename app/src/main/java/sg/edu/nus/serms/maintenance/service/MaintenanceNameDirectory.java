package sg.edu.nus.serms.maintenance.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only display labels for identifiers on maintenance responses. Callers pass only identifiers
 * taken from cases or history they were already authorized to read; this port adds no access rule.
 * Missing identifiers are absent from the returned maps.
 */
public interface MaintenanceNameDirectory {
  Map<UUID, MaintenanceEquipmentLabel> findEquipmentLabels(Set<UUID> equipmentIds);

  Map<UUID, String> findUserDisplayNames(Set<UUID> userIds);
}
