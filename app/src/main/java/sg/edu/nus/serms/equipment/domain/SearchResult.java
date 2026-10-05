package sg.edu.nus.serms.equipment.domain;

import java.util.List;

public record SearchResult(List<EquipmentSummary> items, long totalElements, int page, int size) {
  public int totalPages() {
    return (int) ((totalElements + size - 1) / size);
  }
}
