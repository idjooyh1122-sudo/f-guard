import type { ReactNode } from 'react';
import type { Explanation, ProviderMode } from '../types/api';
import type { Tone } from '../services/format';

export function Card({ title, action, children, className = '' }: { title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || action) && (
        <header className="card-head">
          {title && <h2>{title}</h2>}
          {action}
        </header>
      )}
      {children}
    </section>
  );
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge tone-${tone}`}>{children}</span>;
}

/** REAL/MOCK 데이터 출처 표시. MOCK 은 항상 눈에 띄게 보여준다. */
export function ModeBadge({ mode, label }: { mode: ProviderMode | string; label?: string }) {
  if (mode === 'REAL') return <span className="badge tone-info" title="실제 API 응답">{label ?? 'LIVE'}</span>;
  if (mode === 'MOCK') return <span className="badge tone-mock" title="실제 API 가 아닌 시연용 데이터">MOCK{label ? ` · ${label}` : ''}</span>;
  return <span className="badge tone-neutral">{label ?? mode}</span>;
}

export function ErrorBox({ error, onRetry }: { error: Error | null | undefined; onRetry?: () => void }) {
  if (!error) return null;
  return (
    <div className="alert alert-bad" role="alert">
      <span>{error.message}</span>
      {onRetry && <button className="btn btn-small" onClick={onRetry}>다시 시도</button>}
    </div>
  );
}

export function Loading({ text = '불러오는 중…' }: { text?: string }) {
  return <div className="loading" aria-live="polite"><span className="spinner" />{text}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function ExplanationBox({ explanation }: { explanation: Explanation }) {
  return (
    <div className="explain">
      <div className="explain-head">
        <strong>AI 설명</strong>
        <ModeBadge mode={explanation.mode} label={explanation.mode === 'REAL' ? explanation.model ?? 'GPT' : '규칙 기반 문장'} />
      </div>
      <p>{explanation.summary}</p>
      {explanation.actions.length > 0 && (
        <ul>{explanation.actions.map((a, i) => <li key={i}>{a}</li>)}</ul>
      )}
      {explanation.error && <p className="muted small">GPT 호출 실패로 대체 문장을 표시합니다: {explanation.error}</p>}
    </div>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}
