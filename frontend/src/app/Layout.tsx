import { Boxes, ChevronRight, LogOut } from 'lucide-react';
import { Link, NavLink, Outlet } from 'react-router';
import { useState } from 'react';
import { useAuth } from './AuthProvider';
import { visibleNavigation, type NavItem } from './navigation';
import { useUnread } from '@/features/notification/queries';
import { Button } from '@/components/ui/button';
import { message } from '@/api/client';
import { roleLabels } from '@/lib/roles';
export function Layout() {
  const { user, logout } = useAuth();
  const unread = useUnread();
  const [error, setError] = useState('');
  const [leaving, setLeaving] = useState(false);
  const items = visibleNavigation(user?.roles);
  const workspace = items.filter((item) => item.group === 'workspace');
  const manage = items.filter((item) => item.group === 'manage');
  const unreadCount = unread.data?.count ?? 0;
  async function leave() {
    setLeaving(true);
    try {
      await logout();
    } catch (e) {
      setError(message(e));
    } finally {
      setLeaving(false);
    }
  }
  const link = (item: NavItem) => (
    <NavLink key={item.to} to={item.to} end={item.end}>
      <item.icon size={19} />
      {item.label}
      {item.to === '/notifications' && unreadCount > 0 && (
        <span className="nav-count" aria-label={`${unreadCount} unread notifications`}>
          {unreadCount}
        </span>
      )}
    </NavLink>
  );
  return (
    <div className="shell">
      <aside className="sidebar">
        <Link className="brand" to="/">
          <span className="brand-mark">
            <Boxes size={23} />
          </span>
          <span>
            SERMS<small>Equipment workspace</small>
          </span>
        </Link>
        <span className="nav-label">WORKSPACE</span>
        <nav aria-label="Main navigation">{workspace.map(link)}</nav>
        {manage.length > 0 && (
          <>
            <span className="nav-label nav-label-next">MANAGE</span>
            <nav aria-label="Management">{manage.map(link)}</nav>
          </>
        )}
        <div className="sidebar-note">
          <span className="status-dot" />
          Shared equipment.
          <br />A little more organised.
        </div>
        <div className="profile">
          <div className="avatar">{user?.displayName?.slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{user?.displayName}</strong>
            <small>{roleLabels(user?.roles).join(' · ')}</small>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            SERMS <ChevronRight size={14} /> Workspace
          </div>
          <Button variant="ghost" onClick={() => void leave()} disabled={leaving}>
            <LogOut size={16} />
            Sign out
          </Button>
        </header>
        {error && (
          <p className="banner-error" role="alert">
            {error}
          </p>
        )}
        <main id="main">
          <Outlet />
        </main>
        <footer>
          Shared Equipment Reservation and Maintenance System <span>Team 07</span>
        </footer>
      </div>
    </div>
  );
}
