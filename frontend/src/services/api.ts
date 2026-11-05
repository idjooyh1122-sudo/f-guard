import type * as T from '../types/api';

export class ApiRequestError extends Error {
  constructor(public status: number, public code: string, message: string, public details: string[] = []) {
    super(message);
  }
}

async function request<R>(path: string, init?: RequestInit): Promise<R> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      ...init,
      headers: init?.body instanceof FormData ? init?.headers : { 'Content-Type': 'application/json', ...init?.headers },
    });
  } catch {
    throw new ApiRequestError(0, 'NETWORK', '서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인하세요.');
  }
  if (res.status === 204) return undefined as R;
  const text = await res.text();
  const body = text ? JSON.parse(text) : undefined;
  if (!res.ok) {
    const e = body as Partial<T.ApiError> | undefined;
    throw new ApiRequestError(res.status, e?.code ?? 'HTTP_' + res.status, e?.message ?? `요청 실패 (${res.status})`, e?.details ?? []);
  }
  return body as R;
}

const json = (method: string, data?: unknown): RequestInit => ({ method, body: data === undefined ? undefined : JSON.stringify(data) });

export const api = {
  dashboard: () => request<T.Dashboard>('/dashboard'),
  seedDemo: () => request<{ createdCourses: number }>('/demo/seed', json('POST')),

  courses: () => request<T.Course[]>('/courses'),
  course: (id: number) => request<T.Course>(`/courses/${id}`),
  createCourse: (c: T.CourseRequest) => request<T.Course>('/courses', json('POST', c)),
  updateCourse: (id: number, c: T.CourseRequest) => request<T.Course>(`/courses/${id}`, json('PUT', c)),
  deleteCourse: (id: number) => request<void>(`/courses/${id}`, json('DELETE')),

  attendance: (courseId: number) => request<T.AttendanceRecord[]>(`/courses/${courseId}/attendance`),
  attendanceSummary: (courseId: number) => request<T.AttendanceSummary>(`/courses/${courseId}/attendance/summary`),
  upsertAttendance: (courseId: number, r: { classDate: string; status: T.AttendanceStatus; note?: string }) =>
    request<T.AttendanceRecord>(`/courses/${courseId}/attendance`, json('PUT', r)),
  deleteAttendance: (courseId: number, id: number) => request<void>(`/courses/${courseId}/attendance/${id}`, json('DELETE')),

  allAssignments: () => request<T.Assignment[]>('/assignments'),
  assignments: (courseId: number) => request<T.Assignment[]>(`/courses/${courseId}/assignments`),
  createAssignment: (courseId: number, a: T.AssignmentRequest) => request<T.Assignment>(`/courses/${courseId}/assignments`, json('POST', a)),
  updateAssignment: (id: number, a: T.AssignmentRequest) => request<T.Assignment>(`/assignments/${id}`, json('PUT', a)),
  deleteAssignment: (id: number) => request<void>(`/assignments/${id}`, json('DELETE')),

  diagnosisAll: () => request<T.CourseDiagnosis[]>('/diagnosis'),
  diagnosis: (courseId: number) => request<T.CourseDiagnosis>(`/courses/${courseId}/diagnosis`),
  whatIf: (courseId: number, w: { extraAbsences: number; extraLates: number; missAssignmentIds: number[] }) =>
    request<T.CourseDiagnosis>(`/courses/${courseId}/diagnosis/what-if`, json('POST', w)),
  explainDiagnosis: (courseId: number) => request<T.Explanation>(`/courses/${courseId}/diagnosis/explain`, json('POST')),

  lateness: (courseId: number, date?: string) =>
    request<T.LatenessResponse>(`/courses/${courseId}/lateness${date ? `?date=${date}` : ''}`),
  explainLateness: (courseId: number, date?: string) =>
    request<T.Explanation>(`/courses/${courseId}/lateness/explain${date ? `?date=${date}` : ''}`, json('POST')),

  analyzeSyllabus: (file: File, courseId?: number) => {
    const fd = new FormData();
    fd.append('file', file);
    return request<T.SyllabusAnalysis>(`/syllabus/analyze${courseId ? `?courseId=${courseId}` : ''}`, { method: 'POST', body: fd });
  },
  syllabusList: () => request<T.SyllabusAnalysis[]>('/syllabus'),
  confirmSyllabus: (id: number, result: T.SyllabusResult, courseId?: number | null) =>
    request<T.SyllabusAnalysis>(`/syllabus/${id}/confirm`, json('POST', { result, courseId: courseId ?? null })),

  excuse: (r: T.ExcuseRequest) => request<T.ExcuseResponse>('/excuse', json('POST', r)),

  googleStatus: () => request<T.GoogleStatus>('/calendar/google/status'),
  googleAuthUrl: () => request<{ url: string }>('/calendar/google/auth-url'),
  googleDisconnect: () => request<void>('/calendar/google', json('DELETE')),
  syncCalendar: (assignmentIds: number[]) => request<T.SyncItemResult[]>('/calendar/sync', json('POST', { assignmentIds })),

  settings: () => request<T.Settings>('/settings'),
  updateSettings: (s: T.Settings) => request<T.Settings>('/settings', json('PUT', s)),
  integrations: () => request<T.IntegrationStatus>('/integrations'),
};
