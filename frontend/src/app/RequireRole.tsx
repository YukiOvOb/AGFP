import { Navigate, Outlet, useLocation } from 'react-router';
import type { ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import { ErrorState, LoadingState } from '@/components/Feedback';
export function RequireRole({ role, children }: { role?: string; children?: ReactNode }) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading) return <LoadingState />;
  if (auth.error)
    return <ErrorState message={auth.error.message} onRetry={() => void auth.reload()} />;
  if (!auth.user)
    return <Navigate to="/login" state={{ from: location.pathname + location.search }} replace />;
  if (role && !auth.user.roles?.includes(role))
    return <ErrorState message="Your account does not have access to this page." />;
  return children ?? <Outlet />;
}
