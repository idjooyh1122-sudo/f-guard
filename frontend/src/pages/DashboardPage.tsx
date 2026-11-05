import { Link } from 'react-router-dom';
import { useState } from 'react';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { dDay, formatDateTime, hhmm, RISK_LABEL, RISK_TONE } from '../services/format';
import { Badge, Card, Empty, ErrorBox, Loading } from '../components/ui';
import LatenessCard from '../components/LatenessCard';
import RiskBar from '../components/RiskBar';

/** 예: VITE_QUICK_LINKS='[{"label":"이루리","url":"https://..."}]' */
const QUICK_LINKS: { label: string; url: string }[] = (() => {
  try {
    const raw = import.meta.env.VITE_QUICK_LINKS as string | undefined;
    if (raw) return JSON.parse(raw);
  } catch { /* 잘못된 값이면 기본값 사용 */ }
  return [{ label: '강원대학교 홈페이지', url: 'https://www.kangwon.ac.kr' }];
})();

export default function DashboardPage() {
  const { data, error, loading, reload } = useAsync(() => api.dashboard());
  const [seeding, setSeeding] = useState(false);
  const [seedError, setSeedError] = useState<Error | null>(null);

  async function seed() {
    setSeeding(true);
    setSeedError(null);
    try {
      await api.seedDemo();
      await reload();
    } catch (e) {
      setSeedError(e as Error);
    } finally {
      setSeeding(false);
    }
  }

  if (loading && !data) return <Loading />;
  if (error) return <ErrorBox error={error} onRetry={reload} />;
  if (!data) return null;

  const date = new Date(data.date + 'T00:00:00');
  const mocks = Object.entries(data.integrations).filter(([, v]) => v.mode === 'MOCK');

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1>오늘의 F 방어 현황</h1>
          <p>{date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })}</p>
        </div>
        <button className="btn" onClick={reload}>새로고침</button>
      </div>

      {mocks.length > 0 && (
        <div className="alert alert-mock">
          <span>
            일부 기능이 <strong>MOCK(시연용) 데이터</strong>로 동작 중입니다: {mocks.map(([k]) => ({ openai: 'GPT', weather: '날씨', travel: '이동시간', google: '캘린더' } as Record<string, string>)[k]).join(', ')}.
            {' '}<Link to="/settings">연동 상태 보기</Link>
          </span>
        </div>
      )}

      {data.courseCount === 0 && (
        <Card title="시작하기">
          <p style={{ marginTop: 0 }}>아직 등록된 과목이 없습니다. 강의계획서를 업로드하거나 과목을 직접 추가하세요.</p>
          <div className="row">
            <Link className="btn btn-primary" to="/ai">강의계획서 업로드</Link>
            <Link className="btn" to="/courses">과목 직접 추가</Link>
            <button className="btn" onClick={seed} disabled={seeding}>{seeding ? '생성 중…' : '샘플 데이터로 체험'}</button>
          </div>
          <ErrorBox error={seedError} />
        </Card>
      )}

      <div className="grid grid-2">
        <Card title="🛡️ 1차 방어선 · 다음 수업 지각 위험" action={data.nextClassRisk && <Link className="btn btn-small" to="/ai?tab=excuse">지각 사유서</Link>}>
          {data.nextClassRisk ? <LatenessCard data={data.nextClassRisk} /> : <Empty>이번 주에 남은 수업이 없습니다.</Empty>}
        </Card>

        <Card title="오늘 수업">
          {data.todayClasses.length === 0 ? <Empty>오늘은 수업이 없습니다.</Empty> : (
            <ul className="list">
              {data.todayClasses.map((c, i) => (
                <li key={i}>
                  <div><strong>{c.courseName}</strong><div className="muted small">{c.room ?? '강의실 미정'}</div></div>
                  <span className="badge tone-neutral">{hhmm(c.startTime)}–{hhmm(c.endTime)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <div className="grid grid-2">
        <Card title="🛡️ 2차 방어선 · F 위험 과목" action={<Link className="btn btn-small" to="/grades">자세히</Link>}>
          {data.riskCourses.length === 0 ? <Empty>과목을 등록하면 F 위험을 진단합니다.</Empty> : (
            <ul className="list">
              {data.riskCourses.map((c) => (
                <li key={c.courseId} style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                  <div className="row-between">
                    <strong>{c.courseName}</strong>
                    <Badge tone={RISK_TONE[c.diagnosis.status]}>{RISK_LABEL[c.diagnosis.status]}</Badge>
                  </div>
                  {c.diagnosis.status !== 'CANNOT_CALCULATE' && <RiskBar d={c.diagnosis} />}
                  <span className="small muted">{c.diagnosis.reasons[0] ?? c.diagnosis.warnings[0] ?? ''}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="다가오는 과제 마감 (2주)" action={<Link className="btn btn-small" to="/calendar">캘린더</Link>}>
          {data.upcomingAssignments.length === 0 ? <Empty>2주 안에 마감되는 과제가 없습니다.</Empty> : (
            <ul className="list">
              {data.upcomingAssignments.map((a) => (
                <li key={a.id}>
                  <div>
                    <strong>{a.title}</strong>
                    <div className="muted small">{a.courseName} · {formatDateTime(a.dueAt)}</div>
                  </div>
                  <div className="row">
                    {a.calendar.synced && <Badge tone="info">캘린더 등록됨</Badge>}
                    <Badge tone={dDay(a.dueAt).match(/^D-[0-3]$|D-day/) ? 'bad' : 'neutral'}>{dDay(a.dueAt)}</Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card title="빠른 링크">
        <div className="row">
          {QUICK_LINKS.map((l) => (
            <a key={l.url} className="btn" href={l.url} target="_blank" rel="noreferrer">{l.label}</a>
          ))}
        </div>
        <p className="muted small" style={{ marginBottom: 0 }}>
          이루리·K-cloud 등 학내 시스템은 공식 연동 방법이 확인되기 전까지 바로가기만 제공합니다(자동 로그인·크롤링 없음).
          링크는 <code>frontend/.env</code>의 <code>VITE_QUICK_LINKS</code>로 바꿀 수 있습니다.
        </p>
      </Card>
    </div>
  );
}
