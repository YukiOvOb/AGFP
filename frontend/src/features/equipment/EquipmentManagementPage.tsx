import { useState, type FormEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, message } from '@/api/client';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';

type ManagedEquipment = {
  equipmentId: string;
  assetTag: string;
  serialNumber: string | null;
  name: string;
  description: string | null;
  category: string;
  location: string;
  condition: 'GOOD' | 'FAIR' | 'POOR';
  status: string;
  requiresApproval: boolean;
  maxBorrowDuration: string | null;
  version: number;
};
type FormState = {
  assetTag: string;
  serialNumber: string;
  name: string;
  description: string;
  category: string;
  location: string;
  condition: 'GOOD' | 'FAIR' | 'POOR';
  requiresApproval: boolean;
  maxBorrowDuration: string;
};
const emptyForm: FormState = {
  assetTag: '', serialNumber: '', name: '', description: '', category: '', location: '',
  condition: 'GOOD', requiresApproval: false, maxBorrowDuration: '',
};
function fromEquipment(item: ManagedEquipment): FormState {
  return {
    assetTag: item.assetTag, serialNumber: item.serialNumber ?? '', name: item.name,
    description: item.description ?? '', category: item.category, location: item.location,
    condition: item.condition, requiresApproval: item.requiresApproval,
    maxBorrowDuration: item.maxBorrowDuration ?? '',
  };
}

