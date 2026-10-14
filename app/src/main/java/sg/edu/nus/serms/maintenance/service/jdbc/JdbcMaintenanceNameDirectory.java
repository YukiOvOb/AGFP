package sg.edu.nus.serms.maintenance.service.jdbc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.maintenance.service.MaintenanceEquipmentLabel;
import sg.edu.nus.serms.maintenance.service.MaintenanceNameDirectory;

/** One batched query per label kind, so a list response never issues per-row lookups. */
@Component
public class JdbcMaintenanceNameDirectory implements MaintenanceNameDirectory {
  private final NamedParameterJdbcOperations jdbc;

  public JdbcMaintenanceNameDirectory(NamedParameterJdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  public Map<UUID, MaintenanceEquipmentLabel> findEquipmentLabels(Set<UUID> equipmentIds) {
    if (equipmentIds.isEmpty()) return Map.of();
    var labels = new HashMap<UUID, MaintenanceEquipmentLabel>();
    jdbc.query("""
        SELECT equipment_id, name, asset_tag FROM serms.equipment WHERE equipment_id IN (:ids)
        """, Map.of("ids", equipmentIds), row -> {
          labels.put(row.getObject("equipment_id", UUID.class),
              new MaintenanceEquipmentLabel(row.getString("name"), row.getString("asset_tag")));
        });
    return Map.copyOf(labels);
  }

  @Override
  public Map<UUID, String> findUserDisplayNames(Set<UUID> userIds) {
    if (userIds.isEmpty()) return Map.of();
    var names = new HashMap<UUID, String>();
    jdbc.query("""
        SELECT user_id, display_name FROM serms.app_user WHERE user_id IN (:ids)
        """, Map.of("ids", userIds), row -> {
          names.put(row.getObject("user_id", UUID.class), row.getString("display_name"));
        });
    return Map.copyOf(names);
  }
}
