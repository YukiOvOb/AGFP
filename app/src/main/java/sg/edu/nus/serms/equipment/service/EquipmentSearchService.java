package sg.edu.nus.serms.equipment.service;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.equipment.domain.BusySlot;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.equipment.domain.InvalidSearchException;
import sg.edu.nus.serms.equipment.domain.SearchResult;
import sg.edu.nus.serms.equipment.domain.TimeWindow;
import sg.edu.nus.serms.equipment.repository.EquipmentSearchRepository;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

/** UC01 read side: search the catalogue and inspect one item's availability. */
@Service
public class EquipmentSearchService {

  /** Filter values for the search form. */
  public record FilterOptions(List<String> categories, List<String> locations) {}

  /** One item with the slots already held inside the requested window. */
  public record Availability(
      EquipmentSummary equipment,
      TimeWindow window,
      boolean openMaintenance,
      List<BusySlot> busySlots) {}

  private final EquipmentSearchRepository repository;

  public EquipmentSearchService(EquipmentSearchRepository repository) {
    this.repository = repository;
  }

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public SearchResult search(EquipmentSearchCriteria criteria) {
    return repository.search(criteria);
  }

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public FilterOptions filterOptions() {
    return new FilterOptions(repository.categories(), repository.locations());
  }

  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public Availability availability(UUID equipmentId, TimeWindow window) {
    if (java.time.Duration.between(window.start(), window.end())
            .compareTo(EquipmentSearchCriteria.MAX_WINDOW)
        > 0) {
      throw new InvalidSearchException("The time window may span at most 90 days");
    }
    EquipmentSummary equipment =
        repository.findVisible(equipmentId, window).orElseThrow(RecordNotFoundException::new);
    return new Availability(
        equipment,
        window,
        repository.hasOpenMaintenance(equipmentId),
        repository.busySlots(equipmentId, window));
  }
}
