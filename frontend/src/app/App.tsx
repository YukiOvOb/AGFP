import { lazy, Suspense } from 'react';
import { Link, Route, Routes } from 'react-router';
import { LoginPage } from '@/features/auth/LoginPage';
import { HomePage } from '@/features/home/HomePage';
import { LoanPage } from '@/features/loan/LoanPage';
import { OverduePage } from '@/features/overdue/OverduePage';
import { NotificationPage } from '@/features/notification/NotificationPage';
import { EquipmentSearchPage } from '@/features/equipment/EquipmentSearchPage';
import { ReservePage } from '@/features/reservation/ReservePage';
import { MyReservationsPage } from '@/features/reservation/MyReservationsPage';
import { RequireRole } from './RequireRole';
import { Layout } from './Layout';
import { LoadingState } from '@/components/Feedback';
import { MaintenanceHomePage } from '@/features/maintenance/MaintenanceHomePage';
import { MaintenanceCasePage } from '@/features/maintenance/MaintenanceCasePage';
import { MaintenanceReportPage } from '@/features/maintenance/MaintenanceReportPage';
import { ApprovalListPage } from '@/features/approval/ApprovalListPage';
import { ApprovalDetailPage } from '@/features/approval/ApprovalDetailPage';
import { UsersPage } from '@/features/admin/UsersPage';
import { AuditPage } from '@/features/admin/AuditPage';
// Component gallery for developers; the import is dropped from production builds.
const Gallery = import.meta.env.DEV ? lazy(() => import('@/components/ui/Gallery')) : null;
// The chart library loads only for administrators who open the reports.
const ReportsPage = lazy(() => import('@/features/report/ReportsPage'));
export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireRole />}>
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/notifications" element={<NotificationPage />} />
          <Route element={<RequireRole role={['BORROWER', 'CUSTODIAN']} />}>
            <Route path="/loans" element={<LoanPage />} />
          </Route>
          <Route element={<RequireRole role={['CUSTODIAN', 'ADMIN']} />}>
            <Route path="/overdue" element={<OverduePage />} />
          </Route>
          <Route element={<RequireRole role="APPROVER" />}>
            <Route path="/approvals" element={<ApprovalListPage />} />
            <Route path="/approvals/:reservationId" element={<ApprovalDetailPage />} />
          </Route>
          <Route path="/equipment" element={<EquipmentSearchPage />} />
          <Route element={<RequireRole role="BORROWER" />}>
            <Route path="/equipment/:equipmentId/reserve" element={<ReservePage />} />
            <Route path="/reservations" element={<MyReservationsPage />} />
          </Route>
          <Route path="/maintenance" element={<MaintenanceHomePage />} />
          <Route path="/maintenance/cases/:caseId" element={<MaintenanceCasePage />} />
          <Route path="/maintenance/report" element={<MaintenanceReportPage />} />
          <Route element={<RequireRole role="ADMIN" />}>
            <Route path="/admin/users" element={<UsersPage />} />
            <Route path="/admin/audit" element={<AuditPage />} />
            <Route
              path="/reports"
              element={
                <Suspense fallback={<LoadingState />}>
                  <ReportsPage />
                </Suspense>
              }
            />
          </Route>
          {Gallery && (
            <Route
              path="/dev/components"
              element={
                <Suspense fallback={<LoadingState />}>
                  <Gallery />
                </Suspense>
              }
            />
          )}
          <Route
            path="*"
            element={
              <div>
                <h1>Page not found</h1>
                <Link to="/">Back to the overview</Link>
              </div>
            }
          />
        </Route>
      </Route>
    </Routes>
  );
}
