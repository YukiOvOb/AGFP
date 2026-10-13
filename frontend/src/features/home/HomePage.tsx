import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { useAuth } from '@/app/AuthProvider';
import { visibleNavigation } from '@/app/navigation';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/ui/page-header';
import { useUnread } from '@/features/notification/queries';
import { roleLabels } from '@/lib/roles';
/** Landing page: one card per page the signed-in account can use. */
export function HomePage() {
  const { user } = useAuth();
  const unread = useUnread();
  const unreadCount = unread.data?.count ?? 0;
  const pages = visibleNavigation(user?.roles).filter((item) => item.to !== '/');
  const roles = roleLabels(user?.roles);
  return (
    <>
      <PageHeader
        eyebrow="YOUR WORKSPACE"
        title={user?.displayName ? `Welcome, ${user.displayName}` : 'Welcome'}
        description="Everything your account can do in SERMS starts here."
      />
      {roles.length > 0 && (
        <ul aria-label="Your roles" className="m-0 mb-7 flex list-none flex-wrap gap-2 p-0">
          {roles.map((role) => (
            <li key={role}>
              <Badge>{role}</Badge>
            </li>
          ))}
        </ul>
      )}
      <nav aria-label="Your pages" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {pages.map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="group flex flex-col gap-3 rounded-lg border border-border bg-card p-6 transition-colors hover:border-primary/40 hover:bg-secondary/40"
          >
            <span className="flex items-center justify-between gap-3">
              <span className="grid size-10 place-items-center rounded-lg bg-secondary text-secondary-foreground">
                <item.icon size={20} aria-hidden="true" />
              </span>
              {item.to === '/notifications' && unreadCount > 0 && (
                <Badge tone="pending">{unreadCount} unread</Badge>
              )}
            </span>
            <span className="text-sm font-semibold">{item.label}</span>
            <span className="text-xs leading-relaxed text-muted-foreground">
              {item.description}
            </span>
            <span className="mt-auto inline-flex items-center gap-1.5 text-xs font-semibold text-primary">
              Open
              <ArrowRight
                size={14}
                aria-hidden="true"
                className="transition-transform group-hover:translate-x-0.5 motion-reduce:transition-none"
              />
            </span>
          </Link>
        ))}
      </nav>
    </>
  );
}
