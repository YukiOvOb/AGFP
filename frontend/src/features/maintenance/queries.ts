import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { components } from '@/api/schema';
export type MaintenanceCase = components['schemas']['MaintenanceCaseResponse'];
export type MaintenanceHistoryEntry = components['schemas']['MaintenanceHistoryResponse'];
export type Status = MaintenanceCase['status'];
export const maintenanceKeys = {
  all: ['maintenance'] as const,
  open: () => [...maintenanceKeys.all, 'open'] as const,
  assigned: () => [...maintenanceKeys.all, 'assigned'] as const,
  case: (id: string) => [...maintenanceKeys.all, 'case', id] as const,
  history: (id: string) => [...maintenanceKeys.all, 'history', id] as const,
  technicians: () => [...maintenanceKeys.all, 'technicians'] as const,
  equipmentCases: (id: string) => [...maintenanceKeys.all, 'equipmentCases', id] as const,
  equipmentHistory: (id: string) => [...maintenanceKeys.all, 'equipmentHistory', id] as const,
  report: (from: string, to: string) => [...maintenanceKeys.all, 'report', from, to] as const,
};
function required<T>(data: T | undefined): T {
  if (data === undefined) throw new Error('The server returned an empty response.');
  return data;
}
export function useOpenCases() {
  return useQuery({
    queryKey: maintenanceKeys.open(),
    queryFn: async () => required((await api.GET('/api/v1/maintenance/cases/open')).data),
  });
}
export function useAssignedCases() {
  return useQuery({
    queryKey: maintenanceKeys.assigned(),
    queryFn: async () => required((await api.GET('/api/v1/maintenance/cases/assigned')).data),
  });
}
export function useTechnicians() {
  return useQuery({
    queryKey: maintenanceKeys.technicians(),
    queryFn: async () => required((await api.GET('/api/v1/maintenance/technicians')).data),
  });
}
export function useCase(id: string) {
  return useQuery({
    queryKey: maintenanceKeys.case(id),
    queryFn: async () =>
      required(
        (await api.GET('/api/v1/maintenance/cases/{caseId}', { params: { path: { caseId: id } } }))
          .data,
      ),
  });
}
export function useHistory(id: string) {
  return useQuery({
    queryKey: maintenanceKeys.history(id),
    queryFn: async () =>
      required(
        (
          await api.GET('/api/v1/maintenance/cases/{caseId}/history', {
            params: { path: { caseId: id } },
          })
        ).data,
      ),
  });
}
export function useEquipmentCases(id: string) {
  return useQuery({
    queryKey: maintenanceKeys.equipmentCases(id),
    enabled: !!id,
    queryFn: async () =>
      required(
        (
          await api.GET('/api/v1/maintenance/equipment/{equipmentId}/cases', {
            params: { path: { equipmentId: id } },
          })
        ).data,
      ),
  });
}
export function useEquipmentHistory(id: string) {
  return useQuery({
    queryKey: maintenanceKeys.equipmentHistory(id),
    enabled: !!id,
    queryFn: async () =>
      required(
        (
          await api.GET('/api/v1/maintenance/equipment/{equipmentId}/history', {
            params: { path: { equipmentId: id } },
          })
        ).data,
      ),
  });
}
export function useReport(from: string, to: string) {
  return useQuery({
    queryKey: maintenanceKeys.report(from, to),
    enabled: !!from && !!to,
    queryFn: async () =>
      required(
        (await api.GET('/api/v1/maintenance/report', { params: { query: { from, to } } })).data,
      ),
  });
}
// Invalidating the feature prefix refreshes case, history, queues, equipment and reports together.
function useMaintenanceMutation<T>(
  mutationFn: (input: T) => Promise<MaintenanceCase>,
  onSuccess?: () => void,
) {
  const query = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      onSuccess?.();
      return query.invalidateQueries({ queryKey: maintenanceKeys.all });
    },
  });
}
export function useReportFault() {
  return useMaintenanceMutation(async (body: components['schemas']['ReportFaultRequest']) =>
    required((await api.POST('/api/v1/maintenance/cases', { body })).data),
  );
}
export function useAssignment(id: string, onSuccess?: () => void) {
  return useMaintenanceMutation(
    async (technicianId: string) =>
      required(
        (
          await api.POST('/api/v1/maintenance/cases/{caseId}/assignment', {
            params: { path: { caseId: id } },
            body: { technicianId },
          })
        ).data,
      ),
    onSuccess,
  );
}
export type Action =
  'start' | 'diagnosis' | 'repair-actions' | 'notes' | 'resolve' | 'unrepairable';
export function useCaseAction(id: string) {
  return useMaintenanceMutation(async ({ action, text }: { action: Action; text: string }) => {
    const params = { path: { caseId: id } };
    switch (action) {
      case 'start':
        return required(
          (await api.POST('/api/v1/maintenance/cases/{caseId}/start', { params })).data,
        );
      case 'diagnosis':
        return required(
          (
            await api.POST('/api/v1/maintenance/cases/{caseId}/diagnosis', {
              params,
              body: { detail: text },
            })
          ).data,
        );
      case 'repair-actions':
        return required(
          (
            await api.POST('/api/v1/maintenance/cases/{caseId}/repair-actions', {
              params,
              body: { detail: text },
            })
          ).data,
        );
      case 'notes':
        return required(
          (
            await api.POST('/api/v1/maintenance/cases/{caseId}/notes', {
              params,
              body: { detail: text },
            })
          ).data,
        );
      case 'resolve':
        return required(
          (
            await api.POST('/api/v1/maintenance/cases/{caseId}/resolve', {
              params,
              body: { resolutionNote: text },
            })
          ).data,
        );
      case 'unrepairable':
        return required(
          (
            await api.POST('/api/v1/maintenance/cases/{caseId}/unrepairable', {
              params,
              body: { resolutionNote: text },
            })
          ).data,
        );
    }
  });
}
