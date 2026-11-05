import { NavLink, Outlet } from 'react-router-dom';

const NAV = [
  { to: '/', label: '대시보드', icon: '🏠', end: true },
  { to: '/courses', label: '과목', icon: '📚' },
  { to: '/attendance', label: '출석', icon: '🕘' },
  { to: '/grades', label: '성적', icon: '📊' },
  { to: '/ai', label: 'AI 분석', icon: '✨' },
  { to: '/calendar', label: '캘린더', icon: '📅' },
  { to: '/settings', label: '설정', icon: '⚙️' },
];

export default function Layout() {
  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">F</span>
          <div>
            <strong>F를 막아라</strong>
            <small>AI 학점 방어 서비스</small>
          </div>
        </div>
        <nav aria-label="주요 메뉴">
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
              <span aria-hidden>{n.icon}</span>
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <p className="sidebar-foot">Know AI · KNU AI Hackathon</p>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
