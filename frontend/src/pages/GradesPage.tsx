import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useCourseSelection } from '../hooks/useCourseSelection';
import { COMPONENT_LABEL, dDay, formatDateTime, fromLocalInput, hhmm, num, RISK_LABEL, RISK_TONE, toLocalInput } from '../services/format';
import type { Assignment, AssignmentStatus, Course, CourseDiagnosis, Explanation } from '../types/api';
import { Badge, Card, Empty, ErrorBox, ExplanationBox, Field, Loading } from '../components/ui';
import CourseTabs from '../components/CourseTabs';
import RiskBar from '../components/RiskBar';

const STATUS_LABEL: Record<AssignmentStatus, string> = { PENDING: '진행 중', SUBMITTED: '제출', MISSED: '미제출' };

function DiagnosisView({ d }: { d: CourseDiagnosis }) {
  const g = d.diagnosis;
  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="row">
        <Badge tone={RISK_TONE[g.status]}>{RISK_LABEL[g.status]}</Badge>
        {g.requiredPercentOfRemaining != null && g.status !== 'SAFE' && (
          <span className="small">남은 평가에서 만점 대비 <strong>{g.requiredPercentOfRemaining}%</strong> 이상 필요</span>
        )}
      </div>
      {g.status !== 'CANNOT_CALCULATE' && <RiskBar d={g} />}
      {g.reasons.length > 0 && <ul className="issues">{g.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>}
      {g.warnings.length > 0 && <ul className="issues muted">{g.warnings.map((r, i) => <li key={i}>⚠ {r}</li>)}</ul>}
      <div className="table-wrap">
        <table>
          <thead><tr><th>항목</th><th>비율</th><th>점수 범위(100점 환산)</th><th>근거</th></tr></thead>
          <tbody>
            {g.components.map((c, i) => (
              <tr key={i}>
                <td>{c.name}</td><td>{c.weight}%</td>
                <td>{Number(c.lowerScore) === Number(c.upperScore) ? `${c.lowerScore}` : `${c.lowerScore} ~ ${c.upperScore}`}</td>
                <td className="small muted">{c.basis}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ScoresEditor({ course, onSaved }: { course: Course; onSaved: () => void }) {
  const [scores, setScores] = useState<Record<number, string>>({});
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<Error | null>(null);
  useEffect(() => {
    setScores(Object.fromEntries(course.components.map((c) => [c.id!, c.score == null ? '' : String(c.score)])));
  }, [course]);

  async function save() {
    setSaving(true);
    setErr(null);
    try {
      const { id: _id, weightSum: _w, ...rest } = course;
      void _id; void _w;
      await api.updateCourse(course.id, {
        ...rest,
        schedules: course.schedules.map((s) => ({ ...s, startTime: hhmm(s.startTime), endTime: hhmm(s.endTime) })),
        components: course.components.map((c) => ({ ...c, score: num(scores[c.id!] ?? '') })),
      });
      onSaved();
    } catch (e) {
      setErr(e as Error);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="stack" style={{ gap: 10 }}>
      <ErrorBox error={err} />
      <div className="form-grid">
        {course.components.map((c) => (
          <Field key={c.id} label={`${c.name} (${c.weight}%)`} hint={c.type === 'ATTENDANCE' || c.type === 'ASSIGNMENT' ? '비우면 기록으로 자동 계산' : COMPONENT_LABEL[c.type]}>
            <input type="number" min={0} max={100} step="0.1" placeholder="미평가" value={scores[c.id!] ?? ''} onChange={(e) => setScores({ ...scores, [c.id!]: e.target.value })} />
          </Field>
        ))}
      </div>
      <div><button className="btn btn-primary" onClick={save} disabled={saving}>{saving ? '저장 중…' : '점수 저장'}</button></div>
    </div>
  );
}

function AssignmentRow({ a, onChange }: { a: Assignment; onChange: () => void }) {
  const [edit, setEdit] = useState(false);
  const [title, setTitle] = useState(a.title);
  const [due, setDue] = useState(toLocalInput(a.dueAt));
  const [score, setScore] = useState(a.score == null ? '' : String(a.score));
  const [max, setMax] = useState(String(a.maxScore));
  const [err, setErr] = useState<Error | null>(null);

  async function save(status: AssignmentStatus = a.status) {
    setErr(null);
    try {
      await api.updateAssignment(a.id, { title, dueAt: fromLocalInput(due), maxScore: num(max), score: num(score), status, confirmed: true });
      setEdit(false);
      onChange();
    } catch (e) { setErr(e as Error); }
  }

  if (edit) {
    return (
      <tr><td colSpan={5}>
        <ErrorBox error={err} />
        <div className="form-grid">
          <Field label="제목"><input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
          <Field label="마감"><input type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} /></Field>
          <Field label="점수"><input type="number" min={0} value={score} onChange={(e) => setScore(e.target.value)} /></Field>
          <Field label="만점"><input type="number" min={1} value={max} onChange={(e) => setMax(e.target.value)} /></Field>
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <button className="btn btn-primary btn-small" onClick={() => save()}>저장</button>
          <button className="btn btn-small" onClick={() => setEdit(false)}>취소</button>
        </div>
      </td></tr>
    );
  }
  return (
    <tr>
      <td>{a.title}{a.source === 'SYLLABUS' && <> <Badge tone="info">계획서</Badge></>}</td>
      <td className="small">{formatDateTime(a.dueAt)} {a.dueAt && <Badge tone="neutral">{dDay(a.dueAt)}</Badge>}</td>
      <td>
        <select aria-label="제출 상태" value={a.status} onChange={(e) => save(e.target.value as AssignmentStatus)}>
          {(Object.keys(STATUS_LABEL) as AssignmentStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
      </td>
      <td>{a.score != null ? `${a.score} / ${a.maxScore}` : '-'}</td>
      <td className="row">
        <button className="btn btn-small" onClick={() => setEdit(true)}>수정</button>
        <button className="btn btn-small btn-danger" onClick={async () => { if (confirm('과제를 삭제할까요?')) { await api.deleteAssignment(a.id); onChange(); } }}>삭제</button>
      </td>
    </tr>
  );
}

export default function GradesPage() {
  const courses = useAsync(() => api.courses());
  const [selected, setSelected] = useCourseSelection(courses.data);
  const diag = useAsync(() => (selected ? api.diagnosis(selected) : Promise.resolve(null)), [selected]);
  const assignments = useAsync(() => (selected ? api.assignments(selected) : Promise.resolve([])), [selected]);
  const [what, setWhat] = useState({ extraAbsences: 0, extraLates: 0, miss: [] as number[] });
  const [whatResult, setWhatResult] = useState<CourseDiagnosis | null>(null);
  const [exp, setExp] = useState<Explanation | null>(null);
  const [expLoading, setExpLoading] = useState(false);
  const [newA, setNewA] = useState({ title: '', due: '' });
  const [err, setErr] = useState<Error | null>(null);

  useEffect(() => { setWhatResult(null); setExp(null); setWhat({ extraAbsences: 0, extraLates: 0, miss: [] }); }, [selected]);

  const refresh = async () => { await Promise.all([diag.reload(), assignments.reload(), courses.reload()]); setWhatResult(null); };

  async function runWhatIf() {
    if (!selected) return;
    setWhatResult(await api.whatIf(selected, { extraAbsences: what.extraAbsences, extraLates: what.extraLates, missAssignmentIds: what.miss }));
  }

  async function explain() {
    if (!selected) return;
    setExpLoading(true);
    try { setExp(await api.explainDiagnosis(selected)); } finally { setExpLoading(false); }
  }

  async function addAssignment(e: React.FormEvent) {
    e.preventDefault();
    if (!selected || !newA.title.trim()) return;
    setErr(null);
    try {
      await api.createAssignment(selected, { title: newA.title, dueAt: fromLocalInput(newA.due), status: 'PENDING' });
      setNewA({ title: '', due: '' });
      await refresh();
    } catch (er) { setErr(er as Error); }
  }

  if (courses.loading && !courses.data) return <Loading />;
  if (courses.error) return <ErrorBox error={courses.error} onRetry={courses.reload} />;
  if (!courses.data?.length) return <Empty>먼저 <Link to="/courses">과목을 등록</Link>하세요.</Empty>;
  const course = courses.data.find((c) => c.id === selected);
  const pending = (assignments.data ?? []).filter((a) => a.status === 'PENDING');

  return (
    <div className="stack">
      <div className="page-head"><div><h1>성적 관리 · F 위험 진단</h1><p>평가 규칙과 현재 점수로 F 기준까지의 거리를 역산합니다. (GPT 없이 규칙 계산)</p></div></div>
      <CourseTabs courses={courses.data} selected={selected} onSelect={setSelected} />

      <div className="grid grid-2">
        <Card title="현재 진단" action={<button className="btn btn-small" onClick={explain} disabled={expLoading}>{expLoading ? '생성 중…' : 'AI 설명'}</button>}>
          {diag.loading && !diag.data ? <Loading /> : diag.data ? <DiagnosisView d={diag.data} /> : <ErrorBox error={diag.error} />}
          {exp && <ExplanationBox explanation={exp} />}
        </Card>

        <Card title="🔮 가정 시뮬레이션">
          <p className="small muted" style={{ marginTop: 0 }}>"이번 과제를 놓치면?" "한 번 더 결석하면?"을 미리 계산합니다. 실제 기록은 바뀌지 않습니다.</p>
          <div className="form-grid">
            <Field label="추가 결석"><input type="number" min={0} max={50} value={what.extraAbsences} onChange={(e) => setWhat({ ...what, extraAbsences: Number(e.target.value) || 0 })} /></Field>
            <Field label="추가 지각"><input type="number" min={0} max={50} value={what.extraLates} onChange={(e) => setWhat({ ...what, extraLates: Number(e.target.value) || 0 })} /></Field>
          </div>
          {pending.length > 0 && (
            <fieldset style={{ marginTop: 10 }}>
              <legend className="small">놓친다고 가정할 과제</legend>
              {pending.map((a) => (
                <label key={a.id} className="checkbox small">
                  <input type="checkbox" checked={what.miss.includes(a.id)}
                    onChange={(e) => setWhat({ ...what, miss: e.target.checked ? [...what.miss, a.id] : what.miss.filter((x) => x !== a.id) })} />
                  {a.title}
                </label>
              ))}
            </fieldset>
          )}
          <div style={{ marginTop: 12 }}><button className="btn btn-primary" onClick={runWhatIf}>시뮬레이션 실행</button></div>
          {whatResult && <div style={{ marginTop: 14 }}><DiagnosisView d={whatResult} /></div>}
        </Card>
      </div>

      {course && (
        <Card title="평가 항목 점수" action={<Link className="btn btn-small" to="/courses">규칙 수정</Link>}>
          {course.components.length === 0 ? <Empty>평가 항목이 없습니다.</Empty> : <ScoresEditor course={course} onSaved={refresh} />}
        </Card>
      )}

      <Card title="과제">
        <ErrorBox error={err} />
        <form className="row" onSubmit={addAssignment} style={{ marginBottom: 12 }}>
          <input aria-label="과제 제목" placeholder="과제 제목" value={newA.title} onChange={(e) => setNewA({ ...newA, title: e.target.value })} style={{ maxWidth: 260 }} />
          <input aria-label="마감" type="datetime-local" value={newA.due} onChange={(e) => setNewA({ ...newA, due: e.target.value })} style={{ maxWidth: 220 }} />
          <button className="btn" type="submit">+ 추가</button>
        </form>
        {assignments.loading && !assignments.data ? <Loading /> : !assignments.data?.length ? <Empty>과제가 없습니다.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>과제</th><th>마감</th><th>상태</th><th>점수</th><th></th></tr></thead>
              <tbody>{assignments.data.map((a) => <AssignmentRow key={a.id} a={a} onChange={refresh} />)}</tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
