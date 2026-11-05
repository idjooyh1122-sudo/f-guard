import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RiskBar from '../components/RiskBar';
import { ModeBadge } from '../components/ui';
import CourseForm, { EMPTY_COURSE } from '../components/CourseForm';
import type { GradeDiagnosis } from '../types/api';

const diag: GradeDiagnosis = {
  status: 'HIGH', lowerBound: 20, upperBound: 70, failCutoff: 60, requiredPercentOfRemaining: 80,
  effectiveAbsences: 2, remainingAbsencesBeforeF: 6, missedAssignments: 0, components: [], reasons: [], warnings: [],
};

describe('components', () => {
  it('RiskBar 는 확보/최대 점수와 F 기준을 보여준다', () => {
    render(<RiskBar d={diag} />);
    expect(screen.getByText('확보 20점')).toBeInTheDocument();
    expect(screen.getByText('F 기준 60점')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /예상 총점 20~70점, F 기준 60점/ })).toBeInTheDocument();
  });

  it('MOCK 데이터는 항상 MOCK 배지로 표시된다', () => {
    render(<ModeBadge mode="MOCK" label="날씨" />);
    expect(screen.getByText('MOCK · 날씨')).toBeInTheDocument();
  });

  it('CourseForm: 평가 비율 합계를 실시간 표시하고 제출한다', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<CourseForm initial={EMPTY_COURSE} onSubmit={onSubmit} onCancel={() => {}} submitting={false} />);
    expect(screen.getByText('합계 100%')).toBeInTheDocument();

    const weights = screen.getAllByLabelText('비율(%)');
    await user.clear(weights[0]);
    await user.type(weights[0], '5');
    expect(screen.getByText('합계 95%')).toBeInTheDocument();

    await user.type(screen.getByLabelText('과목명 *'), '자료구조');
    await user.click(screen.getByRole('button', { name: '저장' }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ name: '자료구조', rulesConfirmed: false });
    expect(onSubmit.mock.calls[0][0].components[0].weight).toBe(5);
  });
});
