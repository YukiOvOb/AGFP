import { Link } from 'react-router';
import { useAuth } from '@/app/AuthProvider';
import { ErrorState } from '@/components/Feedback';
import { Button } from '@/components/ui/button';
import { hasAnyRole, workspaceRoles } from './format';
import { ReportFaultForm } from './ReportFaultForm';
import { OpenCaseQueue } from './OpenCaseQueue';
import { AssignedCaseList } from './AssignedCaseList';
import { EquipmentHistoryLookup } from './EquipmentHistoryLookup';
export function MaintenanceHomePage() {
  const { user } = useAuth();
  if (!hasAnyRole(user?.roles, ...workspaceRoles))
    return <ErrorState message="Your account does not have access to the maintenance workspace." />;
  return (
    <div className="maintenance">
      <div className="page-title">
        <div>
          <span className="eyebrow">EQUIPMENT CARE</span>
          <h1>Maintenance</h1>
          <p className="muted">Report faults, coordinate repairs and track equipment care.</p>
        </div>
        {hasAnyRole(user?.roles, 'ADMIN') && (
          <Button asChild variant="outline">
            <Link to="/maintenance/report">Maintenance report</Link>
          </Button>
        )}
      </div>
      {hasAnyRole(user?.roles, 'BORROWER', 'CUSTODIAN') && <ReportFaultForm />}
      {hasAnyRole(user?.roles, 'CUSTODIAN', 'ADMIN') && <OpenCaseQueue />}
      {hasAnyRole(user?.roles, 'MAINTAINER') && <AssignedCaseList />}
      {hasAnyRole(user?.roles, 'CUSTODIAN', 'ADMIN') && <EquipmentHistoryLookup />}
    </div>
  );
}
