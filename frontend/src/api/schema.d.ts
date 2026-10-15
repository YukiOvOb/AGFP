export interface paths {
    "/api/v1/reservations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Reserve equipment for a time slot */
        post: operations["createReservation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reservations/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a pending or confirmed reservation */
        post: operations["cancelReservation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["markNotificationRead"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reportMaintenanceFault"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/unrepairable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["markMaintenanceUnrepairable"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/start": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["startMaintenance"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/resolve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["resolveMaintenanceCase"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/repair-actions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["recordMaintenanceRepairAction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/notes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["addMaintenanceNote"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/diagnosis": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["recordMaintenanceDiagnosis"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/assignment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["assignMaintenanceTechnician"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/loans": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listLoans"];
        put?: never;
        post: operations["checkoutEquipment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/loans/{loanId}/return": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["receiveLoanReturn"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/approvals/{reservationId}/decision": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["decideReservation"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminListUsers"];
        put?: never;
        post: operations["adminCreateUser"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/users/{userId}/password-reset": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["adminResetUserPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/users/{userId}/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["adminUpdateUserStatus"];
        trace?: never;
    };
    "/api/v1/admin/users/{userId}/roles": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["adminUpdateUserRoles"];
        trace?: never;
    };
    "/api/v1/admin/users/{userId}/display-name": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["adminUpdateDisplayName"];
        trace?: never;
    };
    "/api/v1/reservations/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** One of the signed-in borrower's reservations */
        get: operations["getReservation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reservations/mine": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The signed-in borrower's reservations */
        get: operations["myReservations"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Reservations, utilisation, overdue and maintenance figures for one period */
        get: operations["getOperationsReport"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/overdue": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listOverdueLoans"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listNotifications"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/notifications/unread-count": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["unreadCount"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/technicians": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listMaintenanceTechnicians"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/report": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getMaintenanceReport"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/equipment/{equipmentId}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getEquipmentMaintenanceHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/equipment/{equipmentId}/cases": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listEquipmentMaintenanceCases"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getMaintenanceCase"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/{caseId}/history": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getMaintenanceCaseHistory"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/open": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listOpenMaintenanceCases"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/maintenance/cases/assigned": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listMyMaintenanceCases"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/loans/{loanId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getLoan"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/loans/checkouts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listCheckoutQueue"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/equipment": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Search equipment by text, category, location and time window */
        get: operations["searchEquipment"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/equipment/{id}/availability": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Held slots of one item inside a window */
        get: operations["equipmentAvailability"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/equipment/filters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Categories and locations for the search form */
        get: operations["equipmentFilters"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["currentUser"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/csrf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["csrf"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/approvals/{reservationId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["getApprovalReservation"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/approvals/pending": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listPendingApprovals"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminListAudit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        CreateReservationRequest: {
            /** Format: uuid */
            equipmentId: string;
            /** Format: date-time */
            startAt: string;
            /** Format: date-time */
            endAt: string;
            purpose?: string;
        };
        ReservationView: {
            /** Format: uuid */
            reservationId: string;
            /** Format: uuid */
            equipmentId: string;
            assetTag: string;
            equipmentName: string;
            location: string;
            /** Format: date-time */
            startAt: string;
            /** Format: date-time */
            endAt: string;
            /** @enum {string} */
            status: "PENDING_APPROVAL" | "CONFIRMED" | "REJECTED" | "CANCELLED" | "FULFILLED";
            purpose?: string | null;
            /** Format: date-time */
            createdAt: string;
            /** Format: int64 */
            version: number;
            cancellable: boolean;
        };
        ReportFaultRequest: {
            /** Format: uuid */
            equipmentId: string;
            /** Format: uuid */
            loanId?: string | null;
            faultDescription: string;
            /**
             * @description Defaults to OTHER
             * @enum {string|null}
             */
            faultCategory?: "MECHANICAL" | "ELECTRICAL" | "SOFTWARE" | "PHYSICAL_DAMAGE" | "MISSING_PART" | "OTHER" | null;
            /**
             * @description Defaults to MEDIUM
             * @enum {string|null}
             */
            priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT" | null;
        };
        MaintenanceCaseResponse: {
            /** Format: uuid */
            maintenanceCaseId: string;
            /** Format: uuid */
            equipmentId: string;
            /** @description Null only when the equipment record cannot be read */
            equipmentName: string | null;
            /** @description Null only when the equipment record cannot be read */
            equipmentAssetTag: string | null;
            /** Format: uuid */
            reportedBy: string;
            /** @description Reporter display name; null only when the user cannot be read */
            reportedByName: string | null;
            /** Format: uuid */
            assignedTo: string | null;
            /** @description Technician display name; null when unassigned */
            assignedToName: string | null;
            /** Format: uuid */
            loanId: string | null;
            /** @enum {string} */
            status: "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "UNREPAIRABLE";
            faultDescription: string;
            /** @enum {string} */
            faultCategory: "MECHANICAL" | "ELECTRICAL" | "SOFTWARE" | "PHYSICAL_DAMAGE" | "MISSING_PART" | "OTHER";
            /** @enum {string} */
            priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
            resolutionNote: string | null;
            /** Format: date-time */
            reportedAt: string;
            /** Format: date-time */
            resolvedAt: string | null;
            /**
             * Format: int64
             * @description Server-side version; mutations do not support If-Match or expectedVersion
             */
            version: number;
        };
        CompleteMaintenanceRequest: {
            resolutionNote: string;
        };
        MaintenanceDetailRequest: {
            detail: string;
        };
        AssignTechnicianRequest: {
            /** Format: uuid */
            technicianId: string;
        };
        CheckoutRequest: {
            /** Format: uuid */
            reservationId: string;
            /** Format: uuid */
            borrowerId: string;
            /** Format: int64 */
            expectedVersion: number;
            identityConfirmed?: boolean;
        };
        LoanView: {
            /** Format: uuid */
            loanId?: string;
            /** Format: uuid */
            reservationId?: string;
            /** Format: uuid */
            equipmentId?: string;
            equipmentName?: string;
            /** Format: uuid */
            borrowerId?: string;
            borrowerName?: string;
            /** Format: uuid */
            checkoutBy?: string;
            /** Format: uuid */
            returnBy?: string;
            /** Format: date-time */
            checkedOutAt?: string;
            /** Format: date-time */
            dueAt?: string;
            /** Format: date-time */
            returnedAt?: string;
            status?: string;
            returnCondition?: string;
            returnNote?: string;
            /** Format: int64 */
            version?: number;
            overdue?: boolean;
        };
        ReturnRequest: {
            /** Format: int64 */
            expectedVersion: number;
            condition: string;
            note?: string;
        };
        LoginRequest: {
            /** Format: email */
            email: string;
            password: string;
        };
        CurrentUser: {
            /** Format: uuid */
            userId: string;
            email: string;
            displayName: string;
            roles: string[];
        };
        DecisionRequest: {
            /** @enum {string} */
            decision: "APPROVED" | "REJECTED";
            comment?: string;
            /** Format: int64 */
            expectedVersion: number;
        };
        ApprovalReservationSnapshot: {
            /** Format: uuid */
            reservationId?: string;
            /** Format: uuid */
            requesterId?: string;
            requesterName?: string;
            equipment?: components["schemas"]["EquipmentSummary"];
            /** Format: date-time */
            startAt?: string;
            /** Format: date-time */
            endAt?: string;
            purpose?: string;
            status?: string;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            expired?: boolean;
        };
        DecisionOutcomeResponse: {
            reservation?: components["schemas"]["ApprovalReservationSnapshot"];
            decision?: components["schemas"]["DecisionResponse"];
        };
        DecisionResponse: {
            /** Format: uuid */
            approvalId?: string;
            /** Format: uuid */
            reservationId?: string;
            /** Format: uuid */
            approverId?: string;
            /** @enum {string} */
            decision?: "APPROVED" | "REJECTED";
            comment?: string;
            /** Format: date-time */
            decidedAt?: string;
        };
        EquipmentSummary: {
            /** Format: uuid */
            equipmentId?: string;
            assetTag?: string;
            name?: string;
            category?: string;
        };
        CreateUserRequest: {
            /** Format: email */
            email: string;
            displayName: string;
            initialPassword: string;
            roles: ("BORROWER" | "APPROVER" | "CUSTODIAN" | "MAINTAINER" | "ADMIN")[];
        };
        AdminUser: {
            /** Format: uuid */
            userId: string;
            email: string;
            displayName: string;
            accountStatus: string;
            roles: ("BORROWER" | "APPROVER" | "CUSTODIAN" | "MAINTAINER" | "ADMIN")[];
            /** Format: int64 */
            version: number;
        };
        ResetPasswordRequest: {
            newPassword: string;
            /** Format: int64 */
            expectedVersion: number;
        };
        UpdateStatusRequest: {
            active: boolean;
            /** Format: int64 */
            expectedVersion: number;
        };
        UpdateRolesRequest: {
            roles: ("BORROWER" | "APPROVER" | "CUSTODIAN" | "MAINTAINER" | "ADMIN")[];
            /** Format: int64 */
            expectedVersion: number;
        };
        UpdateDisplayNameRequest: {
            displayName: string;
            expectedDisplayName: string;
        };
        ReservationPage: {
            content: components["schemas"]["ReservationView"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            totalPages: number;
        };
        CategoryOverdueView: {
            category: string;
            /** Format: int64 */
            loansDue: number;
            /** Format: int64 */
            returnedLate: number;
            /** Format: int64 */
            stillOverdue: number;
        };
        CategoryReservationsView: {
            category: string;
            /** Format: int64 */
            total: number;
            /** Format: int64 */
            pendingApproval: number;
            /** Format: int64 */
            confirmed: number;
            /** Format: int64 */
            fulfilled: number;
            /** Format: int64 */
            rejected: number;
            /** Format: int64 */
            cancelled: number;
        };
        CategoryUtilisationView: {
            category: string;
            /** Format: int32 */
            equipment: number;
            /** Format: double */
            loanedHours: number;
            /**
             * Format: double
             * @description 0 to 1
             */
            utilisation: number;
        };
        EquipmentMaintenanceView: {
            /** Format: uuid */
            equipmentId: string;
            assetTag: string;
            name: string;
            category: string;
            /** Format: int64 */
            cases: number;
        };
        EquipmentUtilisationView: {
            /** Format: uuid */
            equipmentId: string;
            assetTag: string;
            name: string;
            category: string;
            status: string;
            /** Format: int64 */
            loans: number;
            /** Format: double */
            loanedHours: number;
            /**
             * Format: double
             * @description 0 to 1
             */
            utilisation: number;
        };
        MaintenanceView: {
            /** @description Current cases by stored status */
            casesByStatus: {
                [key: string]: number;
            };
            /** Format: int64 */
            reportedInPeriod: number;
            /** Format: int64 */
            completedInPeriod: number;
            /**
             * Format: double
             * @description Absent when no case was completed in the period
             */
            averageCompletionHours?: number;
        };
        OverdueLoanView: {
            /** Format: uuid */
            loanId: string;
            /** Format: uuid */
            equipmentId: string;
            assetTag: string;
            equipmentName: string;
            category: string;
            borrowerName: string;
            /** Format: date-time */
            dueAt: string;
            /** Format: double */
            overdueHours: number;
        };
        ReportView: {
            /** Format: date-time */
            from: string;
            /** Format: date-time */
            to: string;
            /**
             * Format: date-time
             * @description Utilisation and overdue frequency are measured up to this time
             */
            measuredUntil: string;
            /** Format: date-time */
            generatedAt: string;
            reservationsByCategory: components["schemas"]["CategoryReservationsView"][];
            equipmentUtilisation: components["schemas"]["EquipmentUtilisationView"][];
            utilisationByCategory: components["schemas"]["CategoryUtilisationView"][];
            overdueNow: components["schemas"]["OverdueLoanView"][];
            overdueByCategory: components["schemas"]["CategoryOverdueView"][];
            maintenance: components["schemas"]["MaintenanceView"];
            maintenanceByEquipment: components["schemas"]["EquipmentMaintenanceView"][];
        };
        OverdueLoan: {
            /** Format: uuid */
            loanId?: string;
            /** Format: uuid */
            reservationId?: string;
            borrowerName?: string;
            equipmentName?: string;
            /** Format: date-time */
            dueAt?: string;
            /** Format: int64 */
            overdueSeconds?: number;
        };
        OverduePage: {
            content?: components["schemas"]["OverdueLoan"][];
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
        };
        NotificationPage: {
            content: components["schemas"]["NotificationView"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            totalPages: number;
        };
        NotificationView: {
            id: string;
            /** @enum {string} */
            type: "DUE_SOON" | "OVERDUE" | "BUSINESS_EVENT";
            content: string;
            /** Format: date-time */
            deliveredAt: string;
            /** Format: date-time */
            readAt?: string | null;
        };
        UnreadCount: {
            /** Format: int64 */
            count: number;
        };
        MaintenanceTechnicianResponse: {
            /** Format: uuid */
            userId: string;
            email: string;
            displayName: string;
        };
        MaintenanceReportResponse: {
            /** Format: date-time */
            fromInclusive: string;
            /** Format: date-time */
            toExclusive: string;
            casesByStatus: {
                [key: string]: number;
            };
            /** Format: int64 */
            reportedCaseCount: number;
            /** Format: int64 */
            completedCaseCount: number;
            /**
             * Format: duration
             * @description ISO-8601 duration; null when the completion interval is empty
             */
            averageCompletionDuration: string | null;
        };
        MaintenanceHistoryResponse: {
            /** Format: uuid */
            maintenanceCaseId: string;
            /** Format: uuid */
            equipmentId: string;
            /** @enum {string} */
            action: "REPORT_FAULT" | "ASSIGN" | "START" | "RECORD_DIAGNOSIS" | "RECORD_REPAIR_ACTION" | "ADD_NOTE" | "RESOLVE" | "MARK_UNREPAIRABLE";
            /** @enum {string|null} */
            fromStatus: "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "UNREPAIRABLE" | null;
            /** @enum {string} */
            toStatus: "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "UNREPAIRABLE";
            /** Format: uuid */
            actorId: string;
            /** @description Actor display name; null only when the user cannot be read */
            actorName: string | null;
            /** Format: date-time */
            occurredAt: string;
            detail: string | null;
        };
        CheckoutCandidate: {
            /** Format: uuid */
            reservationId?: string;
            /** Format: uuid */
            equipmentId?: string;
            equipmentName?: string;
            /** Format: uuid */
            borrowerId?: string;
            borrowerName?: string;
            /** Format: date-time */
            startAt?: string;
            /** Format: date-time */
            endAt?: string;
            /** Format: int64 */
            version?: number;
        };
        EquipmentPage: {
            content: components["schemas"]["EquipmentView"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            totalPages: number;
        };
        EquipmentView: {
            /** Format: uuid */
            equipmentId: string;
            assetTag: string;
            name: string;
            category: string;
            location: string;
            /** @enum {string} */
            status: "AVAILABLE" | "ON_LOAN" | "UNDER_MAINTENANCE" | "RETIRED";
            requiresApproval: boolean;
            /** @description Whether the requested window can be reserved; null without a window */
            availableInWindow?: boolean | null;
            /** @description Free-text description; null when none is recorded */
            description: string | null;
            /**
             * Format: duration
             * @description ISO-8601 maximum borrowing duration for this item; null when the global limit applies
             */
            maxBorrowDuration: string | null;
        };
        AvailabilityView: {
            equipment: components["schemas"]["EquipmentView"];
            /** Format: date-time */
            from: string;
            /** Format: date-time */
            to: string;
            available: boolean;
            openMaintenance: boolean;
            busySlots: components["schemas"]["BusySlotView"][];
        };
        BusySlotView: {
            /** Format: date-time */
            startAt: string;
            /** Format: date-time */
            endAt: string;
            /** @enum {string} */
            reason: "PENDING_APPROVAL" | "RESERVED" | "ON_LOAN";
        };
        FilterOptionsView: {
            categories: string[];
            locations: string[];
        };
        DetailResponse: {
            /** Format: uuid */
            reservationId?: string;
            /** Format: uuid */
            requesterId?: string;
            requesterName?: string;
            equipment?: components["schemas"]["EquipmentSummary"];
            /** Format: date-time */
            startAt?: string;
            /** Format: date-time */
            endAt?: string;
            purpose?: string;
            status?: string;
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            expired?: boolean;
            decision?: components["schemas"]["DecisionResponse"];
        };
        PendingPageResponse: {
            content?: components["schemas"]["ApprovalReservationSnapshot"][];
            /** Format: int32 */
            number?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        AdminUserPage: {
            content: components["schemas"]["AdminUser"][];
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
        };
        AuditEntry: {
            /** Format: uuid */
            auditId: string;
            /** Format: uuid */
            actorId?: string;
            actorName?: string;
            action: string;
            entityType: string;
            /** Format: uuid */
            entityId: string;
            outcome: string;
            changeSummary?: string;
            /** Format: date-time */
            occurredAt: string;
        };
        AuditEntryPage: {
            content: components["schemas"]["AuditEntry"][];
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    createReservation: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateReservationRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReservationView"];
                };
            };
        };
    };
    cancelReservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReservationView"];
                };
            };
        };
    };
    markNotificationRead: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Marked as read */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reportMaintenanceFault: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReportFaultRequest"];
            };
        };
        responses: {
            /** @description Maintenance case created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    markMaintenanceUnrepairable: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CompleteMaintenanceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    startMaintenance: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    resolveMaintenanceCase: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CompleteMaintenanceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    recordMaintenanceRepairAction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MaintenanceDetailRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    addMaintenanceNote: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MaintenanceDetailRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    recordMaintenanceDiagnosis: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MaintenanceDetailRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    assignMaintenanceTechnician: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignTechnicianRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    listLoans: {
        parameters: {
            query?: {
                activeOnly?: boolean;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoanView"][];
                };
            };
        };
    };
    checkoutEquipment: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CheckoutRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoanView"];
                };
            };
        };
    };
    receiveLoanReturn: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                loanId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReturnRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoanView"];
                };
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Success */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CurrentUser"];
                };
            };
        };
    };
    decideReservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reservationId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DecisionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DecisionOutcomeResponse"];
                };
            };
        };
    };
    adminListUsers: {
        parameters: {
            query?: {
                search?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUserPage"];
                };
            };
        };
    };
    adminCreateUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUserRequest"];
            };
        };
        responses: {
            /** @description Account created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUser"];
                };
            };
        };
    };
    adminResetUserPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ResetPasswordRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUser"];
                };
            };
        };
    };
    adminUpdateUserStatus: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateStatusRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUser"];
                };
            };
        };
    };
    adminUpdateUserRoles: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateRolesRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUser"];
                };
            };
        };
    };
    adminUpdateDisplayName: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateDisplayNameRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminUser"];
                };
            };
        };
    };
    getReservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReservationView"];
                };
            };
        };
    };
    myReservations: {
        parameters: {
            query?: {
                status?: "PENDING_APPROVAL" | "CONFIRMED" | "REJECTED" | "CANCELLED" | "FULFILLED";
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReservationPage"];
                };
            };
        };
    };
    getOperationsReport: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReportView"];
                };
            };
        };
    };
    listOverdueLoans: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                order?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OverduePage"];
                };
            };
        };
    };
    listNotifications: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                sort?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["NotificationPage"];
                };
            };
        };
    };
    unreadCount: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnreadCount"];
                };
            };
        };
    };
    listMaintenanceTechnicians: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceTechnicianResponse"][];
                };
            };
        };
    };
    getMaintenanceReport: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceReportResponse"];
                };
            };
        };
    };
    getEquipmentMaintenanceHistory: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                equipmentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceHistoryResponse"][];
                };
            };
        };
    };
    listEquipmentMaintenanceCases: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                equipmentId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"][];
                };
            };
        };
    };
    getMaintenanceCase: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"];
                };
            };
        };
    };
    getMaintenanceCaseHistory: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                caseId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceHistoryResponse"][];
                };
            };
        };
    };
    listOpenMaintenanceCases: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"][];
                };
            };
        };
    };
    listMyMaintenanceCases: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MaintenanceCaseResponse"][];
                };
            };
        };
    };
    getLoan: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                loanId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["LoanView"];
                };
            };
        };
    };
    listCheckoutQueue: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CheckoutCandidate"][];
                };
            };
        };
    };
    searchEquipment: {
        parameters: {
            query?: {
                q?: string;
                category?: string;
                location?: string;
                from?: string;
                to?: string;
                availableOnly?: boolean;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EquipmentPage"];
                };
            };
        };
    };
    equipmentAvailability: {
        parameters: {
            query: {
                from: string;
                to: string;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AvailabilityView"];
                };
            };
        };
    };
    equipmentFilters: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FilterOptionsView"];
                };
            };
        };
    };
    currentUser: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CurrentUser"];
                };
            };
        };
    };
    csrf: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Success */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getApprovalReservation: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                reservationId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DetailResponse"];
                };
            };
        };
    };
    listPendingApprovals: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                sort?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PendingPageResponse"];
                };
            };
        };
    };
    adminListAudit: {
        parameters: {
            query?: {
                actorId?: string;
                action?: string;
                entityType?: string;
                from?: string;
                to?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AuditEntryPage"];
                };
            };
        };
    };
}
