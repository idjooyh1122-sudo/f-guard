import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { useCourseSelection } from '../hooks/useCourseSelection';
import { ATTENDANCE_LABEL, todayIso } from '../services/format';
import type { AttendanceStatus } from '../types/api';
import { Badge, Card, Empty, ErrorBox, Field, Loading } from '../components/ui';
import CourseTabs from '../components/CourseTabs';

const STATUSES: AttendanceStatus[] = ['ATTENDED', 'LATE', 'ABSENT', 'EXCUSED'];

export default function AttendancePage() {
  const courses = useAsync(() => api.courses());
  const [selected, setSelected] = useCourseSelection(courses.data);
  const records = useAsync(() => (selected ? api.attendance(selected) : Promise.resolve([])), [selected]);
  const diag = useAsync(() => (selected ? api.diagnosis(selected) : Promise.resolve(null)), [selected]);
  const [date, setDate] = useState(todayIso());
  const [note, setNote] = useState('');
  const [err, setErr] = useState<Error | null>(null);

  async function record(status: AttendanceStatus, classDate = date, keepNote?: string | null) {
    if (!selected) return;
    setErr(null);
    try {
      await api.upsertAttendance(selected, { classDate, status, note: keepNote !== undefined ? keepNote ?? undefined : note || undefined });
      if (keepNote === undefined) setNote('');
      await Promise.all([records.reload(), diag.reload()]);
    } catch (e) {
      setErr(e as Error);
    }
  }

  async function remove(id: number) {
    if (!selected) return;
    await api.deleteAttendance(selected, id);
    await Promise.all([records.reload(), diag.reload()]);
  }

  if (courses.loading && !courses.data) return <Loading />;
  if (courses.error) return <ErrorBox error={courses.error} onRetry={courses.reload} />;
  if (!courses.data?.length) return <Empty>먼저 <Link to="/courses">과목을 등록</Link>하세요.</Empty>;

  const counts = STATUSES.map((s) => (records.data ?? []).filter((r) => r.status === s).length);
  const d = diag.data?.diagnosis;
  const course = courses.data.find((c) => c.id === selected);

  return (
    <div className="stack">
      <div className="page-head"><div><h1>출석 관리</h1><p>출석·지각·결석을 기록하면 결석 F 기준까지 남은 횟수를 계산합니다.</p></div></div>
      <CourseTabs courses={courses.data} selected={selected} onSelect={setSelected} />

      <div className="grid grid-3">
        {STATUSES.map((s, i) => (
          <div key={s} className="card metric"><span className="label">{ATTENDANCE_LABEL[s]}</span><span className="value">{counts[i]}</span></div>
        ))}
      </div>

      <Card title="결석 F 기준">
        {course?.absenceFailThreshold == null ? (
          <p className="muted" style={{ margin: 0 }}>결석 F 기준이 입력되지 않았습니다. <Link to="/courses">과목 설정</Link>에서 입력하세요.</p>
        ) : d ? (
          <div className="row" style={{ gap: 24 }}>
            <div className="metric"><span className="label">환산 결석</span><span className="value">{d.effectiveAbsences}회</span></div>
            <div className="metric"><span className="label">F 기준</span><span className="value">{course.absenceFailThreshold}회</span></div>
            <div className="metric"><span className="label">F까지 남은 결석</span>
              <span className="value" style={{ color: (d.remainingAbsencesBeforeF ?? 99) <= 1 ? 'var(--bad)' : undefined }}>{d.remainingAbsencesBeforeF ?? '-'}회</span>
            </div>
            {course.latesPerAbsence && <span className="small muted">지각 {course.latesPerAbsence}회 = 결석 1회로 환산</span>}
          </div>
        ) : <Loading />}
      </Card>

      <Card title="출석 기록하기">
        <ErrorBox error={err} />
        <div className="form-grid" style={{ alignItems: 'end' }}>
          <Field label="수업 날짜"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
          <Field label="메모 (선택)"><input maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          {STATUSES.map((s) => <button key={s} className="btn" onClick={() => record(s)}>{ATTENDANCE_LABEL[s]}</button>)}
        </div>
        <p className="small muted" style={{ marginBottom: 0 }}>같은 날짜를 다시 기록하면 기존 기록이 바뀝니다.</p>
      </Card>

      <Card title="기록">
        {records.loading && !records.data ? <Loading /> : !records.data?.length ? <Empty>기록이 없습니다.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>날짜</th><th>상태</th><th>메모</th><th></th></tr></thead>
              <tbody>
                {records.data.map((r) => (
                  <tr key={r.id}>
                    <td>{r.classDate}</td>
                    <td>
                      <div className="status-pills">
                        {STATUSES.map((s) => (
                          <button key={s} className={r.status === s ? `on-${s}` : ''} onClick={() => r.status !== s && record(s, r.classDate, r.note ?? null)} aria-pressed={r.status === s}>
                            {ATTENDANCE_LABEL[s]}
                          </button>
                        ))}
                      </div>
                    </td>
                    <td className="small muted">{r.note}</td>
                    <td><button className="btn btn-small btn-danger" onClick={() => remove(r.id)}>삭제</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {d && d.status === 'F_CONFIRMED' && <div style={{ marginTop: 12 }}><Badge tone="critical">이 과목은 현재 F 확정 상태입니다</Badge></div>}
      </Card>
    </div>
  );
}
