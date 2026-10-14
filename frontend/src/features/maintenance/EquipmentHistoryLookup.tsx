import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { equipmentLabel, uuid } from './format';
import { useEquipmentCases, useEquipmentHistory } from './queries';
import { MaintenanceCaseCard } from './MaintenanceCaseCard';
import { MaintenanceHistory } from './MaintenanceHistory';
const schema = z.object({ equipmentId: uuid });
export function EquipmentHistoryLookup() {
  const [id, setId] = useState('');
  const form = useForm<{ equipmentId: string }>({
    resolver: zodResolver(schema),
    defaultValues: { equipmentId: '' },
  });
  const cases = useEquipmentCases(id);
  const history = useEquipmentHistory(id);
  return (
    <section className="maintenance-panel">
      <h2>Equipment maintenance lookup</h2>
      <p className="muted">
        Interim equipment-ID lookup. Equipment lookup will be connected when the shared equipment
        catalogue API is available.
      </p>
      <form noValidate onSubmit={form.handleSubmit(({ equipmentId }) => setId(equipmentId))}>
        <label htmlFor="lookup-equipment">Equipment ID for history</label>
        <Input
          id="lookup-equipment"
          {...form.register('equipmentId')}
          aria-invalid={!!form.formState.errors.equipmentId}
          aria-describedby="lookup-error"
        />
        <p id="lookup-error" className="field-error">
          {form.formState.errors.equipmentId?.message}
        </p>
        <Button>Look up maintenance</Button>
      </form>
      {id && (
        <div className="maintenance-lookup-results">
          <h3>{cases.data?.[0] ? equipmentLabel(cases.data[0]) : 'Equipment ' + id}</h3>
          {cases.isPending ? (
            <LoadingState />
          ) : cases.isError ? (
            <ErrorState message={message(cases.error)} onRetry={() => void cases.refetch()} />
          ) : !cases.data.length ? (
            <p>No maintenance cases for this equipment.</p>
          ) : (
            <div className="maintenance-grid">
              {cases.data.map((item) => (
                <MaintenanceCaseCard item={item} key={item.maintenanceCaseId} />
              ))}
            </div>
          )}
          {history.isPending ? (
            <LoadingState />
          ) : history.isError ? (
            <ErrorState message={message(history.error)} onRetry={() => void history.refetch()} />
          ) : (
            <MaintenanceHistory entries={history.data} />
          )}
        </div>
      )}
    </section>
  );
}
