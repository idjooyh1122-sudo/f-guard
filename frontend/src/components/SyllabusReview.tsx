import { useState } from 'react';
import type { Course, SyllabusAnalysis, SyllabusResult } from '../types/api';
import { num } from '../services/format';
import { Badge, ErrorBox, Field, ModeBadge } from './ui';

const s = (v: number | null) => (v == null ? '' : String(v));

/** AI 추출 결과를 사용자가 검토·수정 후 확정한다. 비어 있는 값은 '문서에 없음'을 뜻한다. */
export default function SyllabusReview({ analysis, courses, onConfirm, confirming, error }: {
  analysis: SyllabusAnalysis;
  courses: Course[];
  onConfirm: (result: SyllabusResult, courseId: number | null) => void;
  confirming: boolean;
  error: Error | null;
}) {
  const [r, setR] = useState<SyllabusResult>(analysis.result!);
  const [target, setTarget] = useState<number | null>(analysis.courseId ?? null);
  const uncertain = new Set(r.uncertainFields);
  const mark = (path: string) => (uncertain.has(path) ? <Badge tone="warn">확인 필요</Badge> : null);
  const w = r.weights;
  const sum = [w.attendance, w.assignment, w.midterm, w.final, w.quiz, w.other].reduce<number>((a, b) => a + (b ?? 0), 0);
  const done = analysis.status === 'CONFIRMED';

  const setW = (k: keyof SyllabusResult['weights'], v: number | string | null) => setR({ ...r, weights: { ...r.weights, [k]: v } });
  const setAR = (k: keyof SyllabusResult['attendanceRules'], v: number | string | null) => setR({ ...r, attendanceRules: { ...r.attendanceRules, [k]: v } });

  return (
    <div className="stack">
      <div className="row">
        <strong>{analysis.fileName}</strong>
        <ModeBadge mode={analysis.mode} label={analysis.mode === 'REAL' ? analysis.model ?? 'GPT' : 'GPT 미사용 예시 결과'} />
        {done && <Badge tone="good">확정됨</Badge>}
      </div>
      {analysis.mode === 'MOCK' && (
        <div className="alert alert-mock">OpenAI API 키가 없어 업로드한 파일을 실제로 분석하지 않았습니다. 아래는 시연용 예시 결과입니다.</div>
      )}
      {analysis.validationIssues.length > 0 && (
        <ul className="issues">
          {analysis.validationIssues.map((x, i) => <li key={i} className={x.startsWith('[오류]') ? 'err' : ''}>{x}</li>)}
        </ul>
      )}

      <fieldset disabled={done}>
        <legend>기본 정보</legend>
        <div className="form-grid">
          <Field label="과목명 *"><input value={r.courseName ?? ''} onChange={(e) => setR({ ...r, courseName: e.target.value || null })} /></Field>
          <Field label="교수명"><input value={r.professor ?? ''} onChange={(e) => setR({ ...r, professor: e.target.value || null })} /></Field>
          <Field label="전체 수업 횟수"><input type="number" value={s(r.totalSessions)} onChange={(e) => setR({ ...r, totalSessions: num(e.target.value) })} /></Field>
        </div>
      </fieldset>

      <fieldset disabled={done}>
        <legend>평가 비율 <Badge tone={sum === 100 ? 'good' : 'warn'}>합계 {sum}%</Badge></legend>
        <div className="form-grid">
          {([['attendance', '출석'], ['assignment', '과제'], ['midterm', '중간'], ['final', '기말'], ['quiz', '퀴즈'], ['other', '기타']] as const).map(([k, label]) => (
            <Field key={k} label={`${label} %`}><input type="number" min={0} max={100} value={s(w[k])} placeholder="문서에 없음" onChange={(e) => setW(k, num(e.target.value))} /></Field>
          ))}
        </div>
      </fieldset>

      <fieldset disabled={done}>
        <legend>출석·F 규칙</legend>
        <div className="form-grid">
          <Field label="결석 F 기준(회)"><input type="number" value={s(r.attendanceRules.absenceFailThreshold)} placeholder="문서에 없음" onChange={(e) => setAR('absenceFailThreshold', num(e.target.value))} /></Field>
          <Field label="지각 N회=결석1"><input type="number" value={s(r.attendanceRules.latesPerAbsence)} placeholder="문서에 없음" onChange={(e) => setAR('latesPerAbsence', num(e.target.value))} /></Field>
          <Field label="결석 1회 감점" hint="출석 100점 기준"><input type="number" value={s(r.attendanceRules.absencePenalty)} placeholder="문서에 없음" onChange={(e) => setAR('absencePenalty', num(e.target.value))} /></Field>
          <Field label="지각 1회 감점" hint="출석 100점 기준"><input type="number" value={s(r.attendanceRules.latePenalty)} placeholder="문서에 없음" onChange={(e) => setAR('latePenalty', num(e.target.value))} /></Field>
          <Field label="F 기준 총점"><input type="number" value={s(r.failCriteria.failCutoff)} placeholder="문서에 없음" onChange={(e) => setR({ ...r, failCriteria: { ...r.failCriteria, failCutoff: num(e.target.value) } })} /></Field>
          <Field label="과제 미제출 F(건)"><input type="number" value={s(r.assignmentRules.missingAssignmentFailCount)} placeholder="문서에 없음" onChange={(e) => setR({ ...r, assignmentRules: { ...r.assignmentRules, missingAssignmentFailCount: num(e.target.value) } })} /></Field>
        </div>
        <div className="row small" style={{ marginTop: 8 }}>{mark('attendanceRules.latePenalty')}{mark('assignmentRules.missingAssignmentFailCount')}</div>
        {r.attendanceRules.description && <p className="small muted">출석 규칙 원문: {r.attendanceRules.description}</p>}
        {r.failCriteria.description && <p className="small muted">F 기준 원문: {r.failCriteria.description}</p>}
      </fieldset>

      <fieldset disabled={done}>
        <legend>과제 마감일 (문서에 적힌 것만)</legend>
        <div className="stack" style={{ gap: 8 }}>
          {r.assignments.map((a, i) => (
            <div key={i} className="row">
              <input aria-label="과제명" value={a.title} onChange={(e) => setR({ ...r, assignments: r.assignments.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)) })} style={{ maxWidth: 260 }} />
              <input aria-label="마감일" type="date" value={a.dueDate ?? ''} onChange={(e) => setR({ ...r, assignments: r.assignments.map((x, j) => (j === i ? { ...x, dueDate: e.target.value || null } : x)) })} style={{ maxWidth: 160 }} />
              <input aria-label="마감 시간" type="time" value={a.dueTime ?? ''} onChange={(e) => setR({ ...r, assignments: r.assignments.map((x, j) => (j === i ? { ...x, dueTime: e.target.value || null } : x)) })} style={{ maxWidth: 120 }} />
              {!a.dueDate && <Badge tone="warn">마감일 없음</Badge>}
              <button type="button" className="btn btn-small btn-danger" onClick={() => setR({ ...r, assignments: r.assignments.filter((_, j) => j !== i) })}>삭제</button>
              {a.evidence && <span className="small muted" style={{ flexBasis: '100%' }}>근거: “{a.evidence}”</span>}
            </div>
          ))}
          <div><button type="button" className="btn btn-small" onClick={() => setR({ ...r, assignments: [...r.assignments, { title: '새 과제', dueDate: null, dueTime: null, evidence: null }] })}>+ 과제 추가</button></div>
        </div>
      </fieldset>

      {r.evidence.length > 0 && (
        <details>
          <summary className="small">AI가 참고한 원문 근거 {r.evidence.length}개</summary>
          <ul className="issues">{r.evidence.map((e, i) => <li key={i}><strong>{e.field}</strong>: “{e.quote}”</li>)}</ul>
        </details>
      )}

      {!done && (
        <>
          <ErrorBox error={error} />
          <div className="row">
            <select aria-label="저장할 과목" value={target ?? ''} onChange={(e) => setTarget(e.target.value ? Number(e.target.value) : null)} style={{ maxWidth: 260 }}>
              <option value="">새 과목으로 저장</option>
              {courses.map((c) => <option key={c.id} value={c.id}>기존 과목 갱신: {c.name}</option>)}
            </select>
            <button className="btn btn-primary" disabled={confirming} onClick={() => onConfirm(r, target)}>{confirming ? '저장 중…' : '검토 완료 · 저장'}</button>
          </div>
          <p className="small muted" style={{ margin: 0 }}>저장하면 평가 규칙이 '사용자 확인됨'으로 표시되어 F 확정 판정에 사용됩니다.</p>
        </>
      )}
    </div>
  );
}
