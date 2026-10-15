import { Link, useParams } from 'react-router';
import { useAuth } from '@/app/AuthProvider';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { enumLabel, hasAnyRole, personLabel, singaporeDate, statusLabel, uuid } from './format';
import { useCase, useHistory } from './queries';
import { MaintenanceHistory } from './MaintenanceHistory';
import { MaintenanceActions } from './MaintenanceActions';
import { EquipmentName } from './EquipmentName';
function CaseDetail({ id }: { id: string }) {
  const { user } = useAuth();
  const query = useCase(id);
  const history = useHistory(id);
  if (query.isPending) return <LoadingState />;
  if (query.isError)
    return <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />;
  const item = query.data;
  return (
    <div className="maintenance">
      <Link to="/maintenance" className="maintenance-back">
        ← Maintenance workspace
      </Link>
      <div className="page-title">
        <div>
          <span className="eyebrow">MAINTENANCE CASE</span>
          <h1>Case details</h1>
        </div>
        <span className="badge">{statusLabel(item.status)}</span>
      </div>
      <section className="maintenance-panel">
        <h2 className="maintenance-text">{item.faultDescription}</h2>
        <dl className="maintenance-meta">
          <dt>Case ID</dt>
          <dd>{item.maintenanceCaseId}</dd>
          <dt>Equipment</dt>
          <dd>
            <EquipmentName item={item} linked={hasAnyRole(user?.roles, 'CUSTODIAN', 'ADMIN')} />
          </dd>
          <dt>Fault category</dt>
          <dd>{enumLabel(item.faultCategory)}</dd>
          <dt>Priority</dt>
          <dd>{enumLabel(item.priority)}</dd>
          <dt>Reporter</dt>
          <dd>{personLabel(item.reportedByName, item.reportedBy)}</dd>
          <dt>Assigned technician</dt>
          <dd>
            {item.assignedTo ? personLabel(item.assignedToName, item.assignedTo) : 'Not assigned'}
          </dd>
          <dt>Reported time</dt>
          <dd>{singaporeDate(item.reportedAt)}</dd>
          {item.loanId && (
            <>
              <dt>Loan ID</dt>
              <dd>{item.loanId}</dd>
            </>
          )}
          <dt>Version</dt>
          <dd>{item.version}</dd>
          {(item.status === 'RESOLVED' || item.status === 'UNREPAIRABLE') && (
            <>
              <dt>Resolution note</dt>
              <dd className="maintenance-text">{item.resolutionNote}</dd>
              <dt>Completed time</dt>
              <dd>{singaporeDate(item.resolvedAt)}</dd>
            </>
          )}
        </dl>
      </section>
      {hasAnyRole(user?.roles, 'MAINTAINER') && item.assignedTo === user?.userId && (
        <MaintenanceActions item={item} />
      )}
      <div className="maintenance-panel">
        {history.isPending ? (
          <LoadingState />
        ) : history.isError ? (
          <ErrorState message={message(history.error)} onRetry={() => void history.refetch()} />
        ) : (
          <MaintenanceHistory entries={history.data} />
        )}
      </div>
    </div>
  );
}
export function MaintenanceCasePage() {
  const { caseId = '' } = useParams();
  const { user } = useAuth();
  if (!hasAnyRole(user?.roles, 'CUSTODIAN', 'ADMIN', 'MAINTAINER'))
    return <ErrorState message="Your account does not have access to maintenance case details." />;
  if (!uuid.safeParse(caseId).success)
    return <ErrorState message="Enter a valid maintenance case UUID." />;
  return <CaseDetail id={caseId} />;
}
