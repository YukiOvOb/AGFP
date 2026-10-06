import type { MaintenanceHistoryEntry } from './queries';
import { singaporeDate, statusLabel } from './format';
export function MaintenanceHistory({ entries }: { entries: MaintenanceHistoryEntry[] }) {
  return (
    <section aria-label="Maintenance history">
      <h2>Maintenance history</h2>
      {!entries.length ? (
        <p className="muted">No maintenance history yet.</p>
      ) : (
        <ol className="maintenance-timeline">
          {entries.map((entry, index) => (
            <li key={index}>
              <strong>{entry.action.replaceAll('_', ' ')}</strong>
              <p>
                {entry.fromStatus ? statusLabel(entry.fromStatus) + ' → ' : ''}
                {statusLabel(entry.toStatus)}
              </p>
              <p className="muted">
                {singaporeDate(entry.occurredAt)} · Actor: {entry.actorId}
              </p>
              {entry.detail && <p className="maintenance-text">{entry.detail}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
