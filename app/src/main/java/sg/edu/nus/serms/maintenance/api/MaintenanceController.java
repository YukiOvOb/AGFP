package sg.edu.nus.serms.maintenance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.maintenance.api.MaintenanceDtos.*;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.service.MaintenanceNameDirectory;
import sg.edu.nus.serms.maintenance.service.MaintenanceService;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

/**
 * Actors come only from the Session principal. Service policy owns RBAC, including the provisional
 * CUSTODIAN/ADMIN operator mapping. Responses expose server versions; no If-Match/expectedVersion
 * conditional browser mutation contract is implemented. Display names are looked up only after the
 * service call has authorized the read, and only for identifiers on the returned records.
 */
@RestController
@RequestMapping(value = "/api/v1/maintenance", produces = "application/json")
public class MaintenanceController {
  private final MaintenanceService service;
  private final MaintenanceReportService reports;
  private final MaintenanceNameDirectory names;

  public MaintenanceController(MaintenanceService service, MaintenanceReportService reports,
      MaintenanceNameDirectory names) {
    this.service = service;
    this.reports = reports;
    this.names = names;
  }

  @PostMapping("/cases")
  @Operation(operationId = "reportMaintenanceFault")
  @ApiResponse(responseCode = "201", description = "Maintenance case created")
  public ResponseEntity<MaintenanceCaseResponse> reportFault(
      @Valid @RequestBody ReportFaultRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    var item = service.reportFault(body.equipmentId(), me.getUserId(), body.faultDescription(), body.loanId());
    return ResponseEntity.created(URI.create("/api/v1/maintenance/cases/" + item.maintenanceCaseId()))
        .body(caseResponse(item));
  }

  @GetMapping("/cases/open")
  @Operation(operationId = "listOpenMaintenanceCases")
  public List<MaintenanceCaseResponse> open() {
    return caseResponses(service.getOpenCases());
  }

  @GetMapping("/technicians")
  @Operation(operationId = "listMaintenanceTechnicians")
  public List<MaintenanceTechnicianResponse> technicians() {
    return service.getActiveTechnicians().stream().map(MaintenanceTechnicianResponse::from).toList();
  }

  @GetMapping("/cases/assigned")
  @Operation(operationId = "listMyMaintenanceCases")
  public List<MaintenanceCaseResponse> assigned(@AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponses(service.getAssignedCases(me.getUserId()));
  }

  @GetMapping("/cases/{caseId}")
  @Operation(operationId = "getMaintenanceCase")
  public MaintenanceCaseResponse getCase(@PathVariable UUID caseId) {
    return caseResponse(service.getCase(caseId));
  }

  @GetMapping("/cases/{caseId}/history")
  @Operation(operationId = "getMaintenanceCaseHistory")
  public List<MaintenanceHistoryResponse> history(@PathVariable UUID caseId) {
    return historyResponses(service.getCaseHistory(caseId));
  }

  @PostMapping("/cases/{caseId}/assignment")
  @Operation(operationId = "assignMaintenanceTechnician")
  public MaintenanceCaseResponse assign(@PathVariable UUID caseId, @Valid @RequestBody AssignTechnicianRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.assignTechnician(caseId, body.technicianId(), me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/start")
  @Operation(operationId = "startMaintenance")
  public MaintenanceCaseResponse start(@PathVariable UUID caseId, @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.startMaintenance(caseId, me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/diagnosis")
  @Operation(operationId = "recordMaintenanceDiagnosis")
  public MaintenanceCaseResponse recordDiagnosis(@PathVariable UUID caseId, @Valid @RequestBody MaintenanceDetailRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.recordDiagnosis(caseId, body.detail(), me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/repair-actions")
  @Operation(operationId = "recordMaintenanceRepairAction")
  public MaintenanceCaseResponse recordRepairAction(@PathVariable UUID caseId, @Valid @RequestBody MaintenanceDetailRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.recordRepairAction(caseId, body.detail(), me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/notes")
  @Operation(operationId = "addMaintenanceNote")
  public MaintenanceCaseResponse addMaintenanceNote(@PathVariable UUID caseId, @Valid @RequestBody MaintenanceDetailRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.addMaintenanceNote(caseId, body.detail(), me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/resolve")
  @Operation(operationId = "resolveMaintenanceCase")
  public MaintenanceCaseResponse resolve(@PathVariable UUID caseId, @Valid @RequestBody CompleteMaintenanceRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.resolve(caseId, body.resolutionNote(), me.getUserId()));
  }

  @PostMapping("/cases/{caseId}/unrepairable")
  @Operation(operationId = "markMaintenanceUnrepairable")
  public MaintenanceCaseResponse markUnrepairable(@PathVariable UUID caseId, @Valid @RequestBody CompleteMaintenanceRequest body,
      @AuthenticationPrincipal SermsUserPrincipal me) {
    return caseResponse(service.markUnrepairable(caseId, body.resolutionNote(), me.getUserId()));
  }

  @GetMapping("/equipment/{equipmentId}/cases")
  @Operation(operationId = "listEquipmentMaintenanceCases")
  public List<MaintenanceCaseResponse> equipmentCases(@PathVariable UUID equipmentId) {
    return caseResponses(service.getEquipmentCases(equipmentId));
  }

  @GetMapping("/equipment/{equipmentId}/history")
  @Operation(operationId = "getEquipmentMaintenanceHistory")
  public List<MaintenanceHistoryResponse> equipmentHistory(@PathVariable UUID equipmentId) {
    return historyResponses(service.getEquipmentMaintenanceHistory(equipmentId));
  }

  @GetMapping("/report")
  @Operation(operationId = "getMaintenanceReport")
  public MaintenanceReportResponse report(@RequestParam Instant from, @RequestParam Instant to) {
    return MaintenanceReportResponse.from(reports.getMaintenanceReport(from, to));
  }

  private MaintenanceCaseResponse caseResponse(MaintenanceCase item) {
    return MaintenanceCaseResponse.from(item, MaintenanceNames.forCases(names, List.of(item)));
  }

  private List<MaintenanceCaseResponse> caseResponses(List<MaintenanceCase> items) {
    var labels = MaintenanceNames.forCases(names, items);
    return items.stream().map(item -> MaintenanceCaseResponse.from(item, labels)).toList();
  }

  private List<MaintenanceHistoryResponse> historyResponses(List<MaintenanceHistoryEntry> entries) {
    var labels = MaintenanceNames.forHistory(names, entries);
    return entries.stream().map(entry -> MaintenanceHistoryResponse.from(entry, labels)).toList();
  }
}
