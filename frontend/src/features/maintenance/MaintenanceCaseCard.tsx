import { Link } from 'react-router';
import type { ReactNode } from 'react';
import type { MaintenanceCase } from './queries';
import { enumLabel, personLabel, singaporeDate, statusLabel } from './format';
import { EquipmentName } from './EquipmentName';
export function MaintenanceCaseCard({
  item,
  historyLink = false,
  children,
}: {
  item: MaintenanceCase;
  /** Link the equipment to the operator lookup; only for views that operators see. */
  historyLink?: boolean;
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
        <dd>
          <EquipmentName item={item} linked={historyLink} />
        </dd>
        <dt>Category</dt>
        <dd>{enumLabel(item.faultCategory)}</dd>
        <dt>Priority</dt>
        <dd>{enumLabel(item.priority)}</dd>
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
