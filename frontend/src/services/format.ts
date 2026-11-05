import type { AttendanceStatus, ComponentType, LatenessLevel, RiskStatus } from '../types/api';

export const DAY_LABELS = ['', '월', '화', '수', '목', '금', '토', '일'];

export const RISK_LABEL: Record<RiskStatus, string> = {
  SAFE: '안전',
  LOW: '낮음',
  MEDIUM: '주의',
  HIGH: '위험',
  F_CONFIRMED: 'F 확정',
  CANNOT_CALCULATE: '계산 불가',
};

export const RISK_TONE: Record<RiskStatus, Tone> = {
  SAFE: 'good',
  LOW: 'good',
  MEDIUM: 'warn',
  HIGH: 'bad',
  F_CONFIRMED: 'critical',
  CANNOT_CALCULATE: 'neutral',
};

export const LATENESS_LABEL: Record<LatenessLevel, string> = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음' };
export const LATENESS_TONE: Record<LatenessLevel, Tone> = { LOW: 'good', MEDIUM: 'warn', HIGH: 'bad', CRITICAL: 'critical' };

export const ATTENDANCE_LABEL: Record<AttendanceStatus, string> = { ATTENDED: '출석', LATE: '지각', ABSENT: '결석', EXCUSED: '공결' };

export const COMPONENT_LABEL: Record<ComponentType, string> = {
  ATTENDANCE: '출석', ASSIGNMENT: '과제', MIDTERM: '중간고사', FINAL: '기말고사', QUIZ: '퀴즈', OTHER: '기타',
};

export type Tone = 'good' | 'warn' | 'bad' | 'critical' | 'neutral' | 'info';

export function hhmm(t: string | null | undefined): string {
  if (!t) return '-';
  const time = t.includes('T') ? t.split('T')[1] : t;
  return time.slice(0, 5);
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '마감일 미정';
  const d = new Date(iso);
  return d.toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
}

/** 남은 일수 표시 (D-3, D-day, 마감) */
export function dDay(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '';
  const due = new Date(iso);
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((startOf(due) - startOf(now)) / 86_400_000);
  if (due.getTime() < now.getTime()) return '마감';
  if (diff === 0) return 'D-day';
  return `D-${diff}`;
}

/** datetime-local 입력값 ↔ ISO(+09:00) */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(v: string): string | null {
  if (!v) return null;
  return new Date(v).toISOString();
}

export function todayIso(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function num(v: string): number | null {
  if (v.trim() === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}
