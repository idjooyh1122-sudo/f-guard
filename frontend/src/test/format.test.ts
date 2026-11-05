import { dDay, hhmm, num, todayIso } from '../services/format';

describe('format', () => {
  const now = new Date(2026, 10, 5, 12, 0); // 2026-11-05 12:00 (로컬)

  it('D-day 계산', () => {
    expect(dDay(new Date(2026, 10, 8, 23, 59).toISOString(), now)).toBe('D-3');
    expect(dDay(new Date(2026, 10, 5, 23, 59).toISOString(), now)).toBe('D-day');
    expect(dDay(new Date(2026, 10, 5, 9, 0).toISOString(), now)).toBe('마감');
    expect(dDay(null, now)).toBe('');
  });

  it('시간 표시', () => {
    expect(hhmm('09:00:00')).toBe('09:00');
    expect(hhmm('2026-11-05T08:40:00')).toBe('08:40');
    expect(hhmm(null)).toBe('-');
  });

  it('숫자 입력 파싱: 빈 값은 null(=문서에 없음)', () => {
    expect(num('')).toBeNull();
    expect(num('  ')).toBeNull();
    expect(num('12.5')).toBe(12.5);
    expect(num('abc')).toBeNull();
  });

  it('오늘 날짜 ISO', () => {
    expect(todayIso(now)).toBe('2026-11-05');
  });
});
