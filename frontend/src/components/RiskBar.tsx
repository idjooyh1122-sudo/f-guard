import type { GradeDiagnosis } from '../types/api';

/** 총점 구간 [최저, 최고] 와 F 기준선을 한 줄로 보여준다. */
export default function RiskBar({ d }: { d: GradeDiagnosis }) {
  const lo = Math.max(0, Math.min(100, d.lowerBound));
  const hi = Math.max(0, Math.min(100, d.upperBound));
  return (
    <div>
      <div className="bar" role="img" aria-label={`예상 총점 ${lo}~${hi}점${d.failCutoff != null ? `, F 기준 ${d.failCutoff}점` : ''}`}>
        <div className="bar-range" style={{ left: `${lo}%`, width: `${Math.max(1, hi - lo)}%` }} />
        {d.failCutoff != null && <div className="bar-cut" style={{ left: `${d.failCutoff}%` }} />}
      </div>
      <div className="row-between small muted" style={{ marginTop: 4 }}>
        <span>확보 {d.lowerBound}점</span>
        {d.failCutoff != null && <span style={{ color: 'var(--bad)' }}>F 기준 {d.failCutoff}점</span>}
        <span>최대 {d.upperBound}점</span>
      </div>
    </div>
  );
}
