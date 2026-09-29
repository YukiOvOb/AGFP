package sg.edu.nus.serms.maintenance.repository;

import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.FaultReport;

public interface FaultReportRepository {
  void save(FaultReport report);

  Optional<FaultReport> findById(UUID faultReportId);
}
