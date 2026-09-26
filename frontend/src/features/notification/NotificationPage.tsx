import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from '@tanstack/react-table';
import { Bell, Check, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import { formatInTimeZone } from 'date-fns-tz';
import { api, message } from '@/api/client';
import type { components } from '@/api/schema';
import { Button } from '@/components/ui/button';
import { EmptyState, ErrorState, LoadingState } from '@/components/Feedback';
import { notificationKeys, useUnread } from './queries';
type Notification = components['schemas']['NotificationView'];
const helper = createColumnHelper<Notification>();
const labels: Record<string, string> = {
  DUE_SOON: 'Due soon',
  OVERDUE: 'Overdue reminder',
  BUSINESS_EVENT: 'Reservation update',
};
export function NotificationPage() {
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('deliveredAt,desc');
  const client = useQueryClient();
  const unread = useUnread();
  const notifications = useQuery({
    queryKey: [...notificationKeys.all, 'list', page, sort],
    queryFn: async () => {
      const { data } = await api.GET('/api/v1/notifications', {
        params: { query: { page, size: 20, sort } },
      });
      return data!;
    },
  });
  const read = useMutation({
    mutationFn: async (id: string) => {
      await api.POST('/api/v1/notifications/{id}/read', { params: { path: { id } } });
    },
    onSuccess: () => client.invalidateQueries({ queryKey: notificationKeys.all }),
  });
  const columns = useMemo(
    () => [
      helper.accessor('content', {
        header: 'Update',
        cell: (info) => (
          <div className="message-cell">
            <span
              className={`message-icon ${info.row.original.type === 'OVERDUE' ? 'overdue' : ''}`}
            >
              <Bell size={18} />
            </span>
            <div>
              <strong>{labels[info.row.original.type ?? ''] ?? 'Equipment update'}</strong>
              <p>{info.getValue()}</p>
            </div>
          </div>
        ),
      }),
      helper.accessor('deliveredAt', {
        header: 'Received · SGT',
        cell: (info) =>
          info.getValue() ? (
            <time dateTime={info.getValue()}>
              {formatInTimeZone(info.getValue()!, 'Asia/Singapore', 'dd MMM yyyy')}
              <small>{formatInTimeZone(info.getValue()!, 'Asia/Singapore', 'HH:mm')}</small>
            </time>
          ) : (
            '—'
          ),
      }),
      helper.accessor('readAt', {
        header: 'Status',
        cell: (info) => (
          <span className={info.getValue() ? 'badge badge-read' : 'badge'}>
            {info.getValue() ? 'Read' : 'Unread'}
          </span>
        ),
      }),
    ],
    [],
  );
  const table = useReactTable({
    data: notifications.data?.content ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  });
  return (
    <>
      <div className="page-title">
        <div>
          <p className="eyebrow">STAY IN THE LOOP</p>
          <h1>
            Notifications<span className="title-dot">.</span>
          </h1>
          <p className="muted">Your equipment updates, with the next step always in view.</p>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            void client.invalidateQueries({ queryKey: notificationKeys.all });
          }}
          disabled={notifications.isFetching}
        >
          <RefreshCw size={16} />
          Refresh
        </Button>
      </div>
      <div className="inbox-summary">
        <span className="summary-icon">
          <Bell size={23} />
        </span>
        <div>
          <strong>
            {unread.data
              ? `${unread.data.count} unread ${unread.data.count === 1 ? 'notification' : 'notifications'}`
              : 'Your notification inbox'}
          </strong>
          <p>Return reminders and reservation decisions, together.</p>
        </div>
        <span className="summary-label">PERSONAL INBOX</span>
      </div>
      {unread.isError && (
        <ErrorState message="Unread count is unavailable." onRetry={() => void unread.refetch()} />
      )}
      <section className="inbox-panel">
        <div className="panel-toolbar">
          <h2>
            All updates{notifications.data && <span>{notifications.data.totalElements}</span>}
          </h2>
          <label className="sort-label">
            Sort by
            <select
              value={sort}
              onChange={(e) => {
                setSort(e.target.value);
                setPage(0);
              }}
            >
              <option value="deliveredAt,desc">Newest first</option>
              <option value="deliveredAt,asc">Oldest first</option>
            </select>
          </label>
        </div>
        {read.isError && <ErrorState message={message(read.error)} />}{' '}
        {notifications.isPending ? (
          <LoadingState />
        ) : notifications.isError ? (
          <ErrorState
            message={message(notifications.error)}
            onRetry={() => void notifications.refetch()}
          />
        ) : !notifications.data?.content?.length ? (
          <EmptyState />
        ) : (
          <div className="table-scroll">
            <table>
              <caption className="sr-only">Delivered notifications for your account</caption>
              <thead>
                {table.getHeaderGroups().map((group) => (
                  <tr key={group.id}>
                    {group.headers.map((header) => (
                      <th key={header.id} scope="col">
                        {flexRender(header.column.columnDef.header, header.getContext())}
                      </th>
                    ))}
                    <th scope="col">
                      <span className="sr-only">Action</span>
                    </th>
                  </tr>
                ))}
              </thead>
              <tbody>
                {table.getRowModel().rows.map((row) => (
                  <tr key={row.id} className={!row.original.readAt ? 'unread-row' : ''}>
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                    <td>
                      {!row.original.readAt ? (
                        <Button
                          variant="ghost"
                          disabled={read.isPending}
                          onClick={() => read.mutate(row.original.id!)}
                        >
                          <Check size={15} />
                          Mark as read
                        </Button>
                      ) : (
                        <span className="reviewed">
                          <Check size={15} />
                          Reviewed
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="pagination">
          <span>
            Page {page + 1}
            {notifications.data && ` of ${Math.max(1, notifications.data.totalPages ?? 1)}`}
          </span>
          <div>
            <Button
              variant="outline"
              aria-label="Previous page"
              disabled={page === 0 || notifications.isFetching}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              aria-label="Next page"
              disabled={
                !notifications.data ||
                page + 1 >= (notifications.data.totalPages ?? 0) ||
                notifications.isFetching
              }
              onClick={() => setPage((p) => p + 1)}
            >
              <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      </section>
      <p className="inbox-note">
        All times are shown in Singapore time. Only notifications addressed to you appear here.
      </p>
    </>
  );
}
