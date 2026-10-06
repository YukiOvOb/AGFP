import { Navigate, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { NotificationPage } from '@/features/notification/NotificationPage';
import { EquipmentSearchPage } from '@/features/equipment/EquipmentSearchPage';
import { ReservePage } from '@/features/reservation/ReservePage';
import { MyReservationsPage } from '@/features/reservation/MyReservationsPage';
import { RequireRole } from './RequireRole';
import { Layout } from './Layout';
import { MaintenanceHomePage } from '@/features/maintenance/MaintenanceHomePage';
import { MaintenanceCasePage } from '@/features/maintenance/MaintenanceCasePage';
import { MaintenanceReportPage } from '@/features/maintenance/MaintenanceReportPage';
import '@/features/maintenance/maintenance.css';
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireRole />}>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/notifications" replace />} />
          <Route path="/notifications" element={<NotificationPage />} />
          <Route path="/equipment" element={<EquipmentSearchPage />} />
          <Route element={<RequireRole role="BORROWER" />}>
            <Route path="/equipment/:equipmentId/reserve" element={<ReservePage />} />
            <Route path="/reservations" element={<MyReservationsPage />} />
          </Route>
          <Route path="/maintenance" element={<MaintenanceHomePage />} />
          <Route path="/maintenance/cases/:caseId" element={<MaintenanceCasePage />} />
          <Route path="/maintenance/report" element={<MaintenanceReportPage />} />
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
