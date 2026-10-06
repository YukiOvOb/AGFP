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
        };
        MaintenanceCaseResponse: {
            /** Format: uuid */
            maintenanceCaseId: string;
            /** Format: uuid */
            equipmentId: string;
            /** Format: uuid */
            reportedBy: string;
            /** Format: uuid */
            assignedTo: string | null;
            /** Format: uuid */
            loanId: string | null;
            /** @enum {string} */
            status: "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "UNREPAIRABLE";
            faultDescription: string;
            resolutionNote: string | null;
            /** Format: date-time */
            reportedAt: string;
            /** Format: date-time */
            resolvedAt: string | null;
            /**
             * Format: int32
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
            /** Format: date-time */
            occurredAt: string;
            detail: string | null;
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
}