export function EquipmentManagementPage() {
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ManagedEquipment | null | undefined>();
  const [form, setForm] = useState<FormState>(emptyForm);
  const [retiring, setRetiring] = useState<ManagedEquipment>();
  const [newCategory, setNewCategory] = useState('');
  const [oldCategory, setOldCategory] = useState('');
  const [renamedCategory, setRenamedCategory] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [success, setSuccess] = useState('');
  const equipment = useQuery({
    queryKey: ['managed-equipment', page],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/equipment/manage', { params: { query: { page, size: 20 } } });
      return data! as ManagedEquipment[];
    },
  });
  const categories = useQuery({
    queryKey: ['equipment-categories'],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/equipment/manage/categories');
      return data! as string[];
    },
  });

  async function refresh() {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['managed-equipment'] }),
      client.invalidateQueries({ queryKey: ['equipment-categories'] }),
      client.invalidateQueries({ queryKey: ['equipment'] }),
    ]);
  }
  function showError(cause: unknown) {
    setError(message(cause));
    setFieldErrors(Object.fromEntries(
      cause instanceof ApiError ? (cause.problem.errors ?? []).map((entry) => [entry.field, entry.message]) : [],
    ));
  }
  function openEditor(item: ManagedEquipment | null) {
    setEditing(item);
    setForm(item ? fromEquipment(item) : { ...emptyForm, category: categories.data?.[0] ?? '' });
    setError(''); setFieldErrors({}); setSuccess('');
  }
  function field(name: keyof FormState, label: string, required = false) {
    if (name === 'requiresApproval' || name === 'condition') return null;
    return <label key={name} className="field">{label}
      <input required={required} value={form[name] as string}
        onChange={(event) => setForm({ ...form, [name]: event.target.value })} />
      {fieldErrors[name] && <span className="field-error">{fieldErrors[name]}</span>}
    </label>;
  }
  async function save(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setFieldErrors({}); setSuccess('');
    const body = {
      ...form,
      serialNumber: form.serialNumber.trim() || undefined,
      description: form.description.trim() || undefined,
      maxBorrowDuration: form.maxBorrowDuration.trim() || undefined,
      expectedVersion: editing?.version,
    };
    try {
      if (editing) await api.PUT('/api/v1/equipment/manage/{id}', {
        params: { path: { id: editing.equipmentId } }, body,
      });
      else await api.POST('/api/v1/equipment/manage', { body });
      setEditing(undefined);
      setSuccess(editing ? 'Equipment updated.' : 'Equipment registered.');
      await refresh();
    } catch (cause) { showError(cause); }
    finally { setSaving(false); }
  }
  async function retire() {
    if (!retiring) return;
    setSaving(true); setError(''); setSuccess('');
    try {
      await api.POST('/api/v1/equipment/manage/{id}/retire', {
        params: { path: { id: retiring.equipmentId } },
        body: { expectedVersion: retiring.version },
      });
      setRetiring(undefined); setSuccess('Equipment retired.'); await refresh();
    } catch (cause) { showError(cause); }
    finally { setSaving(false); }
  }
  async function createCategory(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setSuccess('');
    try {
      await api.POST('/api/v1/equipment/manage/categories', { body: { name: newCategory.trim() } });
      setNewCategory(''); setSuccess('Category created.'); await refresh();
    } catch (cause) { showError(cause); }
    finally { setSaving(false); }
  }
  async function renameCategory(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setSuccess('');
    try {
      await api.PATCH('/api/v1/equipment/manage/categories/{name}', {
        params: { path: { name: oldCategory } }, body: { name: renamedCategory.trim() },
      });
      setEditing(undefined); setOldCategory(''); setRenamedCategory('');
      setSuccess('Category renamed.'); await refresh();
    } catch (cause) { showError(cause); }
    finally { setSaving(false); }
  }

  return <div className="equipment-management">
    <h1>Manage equipment</h1>
    <p>Register, update and retire equipment. Retired items stay in history.</p>
    {error && <p role="alert" className="banner-error">{error}</p>}
    {success && <p role="status">{success}</p>}
    <Button onClick={() => openEditor(null)}>Register equipment</Button>
    {editing !== undefined && <form onSubmit={(event) => void save(event)} aria-label="Equipment form">
      <h2>{editing ? `Edit ${editing.name}` : 'Register equipment'}</h2>
      {field('assetTag', 'Asset tag', true)}
      {field('serialNumber', 'Serial number')}
      {field('name', 'Name', true)}
      {field('description', 'Description')}
      <label className="field">Category
        <select required value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value })}>
          <option value="">Choose a category</option>
          {categories.data?.map((name) => <option key={name} value={name}>{name}</option>)}
        </select>
        {fieldErrors.category && <span className="field-error">{fieldErrors.category}</span>}
      </label>
      {field('location', 'Location', true)}
      <label className="field">Condition
        <select value={form.condition} onChange={(event) => setForm({ ...form, condition: event.target.value as FormState['condition'] })}>
          <option value="GOOD">Good</option><option value="FAIR">Fair</option><option value="POOR">Poor</option>
        </select>
      </label>
      <label className="field">Maximum borrowing duration (ISO-8601; blank uses the global limit)
        <input value={form.maxBorrowDuration} placeholder="PT72H"
          onChange={(event) => setForm({ ...form, maxBorrowDuration: event.target.value })} />
        {fieldErrors.maxBorrowDuration && <span className="field-error">{fieldErrors.maxBorrowDuration}</span>}
      </label>
      <label><input type="checkbox" checked={form.requiresApproval}
        onChange={(event) => setForm({ ...form, requiresApproval: event.target.checked })} /> Requires approval</label>
      <div className="actions"><Button type="submit" disabled={saving}>Save equipment</Button>
        <Button type="button" variant="ghost" onClick={() => setEditing(undefined)}>Cancel</Button></div>
    </form>}
    <section aria-label="Equipment records">
      <h2>Equipment records</h2>
      {equipment.isPending && <p>Loading equipment…</p>}
      {equipment.error && <p role="alert">{message(equipment.error)}</p>}
      {equipment.data?.length === 0 && <p>No equipment on this page.</p>}
      {equipment.data?.map((item) => <article key={item.equipmentId}>
        <strong>{item.name} ({item.assetTag})</strong>
        <p>{item.category} · {item.location} · {item.condition} · {item.status}</p>
        {item.serialNumber && <p>Serial number: {item.serialNumber}</p>}
        {item.status !== 'RETIRED' && <div className="actions">
          <Button variant="outline" onClick={() => openEditor(item)}>Edit {item.name}</Button>
          <Button variant="destructive" onClick={() => setRetiring(item)}>Retire {item.name}</Button>
        </div>}
      </article>)}
      <div className="actions">
        <Button variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous page</Button>
        <Button variant="outline" disabled={(equipment.data?.length ?? 0) < 20} onClick={() => setPage(page + 1)}>Next page</Button>
      </div>
    </section>
    <section aria-label="Categories">
      <h2>Categories</h2>
      {categories.isPending && <p>Loading categories…</p>}
      {categories.error && <p role="alert">{message(categories.error)}</p>}
      <p>{categories.data?.join(', ') || 'No categories yet.'}</p>
      <form onSubmit={(event) => void createCategory(event)}>
        <label>New category <input required value={newCategory} onChange={(event) => setNewCategory(event.target.value)} /></label>
        <Button type="submit" disabled={saving}>Create category</Button>
      </form>
      <form onSubmit={(event) => void renameCategory(event)}>
        <label>Category to rename <select required value={oldCategory} onChange={(event) => setOldCategory(event.target.value)}>
          <option value="">Choose a category</option>
          {categories.data?.map((name) => <option key={name} value={name}>{name}</option>)}
        </select></label>
        <label>New name <input required value={renamedCategory} onChange={(event) => setRenamedCategory(event.target.value)} /></label>
        <Button type="submit" disabled={saving}>Rename category</Button>
      </form>
    </section>
    <ConfirmDialog open={!!retiring} onOpenChange={(open) => { if (!open) setRetiring(undefined); }}
      title="Retire this equipment?" description="Retirement keeps the equipment and its history, but it can no longer be reserved. An item on loan must be returned first."
      confirmLabel="Retire equipment" destructive pending={saving} onConfirm={retire} />
  </div>;
}
