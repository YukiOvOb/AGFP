import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { ChevronLeft, ChevronRight, ClipboardCheck, RefreshCw } from 'lucide-react';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/Feedback';
import { message } from '@/api/client';
import { useAuth } from '@/app/AuthProvider';
import { singaporeTime, statusLabel } from './format';
import { usePendingApprovals, type ReservationView } from './queries';

const columns = createColumnHelper<ReservationView>();
export function ApprovalListPage() {
  const [page, setPage] = useState(0);
  const { user } = useAuth();
  const query = usePendingApprovals(page);
  const definitions = useMemo(
    () => [
      columns.accessor('equipment', {
        header: 'Equipment',
        cell: ({ row }) => (
          <div>
            <strong>{row.original.equipment?.name ?? 'Equipment'}</strong>
            <small>
              {row.original.equipment?.assetTag} · {row.original.equipment?.category}
            </small>
          </div>
        ),
      }),
      columns.accessor('requesterName', {
        header: 'Applicant',
        cell: ({ row }) => (
          <div>
            {row.original.requesterName ?? 'Applicant'}
            {row.original.requesterId === user?.userId && <small>Your request</small>}
          </div>
        ),
      }),
      columns.accessor('startAt', {
        header: 'Reserved time · SGT',
        cell: ({ row }) => (
          <div>
            {singaporeTime(row.original.startAt)}
            <small>to {singaporeTime(row.original.endAt)}</small>
          </div>
        ),
      }),
      columns.accessor('status', {
        header: 'Status',
        cell: ({ row }) => (
          <div>
            <span className="badge">{statusLabel(row.original.status)}</span>
            {row.original.expired && (
              <small className="approval-expired">Expired · rejection allowed</small>
            )}
          </div>
        ),
      }),
      columns.display({
        id: 'review',
        header: 'Action',
        cell: ({ row }) =>
          row.original.reservationId ? (
            <Button asChild variant="outline">
              <Link
                to={`/approvals/${row.original.reservationId}`}
                aria-label={`Review request for ${row.original.equipment?.assetTag ?? 'equipment'}`}
              >
                Review request
              </Link>
            </Button>
          ) : (
            <span>Unavailable</span>
          ),
      }),
    ],
    [user?.userId],
  );
  const table = useReactTable({
    data: query.data?.content ?? [],
    columns: definitions,
    getCoreRowModel: getCoreRowModel(),
  });
  return (
    <div className="approval">
      <div className="page-title">
        <div>
          <p className="eyebrow">REVIEW EQUIPMENT REQUESTS</p>
          <h1>
            Approvals<span className="title-dot">.</span>
          </h1>
          <p className="muted">Review a reservation before it is confirmed.</p>
        </div>
        <Button variant="outline" disabled={query.isFetching} onClick={() => void query.refetch()}>
          <RefreshCw size={16} />
          Refresh
        </Button>
      </div>
      <section className="approval-panel" aria-labelledby="approval-queue-title">
        <div className="approval-panel-heading">
          <ClipboardCheck size={22} />
          <div>
            <h2 id="approval-queue-title">Pending reservations</h2>
            <p className="muted">Ordered by start time. Expired requests can still be rejected.</p>
          </div>
          {query.data && <span className="badge">{query.data.totalElements ?? 0} pending</span>}
        </div>
        {query.isPending ? (
          <LoadingState />
        ) : query.isError ? (
          <ErrorState message={message(query.error)} onRetry={() => void query.refetch()} />
        ) : !query.data.content?.length ? (
          <div className="state">
            <ClipboardCheck size={28} />
            <h3>No pending reservations</h3>
            <p>New equipment requests will appear here when they need approval.</p>
          </div>
        ) : (
          <div className="approval-table-scroll">
            <table>
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
        <nav className="approval-pagination" aria-label="Approval pages">
          <Button
            variant="outline"
            disabled={page === 0 || query.isFetching}
            onClick={() => setPage(page - 1)}
          >
            <ChevronLeft size={16} />
            Previous
          </Button>
          <span>
            Page {page + 1} of {Math.max(query.data?.totalPages ?? 1, 1)}
          </span>
          <Button
            variant="outline"
            disabled={query.isFetching || !query.data || page + 1 >= (query.data.totalPages ?? 0)}
            onClick={() => setPage(page + 1)}
          >
            Next
            <ChevronRight size={16} />
          </Button>
        </nav>
      </section>
    </div>
  );
}
