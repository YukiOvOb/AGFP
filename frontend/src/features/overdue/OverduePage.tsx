import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { createColumnHelper } from '@tanstack/react-table';
import { api, message } from '@/api/client';
import type { components } from '@/api/schema';
import { PageHeader } from '@/components/ui/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { DataTable } from '@/components/ui/data-table';
import { Select } from '@/components/ui/select';
import { ErrorState, EmptyState } from '@/components/Feedback';
import { formatDateTime } from '@/lib/time';

type OverdueLoan = components['schemas']['OverdueLoan'];
const column = createColumnHelper<OverdueLoan>();
const columns = [
  column.accessor('equipmentName', { header: 'Equipment' }),
  column.accessor('borrowerName', { header: 'Borrower' }),
  column.accessor('dueAt', {
    header: 'Due · SGT',
    cell: (info) => info.getValue()
      ? <time dateTime={info.getValue()}>{formatDateTime(info.getValue()!)}</time> : '—',
  }),
  column.accessor('overdueSeconds', {
    header: 'Overdue by',
    cell: (info) => {
      const seconds = info.getValue();
      if (seconds == null) return '—';
      const days = Math.floor(seconds / 86400);
      const hours = Math.floor((seconds % 86400) / 3600);
      const minutes = Math.floor((seconds % 3600) / 60);
      return `${days}d ${hours}h ${minutes}m`;
    },
  }),
];
export function OverduePage() {
  const [page, setPage] = useState(0);
  const [order, setOrder] = useState<'desc' | 'asc'>('desc');
  const result = useQuery({
    queryKey: ['overdue', page, order],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/overdue', {
        params: { query: { page, size: 20, order } },
      });
      return data!;
    },
  });
  return (
    <div className="space-y-6">
      <PageHeader eyebrow="LOAN FOLLOW-UP" title="Overdue loans"
        description="Active loans past their due time, longest overdue first. Times are shown in Singapore time." />
      <Card>
        <CardContent className="pt-6">
          <div className="mb-4 flex items-center justify-between gap-3">
            <p className="m-0 text-sm text-muted-foreground">
              {result.data ? `${result.data.totalElements} overdue loans` : 'Loading overdue loans'}
            </p>
            <label className="flex items-center gap-2 text-sm">
              Duration
              <Select aria-label="Sort by overdue duration" value={order}
                onChange={(event) => { setOrder(event.target.value as 'desc' | 'asc'); setPage(0); }}>
                <option value="desc">Longest first</option>
                <option value="asc">Shortest first</option>
              </Select>
            </label>
          </div>
          {result.isError ? <ErrorState message={message(result.error)} onRetry={() => void result.refetch()} /> :
            <DataTable columns={columns} data={result.data?.content ?? []}
              caption="Active overdue loans" loading={result.isPending}
              empty={<EmptyState title="No overdue loans" description="All active loans are within their return time." />}
              paging={{ page, pageCount: Math.max(1, Math.ceil((result.data?.totalElements ?? 0) / 20)),
                onPageChange: setPage, busy: result.isFetching }} />}
        </CardContent>
      </Card>
    </div>
  );
}
