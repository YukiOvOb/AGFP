import { Bell, Boxes, ChevronRight, LogOut } from 'lucide-react';
import { NavLink, Outlet } from 'react-router';
import { useState } from 'react';
import { useAuth } from './AuthProvider';
import { useUnread } from '@/features/notification/queries';
import { Button } from '@/components/ui/button';
import { message } from '@/api/client';
export function Layout() {
  const { user, logout } = useAuth();
  const unread = useUnread();
  const [error, setError] = useState('');
  const [leaving, setLeaving] = useState(false);
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
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="/">
          <span className="brand-mark">
            <Boxes size={23} />
          </span>
          <span>
            SERMS<small>Equipment workspace</small>
          </span>
        </a>
        <span className="nav-label">WORKSPACE</span>
        <nav aria-label="Main navigation">
          <NavLink to="/notifications">
            <Bell size={19} />
            Notifications
            {unread.data && (unread.data.count ?? 0) > 0 && (
              <span className="nav-count" aria-label={`${unread.data.count} unread notifications`}>
                {unread.data.count}
              </span>
            )}
          </NavLink>
        </nav>
        <div className="sidebar-note">
          <span className="status-dot" />
          Shared equipment.
          <br />A little more organised.
        </div>
        <div className="profile">
          <div className="avatar">{user?.displayName?.slice(0, 1).toUpperCase()}</div>
          <div>
            <strong>{user?.displayName}</strong>
            <small>{user?.roles?.join(' · ')}</small>
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
