import { Navigate, Outlet, useLocation } from 'react-router';
import type { ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import { ErrorState, LoadingState } from '@/components/Feedback';
export function RequireRole({ role, roles, children }: {
  role?: string;
  /** Any listed role grants this page; no implicit ADMIN privilege. */
  roles?: readonly string[];
  children?: ReactNode;
}) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading) return <LoadingState />;
  if (auth.error)
    return <ErrorState message={auth.error.message} onRetry={() => void auth.reload()} />;
  if (!auth.user)
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  if ((role && !auth.user.roles?.includes(role)) ||
      (roles && !roles.some((r) => auth.user!.roles?.includes(r))))
    return <ErrorState message="Your account does not have access to this page." />;
  return children ?? <Outlet />;
}
