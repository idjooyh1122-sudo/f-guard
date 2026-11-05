import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../services/api';
import { useAsync } from '../hooks/useAsync';
import type { SyllabusAnalysis, SyllabusResult } from '../types/api';
import { Badge, Card, Empty, ErrorBox, Loading, ModeBadge } from '../components/ui';
import SyllabusReview from '../components/SyllabusReview';
import ExcuseForm from '../components/ExcuseForm';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPT = '.pdf,.png,.jpg,.jpeg,.webp,application/pdf,image/png,image/jpeg,image/webp';

export default function AiPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'excuse' ? 'excuse' : 'syllabus';
  const courses = useAsync(() => api.courses());
  const history = useAsync(() => api.syllabusList());
  const [current, setCurrent] = useState<SyllabusAnalysis | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadErr, setUploadErr] = useState<Error | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmErr, setConfirmErr] = useState<Error | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  async function upload(file: File | undefined) {
    if (!file) return;
    setUploadErr(null);
    if (file.size > MAX_BYTES) { setUploadErr(new Error('파일은 10MB 이하만 업로드할 수 있습니다.')); return; }
    setUploading(true);
    try {
      const a = await api.analyzeSyllabus(file);
      setCurrent(a);
      await history.reload();
    } catch (e) {
      setUploadErr(e as Error);
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  async function confirm(result: SyllabusResult, courseId: number | null) {
    if (!current) return;
    setConfirming(true);
    setConfirmErr(null);
    try {
      setCurrent(await api.confirmSyllabus(current.id, result, courseId));
      await Promise.all([history.reload(), courses.reload()]);
    } catch (e) {
      setConfirmErr(e as Error);
    } finally {
      setConfirming(false);
    }
  }

  return (
    <div className="stack">
      <div className="page-head"><div><h1>AI 분석</h1><p>강의계획서 분석과 지각 사유서 작성을 GPT가 돕습니다. 결과는 항상 직접 확인 후 사용하세요.</p></div></div>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'syllabus'} className={`tab${tab === 'syllabus' ? ' active' : ''}`} onClick={() => setParams({})}>📄 강의계획서 분석</button>
        <button role="tab" aria-selected={tab === 'excuse'} className={`tab${tab === 'excuse' ? ' active' : ''}`} onClick={() => setParams({ tab: 'excuse' })}>✉️ 지각 사유서</button>
      </div>

      {tab === 'excuse' ? (
        courses.data ? <ExcuseForm courses={courses.data} /> : <Loading />
      ) : (
        <>
          <Card title="강의계획서 업로드">
            <div
              className="empty" style={{ cursor: 'pointer' }}
              onClick={() => fileRef.current?.click()}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => { e.preventDefault(); void upload(e.dataTransfer.files[0]); }}
              role="button" tabIndex={0} onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && fileRef.current?.click()}
            >
              {uploading ? <Loading text="AI가 강의계획서를 분석하는 중… (최대 1분)" /> : (
                <>
                  <strong>PDF 또는 이미지(PNG·JPG·WEBP)를 끌어다 놓거나 클릭해서 선택</strong>
                  <div className="small">최대 10MB · 평가 비율, 출석 감점, F 기준, 과제 마감일을 추출합니다</div>
                </>
              )}
            </div>
            <input ref={fileRef} type="file" accept={ACCEPT} hidden onChange={(e) => upload(e.target.files?.[0])} />
            <div style={{ marginTop: 10 }}><ErrorBox error={uploadErr} /></div>
          </Card>

          {current && current.status === 'FAILED' && (
            <div className="alert alert-bad">분석 실패: {current.errorMessage}</div>
          )}
          {current && current.result && (
            <Card title="분석 결과 검토">
              <SyllabusReview key={`${current.id}-${current.status}`} analysis={current} courses={courses.data ?? []} onConfirm={confirm} confirming={confirming} error={confirmErr} />
            </Card>
          )}

          <Card title="분석 기록">
            {history.loading && !history.data ? <Loading /> : !history.data?.length ? <Empty>아직 분석한 강의계획서가 없습니다.</Empty> : (
              <ul className="list">
                {history.data.map((h) => (
                  <li key={h.id}>
                    <div>
                      <strong>{h.result?.courseName ?? h.fileName}</strong>
                      <div className="small muted">{h.fileName} · {new Date(h.createdAt).toLocaleString('ko-KR')}</div>
                    </div>
                    <div className="row">
                      <ModeBadge mode={h.mode} />
                      <Badge tone={h.status === 'CONFIRMED' ? 'good' : h.status === 'FAILED' ? 'bad' : 'warn'}>
                        {{ CONFIRMED: '확정', FAILED: '실패', PENDING_REVIEW: '검토 대기' }[h.status]}
                      </Badge>
                      {h.result && <button className="btn btn-small" onClick={() => { setConfirmErr(null); setCurrent(h); }}>열기</button>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
