import { useEffect, useState } from 'react';
import type { Course } from '../types/api';

/** 과목 목록에서 선택된 과목 id 를 유지한다 (목록이 바뀌면 첫 과목으로). */
export function useCourseSelection(courses: Course[] | undefined) {
  const [selected, setSelected] = useState<number | null>(null);
  useEffect(() => {
    if (!courses || courses.length === 0) { setSelected(null); return; }
    if (selected == null || !courses.some((c) => c.id === selected)) setSelected(courses[0].id);
  }, [courses, selected]);
  return [selected, setSelected] as const;
}
