import { useState } from 'react';
import { api } from '../services/api';
import { hhmm, LATENESS_LABEL, LATENESS_TONE, todayIso } from '../services/format';
import type { Explanation, LatenessResponse } from '../types/api';
import { Badge, ExplanationBox, ModeBadge } from './ui';

const TONE_COLOR = { good: 'var(--good)', warn: 'var(--warn)', bad: 'var(--bad)', critical: 'var(--critical-bg)', neutral: 'var(--muted)', info: 'var(--primary)' };

export default function LatenessCard({ data }: { data: LatenessResponse }) {
  const r = data.risk;
  const tone = LATENESS_TONE[r.level];
  const [exp, setExp] = useState<Explanation | null>(null);
  const [loading, setLoading] = useState(false);
  const w = r.weather;

  async function explain() {
    setLoading(true);
    try {
      setExp(await api.explainLateness(data.courseId, data.date));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="stack">
      <div className="row" style={{ gap: 18, alignItems: 'center' }}>
        <div className="score-ring" style={{ ['--p' as string]: r.score, ['--c' as string]: TONE_COLOR[tone] }}>
          <div><div><strong>{r.score}</strong><small>/100점</small></div></div>
        </div>
        <div className="stack" style={{ gap: 6 }}>
          <div className="row">
            <strong>{data.courseName}</strong>
            <Badge tone={tone}>지각 위험 {LATENESS_LABEL[r.level]}</Badge>
          </div>
          <span className="muted small">
            {data.date !== todayIso() && <Badge tone="info">{new Date(data.date + 'T00:00:00').toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric', weekday: 'short' })}</Badge>}{' '}
            {hhmm(r.classStart)} 수업{data.room ? ` · ${data.room}` : ''}
          </span>
          <div className="row" style={{ gap: 16 }}>
            <div className="metric"><span className="label">준비 시작</span><span className="value" style={{ fontSize: '1.3rem' }}>{hhmm(r.startPreparingBy)}</span></div>
            <div className="metric"><span className="label">권장 출발</span><span className="value" style={{ fontSize: '1.3rem', color: 'var(--primary)' }}>{hhmm(r.recommendedDeparture)}</span></div>
          </div>
        </div>
      </div>

      {r.expectedLateMinutes != null && (
        <div className="alert alert-bad">지금 준비를 시작해도 약 {r.expectedLateMinutes}분 늦을 것으로 계산됩니다. 지각 사유서를 준비하세요.</div>
      )}

      <div className="row small muted">
        <span>이동 {r.baseTravelMinutes}분 → 보정 {r.adjustedTravelMinutes}분 · 준비 {r.prepMinutes}분 · 여유 {r.bufferMinutes}분</span>
      </div>
      <div className="row small">
        {w && (
          <>
            <ModeBadge mode={w.mode} label="날씨" />
            <span className="muted">{w.source} · {w.precipType === 'NONE' ? '강수 없음' : { RAIN: '비', RAIN_SNOW: '비/눈', SNOW: '눈', SHOWER: '소나기' }[w.precipType]}
              {w.precipProbability != null ? ` · 강수확률 ${w.precipProbability}%` : ''}{w.temperature != null ? ` · ${w.temperature}℃` : ''}</span>
          </>
        )}
        <span className="muted">· 이동시간: {r.travelSource}</span>
      </div>

      {r.factors.length > 0 ? (
        <ul className="factor-list" aria-label="위험 점수 근거">
          {r.factors.map((f) => <li key={f.code}><span>{f.description}</span><span className="pts">+{f.points}</span></li>)}
        </ul>
      ) : <p className="muted small" style={{ margin: 0 }}>위험 요인이 없습니다.</p>}
      <p className="muted small" style={{ margin: 0 }}>※ 확률이 아니라 규칙 점수의 합입니다. 각 항목의 점수를 더하면 총점이 됩니다.</p>

      <div className="row">
        <button className="btn btn-small" onClick={explain} disabled={loading}>{loading ? '설명 생성 중…' : 'AI로 원인·대응 설명 보기'}</button>
      </div>
      {exp && <ExplanationBox explanation={exp} />}
    </div>
  );
}
