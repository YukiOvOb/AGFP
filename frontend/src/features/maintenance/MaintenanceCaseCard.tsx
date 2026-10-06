import { Link } from 'react-router';
import type { ReactNode } from 'react';
import type { MaintenanceCase } from './queries';
import { singaporeDate, statusLabel } from './format';
export function MaintenanceCaseCard({
  item,
  children,
}: {
  item: MaintenanceCase;
  children?: ReactNode;
}) {
  return (
    <article className="maintenance-card">
      <span className="badge">{statusLabel(item.status)}</span>
      <h3>
        <Link to={'/maintenance/cases/' + item.maintenanceCaseId}>{item.faultDescription}</Link>
      </h3>
      <dl className="maintenance-meta">
        <dt>Equipment ID</dt>
        <dd>{item.equipmentId}</dd>
        <dt>Reported</dt>
        <dd>{singaporeDate(item.reportedAt)}</dd>
        <dt>Reporter ID</dt>
        <dd>{item.reportedBy}</dd>
        <dt>Version</dt>
        <dd>{item.version}</dd>
      </dl>
      {item.resolutionNote && <p>{item.resolutionNote}</p>}
      {children}
    </article>
  );
}
