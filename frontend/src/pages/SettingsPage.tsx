import { useEffect, useState } from 'react';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import { num } from '../services/format';
import type { IntegrationItem, Settings } from '../types/api';
import { Badge, Card, ErrorBox, Field, Loading, ModeBadge } from '../components/ui';

const PRESETS = [
  { label: '강원대 춘천캠퍼스', lat: 37.8691, lng: 127.7437 },
  { label: '강원대 삼척캠퍼스', lat: 37.4528, lng: 129.1620 },
  { label: '강원대 도계캠퍼스', lat: 37.2395, lng: 129.0517 },
];

function IntegrationRow({ name, item }: { name: string; item: IntegrationItem }) {
  return (
    <li>
      <div><strong>{name}</strong><div className="small muted">{item.detail}</div></div>
      <div className="row">
        {item.mode === 'MOCK' ? <ModeBadge mode="MOCK" /> : item.mode === 'REAL' ? <Badge tone="good">실제 연동</Badge> : <Badge tone="neutral">{item.mode === 'MANUAL' ? '수동 입력값' : '미설정'}</Badge>}
        {item.connected && <Badge tone="info">계정 연결됨</Badge>}
      </div>
    </li>
  );
}

export default function SettingsPage() {
  const settings = useAsync(() => api.settings());
  const integ = useAsync(() => api.integrations());
  const [form, setForm] = useState<Settings | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [err, setErr] = useState<Error | null>(null);

  useEffect(() => { if (settings.data) setForm(settings.data); }, [settings.data]);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!form) return;
    setSaving(true); setErr(null); setSaved(false);
    try {
      setForm(await api.updateSettings(form));
      setSaved(true);
    } catch (er) { setErr(er as Error); } finally { setSaving(false); }
  }

  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => form && setForm({ ...form, [k]: v });
  const n = (v: number | null | undefined) => (v == null ? '' : String(v));

  return (
    <div className="stack">
      <div className="page-head"><div><h1>환경 설정</h1><p>지각 위험 계산에 쓰이는 위치·준비시간과 외부 API 연동 상태입니다.</p></div></div>
      <Card title="내 등교 정보">
        {!form ? (settings.error ? <ErrorBox error={settings.error} /> : <Loading />) : (
          <form className="stack" onSubmit={save}>
            <div className="form-grid">
              <Field label="이름"><input required maxLength={50} value={form.displayName} onChange={(e) => set('displayName', e.target.value)} /></Field>
              <Field label="준비 시간(분)" hint="기상~출발"><input type="number" min={0} max={300} value={form.prepMinutes} onChange={(e) => set('prepMinutes', Number(e.target.value))} /></Field>
              <Field label="평소 이동 시간(분)" hint="대중교통 기준"><input type="number" min={0} max={300} value={form.baseTravelMinutes} onChange={(e) => set('baseTravelMinutes', Number(e.target.value))} /></Field>
              <Field label="도착 여유(분)"><input type="number" min={0} max={120} value={form.bufferMinutes} onChange={(e) => set('bufferMinutes', Number(e.target.value))} /></Field>
            </div>
            <fieldset>
              <legend>출발지</legend>
              <div className="form-grid">
                <Field label="이름"><input value={form.homeLabel ?? ''} onChange={(e) => set('homeLabel', e.target.value)} /></Field>
                <Field label="위도"><input type="number" step="0.0001" value={n(form.homeLat)} onChange={(e) => set('homeLat', num(e.target.value))} /></Field>
                <Field label="경도"><input type="number" step="0.0001" value={n(form.homeLng)} onChange={(e) => set('homeLng', num(e.target.value))} /></Field>
              </div>
              <div className="row" style={{ marginTop: 8 }}>
                <button type="button" className="btn btn-small" onClick={() => navigator.geolocation?.getCurrentPosition((p) =>
                  setForm((f) => f && { ...f, homeLat: Number(p.coords.latitude.toFixed(4)), homeLng: Number(p.coords.longitude.toFixed(4)) }))}>현재 위치 사용</button>
              </div>
            </fieldset>
            <fieldset>
              <legend>학교(목적지) · 날씨 조회 위치</legend>
              <div className="row" style={{ marginBottom: 8 }}>
                {PRESETS.map((p) => (
                  <button type="button" key={p.label} className="btn btn-small" onClick={() => setForm({ ...form, campusLabel: p.label, campusLat: p.lat, campusLng: p.lng })}>{p.label}</button>
                ))}
              </div>
              <div className="form-grid">
                <Field label="이름"><input value={form.campusLabel ?? ''} onChange={(e) => set('campusLabel', e.target.value)} /></Field>
                <Field label="위도"><input type="number" step="0.0001" value={n(form.campusLat)} onChange={(e) => set('campusLat', num(e.target.value))} /></Field>
                <Field label="경도"><input type="number" step="0.0001" value={n(form.campusLng)} onChange={(e) => set('campusLng', num(e.target.value))} /></Field>
              </div>
              <p className="small muted" style={{ marginBottom: 0 }}>캠퍼스 좌표는 대략적인 값입니다. 정확한 건물 좌표로 바꿔 쓰세요.</p>
            </fieldset>
            <ErrorBox error={err} />
            <div className="row">
              <button className="btn btn-primary" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
              {saved && <span className="small muted">저장되었습니다.</span>}
            </div>
          </form>
        )}
      </Card>

      <Card title="API 연동 상태" action={<button className="btn btn-small" onClick={integ.reload}>새로고침</button>}>
        {!integ.data ? (integ.error ? <ErrorBox error={integ.error} /> : <Loading />) : (
          <ul className="list">
            <IntegrationRow name="OpenAI GPT" item={integ.data.openai} />
            <IntegrationRow name="기상청 단기예보" item={integ.data.weather} />
            <IntegrationRow name="이동시간" item={integ.data.travel} />
            <IntegrationRow name="Google Calendar" item={integ.data.google} />
          </ul>
        )}
        <p className="small muted" style={{ marginBottom: 0 }}>API 키는 서버의 <code>.env</code> 파일에서만 설정합니다. 화면이나 브라우저에는 키가 저장되지 않습니다.</p>
      </Card>
    </div>
  );
}
