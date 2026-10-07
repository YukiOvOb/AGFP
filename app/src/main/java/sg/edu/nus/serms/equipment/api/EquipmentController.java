package sg.edu.nus.serms.equipment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.equipment.domain.BusySlot;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.domain.EquipmentStatus;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.equipment.domain.TimeWindow;
import sg.edu.nus.serms.equipment.service.EquipmentSearchService;

/** UC01 search endpoints. Times are ISO-8601 instants (UTC); the client shows Singapore time. */
@RestController
@RequestMapping(value = "/api/v1/equipment", produces = "application/json")
public class EquipmentController {

  public record EquipmentView(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String assetTag,
      @Schema(requiredMode = RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) String location,
      @Schema(requiredMode = RequiredMode.REQUIRED) EquipmentStatus status,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean requiresApproval,
      @Schema(
              nullable = true,
              description = "Whether the requested window can be reserved; null without a window")
          Boolean availableInWindow) {
    static EquipmentView of(EquipmentSummary s) {
      return new EquipmentView(
          s.equipmentId(),
          s.assetTag(),
          s.name(),
          s.category(),
          s.location(),
          s.status(),
          s.requiresApproval(),
          s.availableInWindow());
    }
  }

  public record EquipmentPage(
      @Schema(requiredMode = RequiredMode.REQUIRED) List<EquipmentView> content,
      @Schema(requiredMode = RequiredMode.REQUIRED) int page,
      @Schema(requiredMode = RequiredMode.REQUIRED) int size,
      @Schema(requiredMode = RequiredMode.REQUIRED) long totalElements,
      @Schema(requiredMode = RequiredMode.REQUIRED) int totalPages) {}

  public record FilterOptionsView(
      @Schema(requiredMode = RequiredMode.REQUIRED) List<String> categories,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<String> locations) {}

  public record BusySlotView(
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant startAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant endAt,
      @Schema(
              requiredMode = RequiredMode.REQUIRED,
              allowableValues = {"PENDING_APPROVAL", "RESERVED", "ON_LOAN"})
          String reason) {
    static BusySlotView of(BusySlot s) {
      return new BusySlotView(s.startAt(), s.endAt(), s.reason());
    }
  }

  public record AvailabilityView(
      @Schema(requiredMode = RequiredMode.REQUIRED) EquipmentView equipment,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant from,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant to,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean available,
      @Schema(requiredMode = RequiredMode.REQUIRED) boolean openMaintenance,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<BusySlotView> busySlots) {}

  private final EquipmentSearchService service;

  public EquipmentController(EquipmentSearchService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(operationId = "searchEquipment", summary = "Search equipment by text, category, location and time window")
  public EquipmentPage search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "false") boolean availableOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result =
        service.search(
            new EquipmentSearchCriteria(q, category, location, from, to, availableOnly, page, size));
    return new EquipmentPage(
        result.items().stream().map(EquipmentView::of).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }

  @GetMapping("/filters")
  @Operation(operationId = "equipmentFilters", summary = "Categories and locations for the search form")
  public FilterOptionsView filters() {
    var options = service.filterOptions();
    return new FilterOptionsView(options.categories(), options.locations());
  }

  @GetMapping("/{id}/availability")
  @Operation(operationId = "equipmentAvailability", summary = "Held slots of one item inside a window")
  public AvailabilityView availability(
      @PathVariable UUID id,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    var a = service.availability(id, new TimeWindow(from, to));
    return new AvailabilityView(
        EquipmentView.of(a.equipment()),
        from,
        to,
        Boolean.TRUE.equals(a.equipment().availableInWindow()),
        a.openMaintenance(),
        a.busySlots().stream().map(BusySlotView::of).toList());
  }
}
