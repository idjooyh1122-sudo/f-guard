import type { Course } from '../types/api';

export default function CourseTabs({ courses, selected, onSelect }: { courses: Course[]; selected: number | null; onSelect: (id: number) => void }) {
  return (
    <div className="tabs" role="tablist" aria-label="과목 선택">
      {courses.map((c) => (
        <button key={c.id} role="tab" aria-selected={selected === c.id} className={`tab${selected === c.id ? ' active' : ''}`} onClick={() => onSelect(c.id)}>
          {c.name}
        </button>
      ))}
    </div>
  );
}
