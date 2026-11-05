import { useState } from 'react';
import type { ComponentType, CourseRequest, GradeComponent, Schedule } from '../types/api';
import { COMPONENT_LABEL, DAY_LABELS, num } from '../services/format';
import { Field } from './ui';

export const EMPTY_COURSE: CourseRequest = {
  name: '', professor: '', credits: 3, totalSessions: 30,
  absenceFailThreshold: null, latesPerAbsence: null, absencePenalty: null, latePenalty: null,
  failCutoff: null, missingAssignmentFailCount: null, rulesNote: '', rulesConfirmed: false,
  schedules: [{ dayOfWeek: 1, startTime: '09:00', endTime: '10:15', room: '' }],
  components: [
    { type: 'ATTENDANCE', name: '출석', weight: 10, score: null },
    { type: 'ASSIGNMENT', name: '과제', weight: 20, score: null },
    { type: 'MIDTERM', name: '중간고사', weight: 30, score: null },
    { type: 'FINAL', name: '기말고사', weight: 40, score: null },
  ],
};

const numStr = (v: number | null | undefined) => (v == null ? '' : String(v));

export default function CourseForm({ initial, onSubmit, onCancel, submitting }: {
  initial: CourseRequest;
  onSubmit: (c: CourseRequest) => void;
  onCancel: () => void;
  submitting: boolean;
}) {
  const [c, setC] = useState<CourseRequest>(initial);
  const set = <K extends keyof CourseRequest>(k: K, v: CourseRequest[K]) => setC((p) => ({ ...p, [k]: v }));
  const weightSum = c.components.reduce((s, x) => s + (Number(x.weight) || 0), 0);

  const setSchedule = (i: number, patch: Partial<Schedule>) =>
    set('schedules', c.schedules.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  const setComp = (i: number, patch: Partial<GradeComponent>) =>
    set('components', c.components.map((s, j) => (j === i ? { ...s, ...patch } : s)));

  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); onSubmit(c); }}>
      <div className="form-grid">
        <Field label="과목명 *"><input required maxLength={100} value={c.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="교수명"><input maxLength={50} value={c.professor ?? ''} onChange={(e) => set('professor', e.target.value)} /></Field>
        <Field label="학점"><input type="number" min={0} max={10} value={numStr(c.credits)} onChange={(e) => set('credits', num(e.target.value))} /></Field>
        <Field label="전체 수업 횟수"><input type="number" min={1} max={200} value={numStr(c.totalSessions)} onChange={(e) => set('totalSessions', num(e.target.value))} /></Field>
      </div>

      <fieldset>
        <legend>수업 시간표</legend>
        <div className="stack" style={{ gap: 8 }}>
          {c.schedules.map((s, i) => (
            <div key={i} className="row">
              <select aria-label="요일" value={s.dayOfWeek} onChange={(e) => setSchedule(i, { dayOfWeek: Number(e.target.value) })} style={{ width: 80 }}>
                {DAY_LABELS.slice(1).map((d, idx) => <option key={d} value={idx + 1}>{d}</option>)}
              </select>
              <input aria-label="시작" type="time" value={s.startTime.slice(0, 5)} onChange={(e) => setSchedule(i, { startTime: e.target.value })} style={{ width: 120 }} />
              <span>~</span>
              <input aria-label="종료" type="time" value={s.endTime.slice(0, 5)} onChange={(e) => setSchedule(i, { endTime: e.target.value })} style={{ width: 120 }} />
              <input aria-label="강의실" placeholder="강의실" value={s.room ?? ''} onChange={(e) => setSchedule(i, { room: e.target.value })} style={{ width: 160 }} />
              <button type="button" className="btn btn-small btn-danger" onClick={() => set('schedules', c.schedules.filter((_, j) => j !== i))}>삭제</button>
            </div>
          ))}
          <div><button type="button" className="btn btn-small" onClick={() => set('schedules', [...c.schedules, { dayOfWeek: 1, startTime: '09:00', endTime: '10:15', room: '' }])}>+ 시간 추가</button></div>
        </div>
      </fieldset>

      <fieldset>
        <legend>평가 항목 <span className={`badge ${Math.abs(weightSum - 100) < 0.01 ? 'tone-good' : 'tone-warn'}`}>합계 {weightSum}%</span></legend>
        <div className="stack" style={{ gap: 8 }}>
          {c.components.map((x, i) => (
            <div key={i} className="row">
              <select aria-label="유형" value={x.type} onChange={(e) => setComp(i, { type: e.target.value as ComponentType })} style={{ width: 110 }}>
                {(Object.keys(COMPONENT_LABEL) as ComponentType[]).map((t) => <option key={t} value={t}>{COMPONENT_LABEL[t]}</option>)}
              </select>
              <input aria-label="항목명" value={x.name} onChange={(e) => setComp(i, { name: e.target.value })} style={{ width: 130 }} required />
              <input aria-label="비율(%)" type="number" min={0} max={100} step="0.5" value={numStr(x.weight)} onChange={(e) => setComp(i, { weight: num(e.target.value) ?? 0 })} style={{ width: 90 }} />
              <span className="small muted">%</span>
              <input aria-label="점수(100점 환산)" type="number" min={0} max={100} step="0.1" placeholder="점수(미정)" value={numStr(x.score)} onChange={(e) => setComp(i, { score: num(e.target.value) })} style={{ width: 120 }} />
              <button type="button" className="btn btn-small btn-danger" onClick={() => set('components', c.components.filter((_, j) => j !== i))}>삭제</button>
            </div>
          ))}
          <div><button type="button" className="btn btn-small" onClick={() => set('components', [...c.components, { type: 'OTHER', name: '기타', weight: 0, score: null }])}>+ 항목 추가</button></div>
          <span className="field-hint">점수는 100점 만점 환산으로 입력합니다. 출석·과제 점수를 비워두면 출석 기록과 과제 목록으로 자동 계산합니다.</span>
        </div>
      </fieldset>

      <fieldset>
        <legend>F학점 규칙</legend>
        <div className="form-grid">
          <Field label="F 기준 총점 (미만 F)"><input type="number" min={0} max={100} value={numStr(c.failCutoff)} onChange={(e) => set('failCutoff', num(e.target.value))} placeholder="예: 60" /></Field>
          <Field label="결석 F 기준 (N회 이상)"><input type="number" min={1} value={numStr(c.absenceFailThreshold)} onChange={(e) => set('absenceFailThreshold', num(e.target.value))} placeholder="예: 8" /></Field>
          <Field label="지각 N회 = 결석 1회"><input type="number" min={1} value={numStr(c.latesPerAbsence)} onChange={(e) => set('latesPerAbsence', num(e.target.value))} placeholder="예: 3" /></Field>
          <Field label="결석 1회 감점" hint="출석 100점 기준"><input type="number" min={0} max={100} step="0.5" value={numStr(c.absencePenalty)} onChange={(e) => set('absencePenalty', num(e.target.value))} /></Field>
          <Field label="지각 1회 감점" hint="출석 100점 기준"><input type="number" min={0} max={100} step="0.5" value={numStr(c.latePenalty)} onChange={(e) => set('latePenalty', num(e.target.value))} /></Field>
          <Field label="과제 미제출 F (N건 이상)"><input type="number" min={1} value={numStr(c.missingAssignmentFailCount)} onChange={(e) => set('missingAssignmentFailCount', num(e.target.value))} /></Field>
        </div>
        <div style={{ marginTop: 10 }}>
          <Field label="규칙 메모"><textarea maxLength={2000} value={c.rulesNote ?? ''} onChange={(e) => set('rulesNote', e.target.value)} /></Field>
        </div>
        <label className="checkbox" style={{ marginTop: 10 }}>
          <input type="checkbox" checked={c.rulesConfirmed} onChange={(e) => set('rulesConfirmed', e.target.checked)} />
          강의계획서·학칙으로 위 규칙을 확인했습니다 (확인해야 'F 확정'을 표시합니다)
        </label>
      </fieldset>

      <div className="row">
        <button className="btn btn-primary" type="submit" disabled={submitting}>{submitting ? '저장 중…' : '저장'}</button>
        <button className="btn" type="button" onClick={onCancel}>취소</button>
      </div>
    </form>
  );
}
