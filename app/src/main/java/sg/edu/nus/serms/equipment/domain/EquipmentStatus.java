package sg.edu.nus.serms.equipment.domain;

/** Equipment states stored in {@code serms.equipment.status} (V002). */
public enum EquipmentStatus {
  AVAILABLE,
  ON_LOAN,
  UNDER_MAINTENANCE,
  RETIRED
}
