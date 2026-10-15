import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { createColumnHelper } from '@tanstack/react-table';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { api, message } from '@/api/client';
import type { components } from '@/api/schema';
import { PageHeader } from '@/components/ui/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { ErrorState, EmptyState } from '@/components/Feedback';
import { formatDateTime, localToIso } from '@/lib/time';

const column = createColumnHelper<components['schemas']['AuditEntry']>();
const columns = [
  column.accessor('occurredAt', {
    header: 'Time · SGT',
    cell: (info) => <time dateTime={info.getValue()}>{formatDateTime(info.getValue())}</time>,
  }),
  column.accessor('actorName', {
    header: 'Actor',
    cell: (info) => info.getValue() || info.row.original.actorId || 'System',
  }),
  column.accessor('action', { header: 'Action' }),
  column.accessor('entityType', { header: 'Entity' }),
  column.accessor('entityId', {
    header: 'Target',
    cell: (info) => <span className="break-all">{info.getValue()}</span>,
  }),
  column.accessor('outcome', { header: 'Outcome' }),
  column.accessor('changeSummary', { header: 'Summary', cell: (info) => info.getValue() || '—' }),
];
const schema = z
  .object({
    actorId: z.union([z.literal(''), z.uuid('Enter a valid actor UUID.')]),
    action: z.string().trim().max(100),
    entityType: z.string().trim().max(100),
    from: z.string(),
    to: z.string(),
  })
  .refine((values) => !values.from || !!localToIso(values.from), {
    path: ['from'],
    message: 'Enter a valid Singapore time.',
  })
  .refine((values) => !values.to || !!localToIso(values.to), {
    path: ['to'],
    message: 'Enter a valid Singapore time.',
  })
  .refine((values) => !values.from || !values.to || values.from < values.to, {
    path: ['to'],
    message: 'End time must be after start time.',
  });
type Filters = z.infer<typeof schema>;
const defaults: Filters = { actorId: '', action: '', entityType: '', from: '', to: '' };
export function AuditPage() {
  const [filters, setFilters] = useState(defaults);
  const [page, setPage] = useState(0);
  const form = useForm<Filters>({ resolver: zodResolver(schema), defaultValues: defaults });
  const audit = useQuery({
    queryKey: ['admin-audit', filters, page],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/admin/audit', {
        params: {
          query: {
            actorId: filters.actorId || undefined,
            action: filters.action || undefined,
            entityType: filters.entityType || undefined,
            from: localToIso(filters.from) ?? undefined,
            to: localToIso(filters.to) ?? undefined,
            page,
            size: 20,
          },
        },
      });
      return data!;
    },
  });
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="ADMINISTRATION"
        title="Audit log"
        description="Read-only history, newest first. Start time is inclusive and end time exclusive; times are shown in Singapore time."
      />
      <Card>
        <CardContent className="pt-6 space-y-4">
          <form
            className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3"
            onSubmit={(event) =>
              void form.handleSubmit((values) => {
                setFilters(values);
                setPage(0);
              })(event)
            }
          >
            <Field label="Actor UUID" error={form.formState.errors.actorId?.message}>
              <Input {...form.register('actorId')} />
            </Field>
            <Field label="Action" hint="Exact code, e.g. USER_CREATED">
              <Input maxLength={100} {...form.register('action')} />
            </Field>
            <Field label="Entity type" hint="Exact value, e.g. User">
              <Input maxLength={100} {...form.register('entityType')} />
            </Field>
            <Field label="From · SGT" error={form.formState.errors.from?.message}>
              <Input type="datetime-local" {...form.register('from')} />
            </Field>
            <Field label="Until · SGT" error={form.formState.errors.to?.message}>
              <Input type="datetime-local" {...form.register('to')} />
            </Field>
            <div className="flex items-end gap-2">
              <Button type="submit">Apply filters</Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  form.reset(defaults);
                  setFilters(defaults);
                  setPage(0);
                }}
              >
                Clear
              </Button>
            </div>
          </form>
          {audit.isError ? (
            <ErrorState message={message(audit.error)} onRetry={() => void audit.refetch()} />
          ) : (
            <DataTable
              columns={columns}
              data={audit.data?.content ?? []}
              caption="Audit entries"
              loading={audit.isPending}
              empty={
                <EmptyState title="No audit entries" description="No events match these filters." />
              }
              paging={{
                page,
                pageCount: Math.max(1, Math.ceil((audit.data?.totalElements ?? 0) / 20)),
                onPageChange: setPage,
                busy: audit.isFetching,
              }}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
