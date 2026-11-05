import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { dDay, formatDateTime, fromLocalInput, toLocalInput } from '../services/format';
import type { Assignment, SyncItemResult, SyncOutcome } from '../types/api';
import { Badge, Card, Empty, ErrorBox, Loading } from '../components/ui';

const OUTCOME: Record<SyncOutcome, { label: string; tone: 'good' | 'info' | 'neutral' | 'warn' | 'bad' }> = {
  CREATED: { label: '등록 완료', tone: 'good' },
  UPDATED: { label: '마감일 변경 반영', tone: 'good' },
  SKIPPED_DUPLICATE: { label: '이미 등록됨 (중복 방지)', tone: 'info' },
  SKIPPED_NO_DUE_DATE: { label: '마감일 없음', tone: 'warn' },
  SKIPPED_UNCONFIRMED: { label: '미확인 과제', tone: 'warn' },
  FAILED: { label: '실패', tone: 'bad' },
};

const CALLBACK_MSG: Record<string, string> = {
  connected: 'Google 캘린더 연동이 완료되었습니다.',
  denied: 'Google 권한 요청이 거부되었습니다.',
  invalid_state: '연동 요청이 만료되었거나 올바르지 않습니다. 다시 시도해 주세요.',
  token_failed: 'Google 토큰 발급에 실패했습니다. 클라이언트 설정을 확인하세요.',
};

function DueEditor({ a, onSaved }: { a: Assignment; onSaved: () => void }) {
  const [v, setV] = useState(toLocalInput(a.dueAt));
  const [saving, setSaving] = useState(false);
  const changed = v !== toLocalInput(a.dueAt);
  async function save() {
    setSaving(true);
    try {
      await api.updateAssignment(a.id, { title: a.title, dueAt: fromLocalInput(v), maxScore: a.maxScore, score: a.score, status: a.status, confirmed: true });
      onSaved();
    } finally { setSaving(false); }
  }
  return (
    <div className="row">
      <input aria-label={`${a.title} 마감`} type="datetime-local" value={v} onChange={(e) => setV(e.target.value)} style={{ maxWidth: 210 }} />
      {changed && <button className="btn btn-small" onClick={save} disabled={saving}>저장</button>}
    </div>
  );
}

