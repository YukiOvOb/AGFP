package sg.edu.nus.serms.maintenance.service.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

/**
 * CUSTODIAN/ADMIN queue, assignment and arbitrary-case access is a provisional integration
 * policy: the Proposal only specifies an "authorised user". No role implies another role.
 */
@Component("maintenanceAccess")
public class MaintenanceAccessPolicy {
  public static final String OPERATORS = "hasAnyRole('CUSTODIAN','ADMIN')";
  public static final String REPORTER =
      "hasAnyRole('BORROWER','CUSTODIAN') and @maintenanceAccess.isSelf(authentication, #p1)";
  public static final String ASSIGNER =
      OPERATORS + " and @maintenanceAccess.isSelf(authentication, #p2)";
  public static final String TECHNICIAN_START =
      "hasRole('MAINTAINER') and @maintenanceAccess.isSelf(authentication, #p1)";
  public static final String TECHNICIAN_WORK =
      "hasRole('MAINTAINER') and @maintenanceAccess.isSelf(authentication, #p2)";
  public static final String OWN_CASES =
      "hasRole('MAINTAINER') and @maintenanceAccess.isSelf(authentication, #p0)";
  public static final String CASE_READER = OPERATORS
      + " or (hasRole('MAINTAINER') and @maintenanceAccess.isAssigned(authentication, #p0))";
  public static final String REPORT_READER = "hasRole('ADMIN')";

  private final MaintenanceCaseRepository cases;

  public MaintenanceAccessPolicy(MaintenanceCaseRepository cases) { this.cases = cases; }

  public boolean isSelf(Authentication authentication, UUID userId) {
    return authentication != null && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof SermsUserPrincipal me
        && me.getUserId().equals(userId);
  }

  public boolean isAssigned(Authentication authentication, UUID caseId) {
    return caseId != null && cases.findById(caseId)
        .map(item -> isSelf(authentication, item.assignedTo())).orElse(false);
  }
}
