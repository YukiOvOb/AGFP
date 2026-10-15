import {
  Bell,
  Boxes,
  CalendarCheck,
  ChartColumn,
  ClipboardCheck,
  ClockAlert,
  LayoutDashboard,
  PackagePlus,
  ScrollText,
  Search,
  UserCog,
  Wrench,
  type LucideIcon,
} from 'lucide-react';
import { hasAnyRole, type Role } from '@/lib/roles';
export type NavItem = {
  to: string;
  label: string;
  /** One line for the overview page. */
  description: string;
  icon: LucideIcon;
  /** Roles that see the entry; omit for every signed-in user. The backend still decides access. */
  roles?: readonly Role[];
  /** Only match the exact path (React Router `end`). */
  end?: boolean;
  group: 'workspace' | 'manage';
  /** False until the page exists. Set it to true in the pull request that adds the route. */
  available: boolean;
};
/**
 * The one list of pages behind the sidebar and the overview cards.
 * Reserved entries keep paths and labels stable across the tasks that build them.
 */
export const navigation: NavItem[] = [
  {
    to: '/',
    label: 'Overview',
    description: 'Your pages in one place.',
    icon: LayoutDashboard,
    end: true,
    group: 'workspace',
    available: true,
  },
  {
    to: '/equipment',
    label: 'Find equipment',
    description: 'Search by name, category, location and time, then reserve.',
    icon: Search,
    group: 'workspace',
    available: true,
  },
  {
    to: '/loans',
    label: 'Loans and returns',
    description: 'Equipment that is out, and handing it over or taking it back.',
    icon: Boxes,
    roles: ['BORROWER', 'CUSTODIAN'],
    group: 'workspace',
    available: true,
  },
  {
    to: '/reservations',
    label: 'My reservations',
    description: 'Your bookings and their status; cancel before checkout.',
    icon: CalendarCheck,
    roles: ['BORROWER'],
    group: 'workspace',
    available: true,
  },
  {
    to: '/maintenance',
    label: 'Maintenance',
    description: 'Report a fault and follow repair work.',
    icon: Wrench,
    roles: ['BORROWER', 'CUSTODIAN', 'MAINTAINER', 'ADMIN'],
    group: 'workspace',
    available: true,
  },
  {
    to: '/approvals',
    label: 'Approvals',
    description: 'Reservation requests waiting for your decision.',
    icon: ClipboardCheck,
    roles: ['APPROVER'],
    group: 'workspace',
    available: true,
  },
  {
    to: '/notifications',
    label: 'Notifications',
    description: 'Reminders and updates about your reservations and loans.',
    icon: Bell,
    group: 'workspace',
    available: true,
  },
  // Reserved for Issue #19.
  {
    to: '/overdue',
    label: 'Overdue',
    description: 'Loans past their return time and how long they are overdue.',
    icon: ClockAlert,
    roles: ['CUSTODIAN', 'ADMIN'],
    group: 'manage',
    available: true,
  },
  // Issue #17.
  {
    to: '/manage/equipment',
    label: 'Manage equipment',
    description: 'Register, update and retire equipment and categories.',
    icon: PackagePlus,
    roles: ['CUSTODIAN', 'ADMIN'],
    group: 'manage',
    available: true,
  },
  // Issue #23.
  {
    to: '/reports',
    label: 'Reports',
    description: 'Reservations, utilisation, overdue and maintenance figures.',
    icon: ChartColumn,
    roles: ['ADMIN'],
    group: 'manage',
    available: true,
  },
  // Reserved for Issue #18.
  {
    to: '/admin/users',
    label: 'Users and roles',
    description: 'Create accounts with initial roles and search existing users.',
    icon: UserCog,
    roles: ['ADMIN'],
    group: 'manage',
    available: true,
  },
  // Reserved for Issue #18.
  {
    to: '/admin/audit',
    label: 'Audit log',
    description: 'Who did what, and when.',
    icon: ScrollText,
    roles: ['ADMIN'],
    group: 'manage',
    available: true,
  },
];
/** Entries the signed-in account should see, in menu order. */
export function visibleNavigation(roles: readonly string[] | undefined, items = navigation) {
  return items.filter(
    (item) => item.available && (!item.roles || hasAnyRole(roles, ...item.roles)),
  );
}
