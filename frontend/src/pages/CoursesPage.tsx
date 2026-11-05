import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { DAY_LABELS, hhmm } from '../services/format';
import type { Course, CourseRequest } from '../types/api';
import { Badge, Card, Empty, ErrorBox, Loading } from '../components/ui';
import CourseForm, { EMPTY_COURSE } from '../components/CourseForm';

function toRequest(c: Course): CourseRequest {
  const { id: _id, weightSum: _w, ...rest } = c;
  void _id; void _w;
  return { ...rest, schedules: c.schedules.map((s) => ({ ...s, startTime: hhmm(s.startTime), endTime: hhmm(s.endTime) })) };
}

export default function CoursesPage() {
  const { data, error, loading, reload } = useAsync(() => api.courses());
  const [editing, setEditing] = useState<{ id: number | null; value: CourseRequest } | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<Error | null>(null);

  async function submit(c: CourseRequest) {
    setSaving(true);
    setSaveError(null);
    try {
      if (editing?.id) await api.updateCourse(editing.id, c);
      else await api.createCourse(c);
      setEditing(null);
      await reload();
    } catch (e) {
      setSaveError(e as Error);
    } finally {
      setSaving(false);
    }
  }

  async function remove(c: Course) {
    if (!confirm(`'${c.name}' 과목과 출석·과제 기록을 모두 삭제할까요?`)) return;
    try {
      await api.deleteCourse(c.id);
      await reload();
    } catch (e) {
      setSaveError(e as Error);
    }
  }

  return (
    <div className="stack">
      <div className="page-head">
        <div><h1>과목 관리</h1><p>시간표와 평가 규칙을 등록하면 지각·F학점 위험을 계산합니다.</p></div>
        <div className="row">
          <Link className="btn" to="/ai">강의계획서로 자동 등록</Link>
          <button className="btn btn-primary" onClick={() => setEditing({ id: null, value: EMPTY_COURSE })}>+ 과목 추가</button>
        </div>
      </div>

      {editing && (
        <Card title={editing.id ? '과목 수정' : '새 과목'}>
          <ErrorBox error={saveError} />
          <CourseForm key={editing.id ?? 'new'} initial={editing.value} onSubmit={submit} onCancel={() => setEditing(null)} submitting={saving} />
        </Card>
      )}

      {loading && !data ? <Loading /> : <ErrorBox error={error} onRetry={reload} />}
      {data && data.length === 0 && !editing && <Empty>등록된 과목이 없습니다.</Empty>}
      <div className="grid grid-2">
        {data?.map((c) => (
          <Card key={c.id} title={c.name} action={
            <div className="row">
              <button className="btn btn-small" onClick={() => setEditing({ id: c.id, value: toRequest(c) })}>수정</button>
              <button className="btn btn-small btn-danger" onClick={() => remove(c)}>삭제</button>
            </div>
          }>
            <div className="stack" style={{ gap: 8 }}>
              <div className="row small muted">
                {c.professor && <span>{c.professor}</span>}
                {c.credits != null && <span>· {c.credits}학점</span>}
                {c.totalSessions != null && <span>· {c.totalSessions}회</span>}
              </div>
              <div className="row">
                {c.schedules.map((s) => <Badge key={s.id ?? `${s.dayOfWeek}${s.startTime}`} tone="neutral">{DAY_LABELS[s.dayOfWeek]} {hhmm(s.startTime)}–{hhmm(s.endTime)}{s.room ? ` ${s.room}` : ''}</Badge>)}
              </div>
              <div className="row small">
                {c.components.map((x) => <span key={x.id ?? x.name}>{x.name} {x.weight}%{x.score != null ? ` (${x.score}점)` : ''}</span>)}
                {Math.abs(c.weightSum - 100) > 0.01 && <Badge tone="warn">비율 합계 {c.weightSum}%</Badge>}
              </div>
              <div className="row">
                {c.rulesConfirmed ? <Badge tone="good">규칙 확인됨</Badge> : <Badge tone="warn">규칙 미확인</Badge>}
                {c.failCutoff != null && <Badge tone="neutral">F &lt; {c.failCutoff}점</Badge>}
                {c.absenceFailThreshold != null && <Badge tone="neutral">결석 {c.absenceFailThreshold}회 F</Badge>}
              </div>
              {c.rulesNote && <p className="small muted" style={{ margin: 0 }}>{c.rulesNote}</p>}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
