import { Navigate, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotificationPage } from '@/features/notification/NotificationPage';
import { RequireRole } from './RequireRole';
import { Layout } from './Layout';
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireRole />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/notifications" replace />} />
          <Route path="/notifications" element={<NotificationPage />} />
          <Route
            path="*"
            element={
              <div>
                <h1>Page not found</h1>
                <a href="/notifications">Back to notifications</a>
              </div>
            }
          />
        </Route>
      </Route>
    </Routes>
  );
}
