import { Link } from 'react-router';
import type { ReactNode } from 'react';
import type { MaintenanceCase } from './queries';
import { equipmentLabel, personLabel, singaporeDate, statusLabel } from './format';
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
        <dt>Equipment</dt>
        <dd>{equipmentLabel(item)}</dd>
        <dt>Reported</dt>
        <dd>{singaporeDate(item.reportedAt)}</dd>
        <dt>Reporter</dt>
        <dd>{personLabel(item.reportedByName, item.reportedBy)}</dd>
        {item.assignedTo && (
          <>
            <dt>Technician</dt>
            <dd>{personLabel(item.assignedToName, item.assignedTo)}</dd>
          </>
        )}
        <dt>Version</dt>
        <dd>{item.version}</dd>
      </dl>
      {item.resolutionNote && <p>{item.resolutionNote}</p>}
      {children}
    </article>
  );
}
