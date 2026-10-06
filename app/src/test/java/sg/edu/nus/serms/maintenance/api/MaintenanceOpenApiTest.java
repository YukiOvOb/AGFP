package sg.edu.nus.serms.maintenance.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenanceOpenApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;

  @Test
  void exposesEveryMaintenanceOperationWithoutClientActorFields() throws Exception {
    String json = mvc.perform(get("/api/v1/openapi.json")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode document = mapper.readTree(json);
    var paths = document.path("paths");
    var expectedPaths = List.of("/api/v1/maintenance/cases",
        "/api/v1/maintenance/cases/open",
        "/api/v1/maintenance/cases/assigned",
        "/api/v1/maintenance/cases/{caseId}",
        "/api/v1/maintenance/cases/{caseId}/history",
        "/api/v1/maintenance/cases/{caseId}/assignment",
        "/api/v1/maintenance/cases/{caseId}/start",
        "/api/v1/maintenance/cases/{caseId}/diagnosis",
        "/api/v1/maintenance/cases/{caseId}/repair-actions",
        "/api/v1/maintenance/cases/{caseId}/notes",
        "/api/v1/maintenance/cases/{caseId}/resolve",
        "/api/v1/maintenance/cases/{caseId}/unrepairable",
        "/api/v1/maintenance/equipment/{equipmentId}/cases",
        "/api/v1/maintenance/equipment/{equipmentId}/history",
        "/api/v1/maintenance/report");
    for (String path : expectedPaths) assertThat(paths.has(path)).as(path).isTrue();
    var operations = new ArrayList<String>();
    paths.forEach(path -> path.forEach(operation -> {
      if (operation.has("operationId")) operations.add(operation.get("operationId").asText());
    }));
    assertThat(operations).contains("reportMaintenanceFault",
        "listOpenMaintenanceCases",
        "listMyMaintenanceCases",
        "getMaintenanceCase",
        "getMaintenanceCaseHistory",
        "assignMaintenanceTechnician",
        "startMaintenance",
        "recordMaintenanceDiagnosis",
        "recordMaintenanceRepairAction",
        "addMaintenanceNote",
        "resolveMaintenanceCase",
        "markMaintenanceUnrepairable",
        "listEquipmentMaintenanceCases",
        "getEquipmentMaintenanceHistory",
        "getMaintenanceReport");
    assertThat(new HashSet<>(operations)).hasSize(operations.size());
    JsonNode schemas = document.path("components").path("schemas");
    for (String name : List.of("ReportFaultRequest", "AssignTechnicianRequest",
        "MaintenanceDetailRequest", "CompleteMaintenanceRequest")) {
      JsonNode properties = schemas.path(name).path("properties");
      assertThat(properties.isMissingNode()).as(name).isFalse();
      assertThat(properties.has("actorId")).isFalse();
      assertThat(properties.has("reportedBy")).isFalse();
      assertThat(properties.has("expectedVersion")).isFalse();
    }
    assertThat(schemas.path("MaintenanceCaseResponse").path("properties").has("version")).isTrue();
    assertThat(schemas.path("MaintenanceHistoryResponse").path("properties").has("actorId")).isTrue();
    assertThat(schemas.path("MaintenanceReportResponse").path("properties").has("averageCompletionDuration")).isTrue();
    JsonNode created = paths.path("/api/v1/maintenance/cases").path("post").path("responses").path("201");
    assertThat(created.path("content").path("application/json").path("schema").path("$ref").asText())
        .endsWith("/MaintenanceCaseResponse");
  }
}
