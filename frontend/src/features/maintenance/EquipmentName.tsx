import { Link } from 'react-router';
import type { MaintenanceCase } from './queries';
import { equipmentHistoryPath, equipmentLabel } from './format';
export function EquipmentName({ item, linked }: { item: MaintenanceCase; linked: boolean }) {
  const label = equipmentLabel(item);
  if (!linked) return label;
  return (
    <Link
      to={equipmentHistoryPath(item.equipmentId)}
      aria-label={'Maintenance history for ' + label}
    >
      {label}
    </Link>
  );
}
