import { api, ApiRequestError } from '../services/api';

describe('api client', () => {
  afterEach(() => vi.restoreAllMocks());

  it('서버 오류 응답을 코드와 메시지가 있는 예외로 변환', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
      JSON.stringify({ code: 'VALIDATION_FAILED', message: '입력값을 확인해 주세요.', details: ['name: 비어 있음'] }),
      { status: 400 }));
    await expect(api.courses()).rejects.toMatchObject({ status: 400, code: 'VALIDATION_FAILED', details: ['name: 비어 있음'] });
  });

  it('네트워크 오류는 안내 메시지로', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    const err = await api.dashboard().catch((e) => e);
    expect(err).toBeInstanceOf(ApiRequestError);
    expect(err.message).toContain('서버에 연결할 수 없습니다');
  });

  it('204 응답은 undefined', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(api.deleteCourse(1)).resolves.toBeUndefined();
  });

  it('파일 업로드는 multipart(FormData)로 Content-Type 을 직접 지정하지 않는다', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }));
    await api.analyzeSyllabus(new File(['%PDF'], 'a.pdf', { type: 'application/pdf' }));
    const init = spy.mock.calls[0][1]!;
    expect(init.body).toBeInstanceOf(FormData);
    expect(init.headers).toBeUndefined();
  });
});
