import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useSearchParams } from 'react-router';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { ChevronLeft, ChevronRight, PackageSearch, Search } from 'lucide-react';
import type { components } from '@/api/schema';
import { message } from '@/api/client';
import { useAuth } from '@/app/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { localToIso } from '@/lib/time';
import { equipmentStatus, toneClass } from '@/lib/status';
import { useEquipmentFilters, useEquipmentSearch } from './queries';
type Equipment = components['schemas']['EquipmentView'];
const MAX_WINDOW_MS = 90 * 24 * 3600 * 1000;
export const searchSchema = z
  .object({
    q: z.string().max(100, 'Keep the search under 100 characters.'),
    category: z.string(),
    location: z.string(),
    from: z.string(),
    to: z.string(),
    availableOnly: z.boolean(),
  })
  .superRefine((v, ctx) => {
    const from = localToIso(v.from);
    const to = localToIso(v.to);
    if (v.from && !from)
      ctx.addIssue({ code: 'custom', path: ['from'], message: 'Enter a valid start time.' });
    if (v.to && !to)
      ctx.addIssue({ code: 'custom', path: ['to'], message: 'Enter a valid end time.' });
    if (v.from && !v.to)
      ctx.addIssue({ code: 'custom', path: ['to'], message: 'Enter an end time too.' });
    if (!v.from && v.to)
      ctx.addIssue({ code: 'custom', path: ['from'], message: 'Enter a start time too.' });
    if (from && to) {
      const span = Date.parse(to) - Date.parse(from);
      if (span <= 0)
        ctx.addIssue({ code: 'custom', path: ['to'], message: 'The end must be after the start.' });
      else if (span > MAX_WINDOW_MS)
        ctx.addIssue({
          code: 'custom',
          path: ['to'],
          message: 'Search at most 90 days at a time.',
        });
    }
    if (v.availableOnly && !(v.from && v.to))
      ctx.addIssue({
        code: 'custom',
        path: ['availableOnly'],
        message: 'Choose a time window to see only free equipment.',
      });
  });
