import { useState } from 'react';
import { api } from '../services/api';
import { todayIso } from '../services/format';
import type { Course, ExcuseResponse } from '../types/api';
import { Card, ErrorBox, Field, ModeBadge } from './ui';

export default function ExcuseForm({ courses }: { courses: Course[] }) {
  const [form, setForm] = useState<{
    courseId: number | null; classDate: string; reason: string; minutesLate: string; studentName: string; studentId: string; includeWeather: boolean;
  }>({
    courseId: courses[0]?.id ?? null, classDate: todayIso(), reason: '', minutesLate: '', studentName: '', studentId: '', includeWeather: true,
  });
  const [res, setRes] = useState<ExcuseResponse | null>(null);
  const [letter, setLetter] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<Error | null>(null);
  const [copied, setCopied] = useState('');

  async function generate(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setErr(null);
    try {
      const r = await api.excuse({
        courseId: form.courseId, classDate: form.classDate, reason: form.reason,
        minutesLate: form.minutesLate ? Number(form.minutesLate) : null,
        studentName: form.studentName || null, studentId: form.studentId || null, includeWeather: form.includeWeather,
      });
      setRes(r);
      setLetter(r.letter);
      setMessage(r.message);
    } catch (er) {
      setErr(er as Error);
    } finally {
      setLoading(false);
    }
  }

  async function copy(text: string, what: string) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(what);
      setTimeout(() => setCopied(''), 1500);
    } catch {
      setCopied('복사 실패 — 직접 선택해 복사하세요');
    }
  }

  return (
    <div className="grid grid-2">
      <Card title="지각 사유 입력">
        <form className="stack" onSubmit={generate}>
          <div className="form-grid">
            <Field label="과목">
              <select value={form.courseId ?? ''} onChange={(e) => setForm({ ...form, courseId: e.target.value ? Number(e.target.value) : null })}>
                <option value="">선택 안 함</option>
                {courses.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="수업 날짜"><input type="date" required value={form.classDate} onChange={(e) => setForm({ ...form, classDate: e.target.value })} /></Field>
            <Field label="지각 시간(분)"><input type="number" min={0} max={300} value={form.minutesLate} onChange={(e) => setForm({ ...form, minutesLate: e.target.value })} /></Field>
            <Field label="이름"><input maxLength={30} value={form.studentName} onChange={(e) => setForm({ ...form, studentName: e.target.value })} /></Field>
            <Field label="학번"><input maxLength={20} value={form.studentId} onChange={(e) => setForm({ ...form, studentId: e.target.value })} /></Field>
          </div>
          <Field label="지각 사유 *" hint="실제 있었던 일만 적어 주세요. AI는 입력한 사실과 확인된 데이터만 사용합니다.">
            <textarea required maxLength={1000} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} placeholder="예: 버스가 정류장을 지나쳐 다음 버스를 기다렸습니다." />
          </Field>
          <label className="checkbox"><input type="checkbox" checked={form.includeWeather} onChange={(e) => setForm({ ...form, includeWeather: e.target.checked })} /> 기상청 날씨 정보 포함 (실제 데이터일 때만)</label>
          <ErrorBox error={err} />
          <div><button className="btn btn-primary" disabled={loading || !form.reason.trim()}>{loading ? '작성 중…' : 'AI로 사유서 작성'}</button></div>
        </form>
      </Card>

      <Card title="생성 결과" action={res && <ModeBadge mode={res.mode} label={res.mode === 'REAL' ? res.model ?? 'GPT' : '템플릿'} />}>
        {!res ? <p className="muted" style={{ margin: 0 }}>사유를 입력하고 작성 버튼을 누르세요.</p> : (
          <div className="stack">
            {res.error && <div className="alert alert-warn">GPT 호출 실패로 템플릿을 사용했습니다: {res.error}</div>}
            <Field label="공식 사유서 (수정 가능)"><textarea value={letter} onChange={(e) => setLetter(e.target.value)} style={{ minHeight: 260 }} /></Field>
            <div className="row"><button className="btn btn-small" onClick={() => copy(letter, '사유서')}>사유서 복사</button></div>
            <Field label="카카오톡 메시지 (수정 가능)"><textarea value={message} onChange={(e) => setMessage(e.target.value)} /></Field>
            <div className="row">
              <button className="btn btn-small" onClick={() => copy(message, '메시지')}>메시지 복사</button>
              {copied && <span className="small muted">{copied.includes('실패') ? copied : `${copied} 복사됨`}</span>}
            </div>
            {res.citedFacts.length > 0 && <div><strong className="small">사용한 사실</strong><ul className="issues">{res.citedFacts.map((f, i) => <li key={i}>{f}</li>)}</ul></div>}
            {res.cautions.length > 0 && <div><strong className="small">제출 전 확인</strong><ul className="issues muted">{res.cautions.map((f, i) => <li key={i}>{f}</li>)}</ul></div>}
          </div>
        )}
      </Card>
    </div>
  );
}
