package sg.edu.nus.serms.equipment.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.equipment.service.EquipmentManagementService;
import sg.edu.nus.serms.equipment.service.EquipmentManagementService.ManagedEquipment;

@RestController
@RequestMapping(value = "/api/v1/equipment/manage", produces = "application/json")
public class EquipmentManagementController {
  private final EquipmentManagementService service;
  public EquipmentManagementController(EquipmentManagementService service) { this.service = service; }

  public record EquipmentInput(
      @NotBlank String assetTag, String serialNumber, @NotBlank String name,
      String description, @NotBlank String category, @NotBlank String location,
      @NotBlank @Pattern(regexp = "GOOD|FAIR|POOR") String condition,
      boolean requiresApproval, String maxBorrowDuration, @PositiveOrZero Long expectedVersion) {}
  public record CategoryInput(@NotBlank String name) {}
  public record CategoryRename(@NotBlank String name) {}
  public record RetireInput(@NotNull @PositiveOrZero Long expectedVersion) {}

  @GetMapping
  @Operation(operationId = "listManagedEquipment")
  public List<ManagedEquipment> list(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }

  @PostMapping
  @Operation(operationId = "registerEquipment")
  public ResponseEntity<ManagedEquipment> register(@Valid @RequestBody EquipmentInput input) {
    var equipment = service.register(input);
    return ResponseEntity.created(URI.create("/api/v1/equipment/manage/" + equipment.equipmentId())).body(equipment);
  }

  @PutMapping("/{id}")
  @Operation(operationId = "updateManagedEquipment")
  public ManagedEquipment update(@PathVariable UUID id, @Valid @RequestBody EquipmentInput input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/retire")
  @Operation(operationId = "retireEquipment")
  public ManagedEquipment retire(@PathVariable UUID id, @Valid @RequestBody RetireInput input) {
    return service.retire(id, input.expectedVersion());
  }

  @GetMapping("/categories")
  @Operation(operationId = "listEquipmentCategories")
  public List<String> categories() { return service.categories(); }

  @PostMapping("/categories")
  @Operation(operationId = "createEquipmentCategory")
  public ResponseEntity<List<String>> createCategory(@Valid @RequestBody CategoryInput input) {
    return ResponseEntity.created(URI.create("/api/v1/equipment/manage/categories")).body(service.createCategory(input.name()));
  }

  @PatchMapping("/categories/{name}")
  @Operation(operationId = "renameEquipmentCategory")
  public List<String> renameCategory(@PathVariable String name, @Valid @RequestBody CategoryRename input) {
    return service.renameCategory(name, input.name());
  }
}