type SearchForm = z.infer<typeof searchSchema>;
const helper = createColumnHelper<Equipment>();
/** UC01 step 1: find equipment by name, category, location and a time window. */
export function EquipmentSearchPage() {
  const [params, setParams] = useSearchParams();
  const { user } = useAuth();
  const isBorrower = !!user?.roles?.includes('BORROWER');
  const canReportFault = !!user?.roles?.some((role) => ['BORROWER', 'CUSTODIAN'].includes(role));
  const current: SearchForm = {
    q: params.get('q') ?? '',
    category: params.get('category') ?? '',
    location: params.get('location') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    availableOnly: params.get('available') === '1',
  };
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);
  const valid = searchSchema.safeParse(current).success;
  const fromIso = localToIso(current.from) ?? undefined;
  const toIso = localToIso(current.to) ?? undefined;
  const filters = useEquipmentFilters();
  const results = useEquipmentSearch({
    q: current.q || undefined,
    category: current.category || undefined,
    location: current.location || undefined,
    from: valid ? fromIso : undefined,
    to: valid ? toIso : undefined,
    availableOnly: valid && current.availableOnly ? true : undefined,
    page,
  });
  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<SearchForm>({ resolver: zodResolver(searchSchema), values: current });
  const hasWindow = !!(watch('from') && watch('to'));
  function search(values: SearchForm) {
    const next = new URLSearchParams();
    if (values.q.trim()) next.set('q', values.q.trim());
    if (values.category) next.set('category', values.category);
    if (values.location) next.set('location', values.location);
    if (values.from) next.set('from', values.from);
    if (values.to) next.set('to', values.to);
    if (values.availableOnly) next.set('available', '1');
    setParams(next);
  }
  function goTo(nextPage: number) {
    const next = new URLSearchParams(params);
    next.set('page', String(nextPage));
    setParams(next);
  }
  const windowQuery =
    current.from && current.to && valid
      ? `?from=${encodeURIComponent(current.from)}&to=${encodeURIComponent(current.to)}`
      : '';
  const columns = useMemo(
    () => [
      helper.accessor('name', {
        header: 'Equipment',
        cell: (info) => (
          <div>
            <strong>{info.getValue()}</strong>
            <small className="cell-sub">{info.row.original.assetTag}</small>
          </div>
        ),
      }),
      helper.accessor('category', { header: 'Category' }),
      helper.accessor('location', { header: 'Location' }),
      helper.accessor('status', {
        header: 'Status',
        cell: (info) => (
          <span className={toneClass(equipmentStatus[info.getValue()].tone)}>
            {equipmentStatus[info.getValue()].label}
          </span>
        ),
      }),
      helper.accessor('availableInWindow', {
        header: 'Chosen time',
        cell: (info) =>
          info.getValue() == null ? (
            <span className="muted">Pick a time</span>
          ) : info.getValue() ? (
            <span className={toneClass('ok')}>Free</span>
          ) : (
            <span className={toneClass('bad')}>Not free</span>
          ),
      }),
      helper.display({
        id: 'action',
        header: () => <span className="sr-only">Action</span>,
        cell: (info) => {
          const e = info.row.original;
          const canTry =
            isBorrower && e.status !== 'UNDER_MAINTENANCE' && e.availableInWindow !== false;
          return (
            <div className="actions">
              <Link
                className={canTry ? 'button button-primary' : 'button button-outline'}
                to={`/equipment/${e.equipmentId}/reserve${windowQuery}`}
                aria-label={`${canTry ? 'Reserve' : 'View availability of'} ${e.name}`}
              >
                {canTry ? 'Reserve' : 'Availability'}
              </Link>
              {canReportFault && (
                <Link
                  className="button button-outline"
                  to={`/maintenance?equipmentId=${e.equipmentId}`}
                  aria-label={`Report a fault for ${e.name}`}
                >
                  Report fault
                </Link>
              )}
            </div>
          );
        },
      }),
    ],
    [isBorrower, canReportFault, windowQuery],
  );
  const table = useReactTable({
    data: results.data?.content ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });
  return (
    <>
      <div className="page-title">
        <div>
          <p className="eyebrow">SEARCH AND RESERVE</p>
          <h1>
            Find equipment<span className="title-dot">.</span>
          </h1>
          <p className="muted">
            Search by name, category or location, and add a time to see what is free.
          </p>
        </div>
      </div>
      <form
        className="filters"
        onSubmit={handleSubmit(search)}
        noValidate
        aria-label="Search equipment"
      >
        <div className="field field-wide">
          <label htmlFor="q">Name or asset tag</label>
          <Input
            id="q"
            placeholder="e.g. Canon, CAM-001"
            aria-invalid={!!errors.q}
            {...register('q')}
          />
          {errors.q && <p className="field-error">{errors.q.message}</p>}
        </div>
        <div className="field">
          <label htmlFor="category">Category</label>
          <select id="category" className="input" {...register('category')}>
            <option value="">All categories</option>
            {filters.data?.categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="location">Location</label>
          <select id="location" className="input" {...register('location')}>
            <option value="">All locations</option>
            {filters.data?.locations.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="from">From (SGT)</label>
          <Input
            id="from"
            type="datetime-local"
            aria-invalid={!!errors.from}
            aria-describedby={errors.from ? 'from-error' : undefined}
            {...register('from')}
          />
          {errors.from && (
            <p id="from-error" className="field-error">
              {errors.from.message}
            </p>
          )}
        </div>
        <div className="field">
          <label htmlFor="to">Until (SGT)</label>
          <Input
            id="to"
            type="datetime-local"
            aria-invalid={!!errors.to}
            aria-describedby={errors.to ? 'to-error' : undefined}
            {...register('to')}
          />
          {errors.to && (
            <p id="to-error" className="field-error">
              {errors.to.message}
            </p>
          )}
        </div>
        <div className="field field-actions">
          <label className="check">
            <input type="checkbox" disabled={!hasWindow} {...register('availableOnly')} />
            Only show free equipment
          </label>
          {errors.availableOnly && <p className="field-error">{errors.availableOnly.message}</p>}
          <div className="actions">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                reset({
                  q: '',
                  category: '',
                  location: '',
                  from: '',
                  to: '',
                  availableOnly: false,
                });
                setParams(new URLSearchParams());
              }}
            >
              Clear
            </Button>
            <Button type="submit">
              <Search size={16} />
              Search
            </Button>
          </div>
        </div>
      </form>
      <section className="inbox-panel">
        <div className="panel-toolbar">
          <h2>Results{results.data && <span>{results.data.totalElements}</span>}</h2>
        </div>
        {results.isPending ? (
          <LoadingState />
        ) : results.isError ? (
          <ErrorState message={message(results.error)} onRetry={() => void results.refetch()} />
        ) : !results.data.content.length ? (
          <div className="state empty">
            <span className="state-icon">
              <PackageSearch size={26} />
            </span>
            <h2>No equipment matches</h2>
            <p>Try fewer filters or a different time.</p>
          </div>
        ) : (
          <div className="table-scroll">
            <table className="data-table">
              <caption className="sr-only">Equipment matching your search</caption>
              <thead>
                {table.getHeaderGroups().map((group) => (
                  <tr key={group.id}>
                    {group.headers.map((header) => (
                      <th key={header.id} scope="col">
                        {flexRender(header.column.columnDef.header, header.getContext())}
                      </th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody>
                {table.getRowModel().rows.map((row) => (
                  <tr key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="pagination">
          <span>
            Page {page + 1}
            {results.data && ` of ${Math.max(1, results.data.totalPages)}`}
          </span>
          <div>
            <Button
              variant="outline"
              aria-label="Previous page"
              disabled={page === 0 || results.isFetching}
              onClick={() => goTo(page - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              aria-label="Next page"
              disabled={!results.data || page + 1 >= results.data.totalPages || results.isFetching}
              onClick={() => goTo(page + 1)}
            >
              <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      </section>
      <p className="inbox-note">
        Times are Singapore time. “Free” means nobody holds the slot and the equipment is not under
        repair right now; the final check happens when you reserve.
      </p>
    </>
  );
}