export default function CalendarPage() {
  const [params, setParams] = useSearchParams();
  const status = useAsync(() => api.googleStatus());
  const list = useAsync(() => api.allAssignments());
  const [selected, setSelected] = useState<number[]>([]);
  const [results, setResults] = useState<SyncItemResult[] | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [err, setErr] = useState<Error | null>(null);
  const callback = params.get('google');

  useEffect(() => {
    if (!list.data) return;
    setSelected(list.data.filter((a) => a.dueAt && a.status === 'PENDING' && (!a.calendar.synced || a.calendar.outdated)).map((a) => a.id));
  }, [list.data]);

  async function connect() {
    setErr(null);
    try {
      const { url } = await api.googleAuthUrl();
      window.location.href = url;
    } catch (e) { setErr(e as Error); }
  }

  async function sync() {
    setSyncing(true);
    setErr(null);
    try {
      setResults(await api.syncCalendar(selected));
      await list.reload();
    } catch (e) { setErr(e as Error); } finally { setSyncing(false); }
  }

  const g = status.data;
  const items = list.data ?? [];

  return (
    <div className="stack">
      <div className="page-head"><div><h1>과제 마감 캘린더</h1><p>마감일을 확인·수정한 뒤 Google 캘린더에 D-3, D-1 알림과 함께 등록합니다.</p></div></div>

      {callback && CALLBACK_MSG[callback] && (
        <div className={`alert ${callback === 'connected' ? 'alert-info' : 'alert-bad'}`}>
          <span>{CALLBACK_MSG[callback]}</span>
          <button className="btn btn-small" onClick={() => setParams({})}>닫기</button>
        </div>
      )}

      <Card title="Google 캘린더 연동">
        {status.loading && !g ? <Loading /> : !g ? <ErrorBox error={status.error} /> : !g.configured ? (
          <div className="stack" style={{ gap: 8 }}>
            <div className="alert alert-warn">Google OAuth 클라이언트가 설정되지 않았습니다. <code>.env</code>에 GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET을 넣고 백엔드를 다시 시작하세요.</div>
            <p className="small muted" style={{ margin: 0 }}>그동안은 아래 <strong>.ics 파일 내보내기</strong>로 Google·Apple·Outlook 캘린더에 직접 가져올 수 있습니다 (D-3, D-1 알림 포함).</p>
          </div>
        ) : g.connected ? (
          <div className="row">
            <Badge tone="good">연동됨</Badge>
            <span className="small muted">{g.connectedAt && new Date(g.connectedAt).toLocaleString('ko-KR')}</span>
            <button className="btn btn-small" onClick={async () => { await api.googleDisconnect(); await status.reload(); }}>연동 해제</button>
          </div>
        ) : (
          <div className="row">
            <button className="btn btn-primary" onClick={connect}>Google 계정 연동</button>
            <span className="small muted">캘린더 일정 추가 권한(calendar.events)만 요청합니다.</span>
          </div>
        )}
        <div className="row" style={{ marginTop: 12 }}>
          <a className="btn btn-small" href="/api/calendar/export.ics" download>.ics 파일 내보내기</a>
        </div>
        <div style={{ marginTop: 10 }}><ErrorBox error={err} /></div>
      </Card>

      <Card title="과제 목록" action={g?.connected && (
        <button className="btn btn-primary" disabled={syncing || selected.length === 0} onClick={sync}>
          {syncing ? '등록 중…' : `선택한 ${selected.length}개 캘린더 등록`}
        </button>
      )}>
        {list.loading && !list.data ? <Loading /> : !items.length ? <Empty>과제가 없습니다. 강의계획서를 분석하거나 성적 관리에서 과제를 추가하세요.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th></th><th>과제</th><th>마감일 (확인·수정)</th><th>상태</th></tr></thead>
              <tbody>
                {items.map((a) => (
                  <tr key={a.id}>
                    <td>
                      <input type="checkbox" aria-label={`${a.title} 선택`} disabled={!a.dueAt}
                        checked={selected.includes(a.id)}
                        onChange={(e) => setSelected(e.target.checked ? [...selected, a.id] : selected.filter((x) => x !== a.id))} />
                    </td>
                    <td><strong>{a.title}</strong><div className="small muted">{a.courseName}{a.source === 'SYLLABUS' ? ' · 강의계획서' : ''}</div></td>
                    <td>
                      <DueEditor a={a} onSaved={list.reload} />
                      <span className="small muted">{a.dueAt ? `${formatDateTime(a.dueAt)} · ${dDay(a.dueAt)}` : '마감일을 입력해야 등록할 수 있습니다'}</span>
                    </td>
                    <td>
                      {a.calendar.synced
                        ? a.calendar.outdated ? <Badge tone="warn">마감일 변경됨 · 재등록 필요</Badge>
                          : a.calendar.htmlLink ? <a href={a.calendar.htmlLink} target="_blank" rel="noreferrer"><Badge tone="info">등록됨 ↗</Badge></a> : <Badge tone="info">등록됨</Badge>
                        : <Badge tone="neutral">미등록</Badge>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {results && (
        <Card title="등록 결과">
          <ul className="list">
            {results.map((r) => (
              <li key={r.assignmentId}>
                <span>{r.title}</span>
                <div className="row">
                  <Badge tone={OUTCOME[r.outcome].tone}>{OUTCOME[r.outcome].label}</Badge>
                  {r.verified && <Badge tone="good">Google에서 확인됨</Badge>}
                  {r.htmlLink && <a className="small" href={r.htmlLink} target="_blank" rel="noreferrer">열기</a>}
                  {r.error && <span className="small" style={{ color: 'var(--bad)' }}>{r.error}</span>}
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}
