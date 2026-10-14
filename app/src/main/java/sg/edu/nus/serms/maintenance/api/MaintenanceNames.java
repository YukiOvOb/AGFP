package sg.edu.nus.serms.maintenance.api;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.service.MaintenanceEquipmentLabel;
import sg.edu.nus.serms.maintenance.service.MaintenanceNameDirectory;

/** Display labels for one response, resolved with at most one lookup per label kind. */
public record MaintenanceNames(
    Map<UUID, MaintenanceEquipmentLabel> equipmentLabels, Map<UUID, String> userNames) {
  public MaintenanceNames {
    equipmentLabels = Map.copyOf(equipmentLabels);
    userNames = Map.copyOf(userNames);
  }

  static MaintenanceNames forCases(MaintenanceNameDirectory directory,
      Collection<MaintenanceCase> cases) {
    Set<UUID> equipment = new HashSet<>();
    Set<UUID> users = new HashSet<>();
    for (var item : cases) {
      equipment.add(item.equipmentId());
      users.add(item.reportedBy());
      if (item.assignedTo() != null) users.add(item.assignedTo());
    }
    return new MaintenanceNames(directory.findEquipmentLabels(equipment),
        directory.findUserDisplayNames(users));
  }

  static MaintenanceNames forHistory(MaintenanceNameDirectory directory,
      Collection<MaintenanceHistoryEntry> entries) {
    Set<UUID> users = new HashSet<>();
    for (var entry : entries) if (entry.actorId() != null) users.add(entry.actorId());
    return new MaintenanceNames(Map.of(), directory.findUserDisplayNames(users));
  }

  MaintenanceEquipmentLabel equipment(UUID equipmentId) {
    return equipmentId == null ? null : equipmentLabels.get(equipmentId);
  }

  String user(UUID userId) {
    return userId == null ? null : userNames.get(userId);
  }
}
