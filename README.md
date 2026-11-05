# 🛡️ F를 막아라 (F-Guard)

AI 기반 대학 생활 F학점 전방위 방어 서비스 — 팀 Know AI (KNU AI HACKATHON Zero to One)

- **1차 방어선 · 출석**: 날씨·이동시간·준비시간·지각 이력으로 **규칙 기반 지각 위험 점수**와 권장 출발 시각을 계산하고, GPT가 원인과 대응을 설명합니다. 지각했다면 **확인된 사실만 사용한** 지각 사유서·메시지를 작성합니다.
- **2차 방어선 · 성적**: 강의계획서(PDF·이미지)를 GPT가 분석해 평가 비율·감점 규칙·F 기준·과제 마감일을 추출하고, 사용자가 검토 후 저장합니다. Java 결정론적 계산기로 **F 위험을 역산**하고 "이번 과제를 놓치면?" 같은 가정 시뮬레이션을 지원합니다. 과제 마감일은 **Google Calendar에 D-3·D-1 알림**과 함께 중복 없이 등록합니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Web / Data JPA / Validation, Flyway, RestClient |
| Frontend | React 19, TypeScript, Vite 6, React Router 7 |
| Database | PostgreSQL 16 |
| AI | OpenAI Responses API (Structured Outputs + 로컬 JSON 스키마 검증) |
| 외부 API | 기상청 단기예보(공공데이터포털), 카카오모빌리티 길찾기(선택), Google OAuth + Calendar API v3 |
| Test | JUnit 5, Mockito/MockRestServiceServer, Spring MockMvc(실제 PostgreSQL), Vitest + Testing Library |

## 빠른 시작

### 1. 준비물
- JDK 21, Maven 3.9+ (또는 IntelliJ 내장 Maven)
- Node.js 20+ / npm
- PostgreSQL 16

### 2. 데이터베이스 만들기

```bash
sudo -u postgres psql -c "CREATE USER fguard WITH PASSWORD 'fguard';" -c "CREATE DATABASE fguard OWNER fguard;" -c "CREATE DATABASE fguard_test OWNER fguard;"
```

테이블은 백엔드가 처음 시작할 때 Flyway가 자동으로 만듭니다 (`backend/src/main/resources/db/migration`).

### 3. 환경 변수

```bash
cp .env.example .env
```

키를 비워 두면 해당 기능은 **MOCK 모드**로 동작하며 화면에 `MOCK` 배지가 표시됩니다.

| 변수 | 없을 때 동작 |
|---|---|
| `OPENAI_API_KEY`, `OPENAI_VISION_MODEL`, `OPENAI_TEXT_MODEL` | 강의계획서 분석은 예시 결과, 사유서·설명은 템플릿 문장 (MOCK) |
| `KMA_SERVICE_KEY` | 고정된 시연용 날씨(비 70%) 사용 (MOCK) |
| `KAKAO_MOBILITY_REST_KEY` | 설정 화면의 "평소 이동 시간" 사용 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 캘린더 연동 비활성, `.ics` 내보내기만 사용 |

> OpenAI 모델명은 [공식 모델 문서](https://platform.openai.com/docs/models)에서 **PDF/이미지 입력과 Structured Outputs를 지원하는 모델**을 확인해 입력하세요. 강의계획서 분석(`VISION`)은 정확도가 높은 모델, 설명·사유서(`TEXT`)는 경량 모델을 권장합니다.

### 4. 실행

**IntelliJ IDEA**: `f-guard` 폴더를 열면 `.run/` 폴더의 실행 구성 **F-Guard Backend**, **F-Guard Frontend**가 표시됩니다. Backend의 작업 디렉터리는 `backend/`입니다.

**터미널**:

```bash
cd backend && mvn spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

브라우저에서 http://localhost:5173 에 접속합니다. Vite 개발 서버가 `/api` 요청을 `http://localhost:8080`으로 전달합니다.
처음이라면 대시보드의 **샘플 데이터로 체험** 버튼으로 예시 과목 3개를 만들 수 있습니다.

### 5. 테스트

```bash
cd backend && mvn test
```

```bash
cd frontend && npm test
```

백엔드 통합 테스트는 로컬 PostgreSQL의 `fguard_test` DB를 사용합니다 (`TEST_DB_URL`로 변경 가능). 테스트는 외부 API 키를 비워 둔 상태로 실행되어 실제 유료 API를 호출하지 않습니다.

## Google Calendar 연동 설정

1. Google Cloud Console에서 프로젝트를 만들고 **Google Calendar API**를 사용 설정합니다.
2. OAuth 동의 화면을 구성합니다. 테스트 모드라면 시연에 쓸 계정을 **테스트 사용자**로 추가하세요.
3. OAuth 클라이언트 ID(웹 애플리케이션)를 만들고, 승인된 리디렉션 URI에 `http://localhost:8080/api/calendar/google/callback`을 추가합니다.
4. `.env`에 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`을 넣고 백엔드를 재시작합니다.

요청 권한은 `https://www.googleapis.com/auth/calendar.events`(일정 추가·수정)뿐입니다.

## 폴더 구조

```
f-guard/
├─ backend/                 Spring Boot (controller / service / repository / entity / dto / config / client / domain)
│  └─ src/main/resources/
│     ├─ db/migration/      Flyway SQL
│     └─ schemas/           GPT 응답 JSON 스키마 (OpenAI strict 모드 + 서버 검증에 공용)
├─ frontend/                React + TypeScript + Vite (components / pages / services / types / hooks)
├─ docs/IMPLEMENTATION.md   구현 기록, 설계 결정, 남은 과제
├─ .run/                    IntelliJ 실행 구성
└─ .env.example
```

## 주요 API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/dashboard` | 오늘 수업, 다음 수업 지각 위험, 마감 과제, F 위험 과목, 연동 상태 |
| CRUD | `/api/courses`, `/api/courses/{id}/attendance`, `/api/courses/{id}/assignments`, `/api/assignments/{id}` | 과목·시간표·평가 규칙·출석·과제 |
| GET/POST | `/api/courses/{id}/diagnosis`, `/diagnosis/what-if`, `/diagnosis/explain` | F 위험 진단, 가정 시뮬레이션, GPT 설명 |
| GET/POST | `/api/courses/{id}/lateness`, `/lateness/explain`, `/api/lateness/next` | 지각 위험 점수, GPT 설명 |
| POST | `/api/syllabus/analyze` (multipart), `/api/syllabus/{id}/confirm` | 강의계획서 분석 → 검토 후 확정 |
| POST | `/api/excuse` | 지각 사유서·메시지 생성 |
| GET/POST | `/api/calendar/google/*`, `/api/calendar/sync`, `/api/calendar/export.ics` | OAuth, 일정 등록, .ics 내보내기 |
| GET/PUT | `/api/settings`, `/api/integrations` | 위치·준비시간, 외부 API 연동 상태 |
| POST | `/api/demo/seed` | 샘플 데이터 (과목이 없을 때만) |

## 안전장치 요약

- 지각 위험도는 **확률이 아니라 규칙 점수의 합**이며, 모든 근거 항목과 점수를 화면에 보여줍니다.
- F 확정은 **사용자가 규칙을 확인한 과목**에서, 입력값으로 논리적으로 확정될 때만 표시합니다. 정보가 부족하면 `계산 불가`로 표시합니다.
- 강의계획서에 없는 값은 `null` / "확인 필요"로 두고, 날짜 없는 과제에 마감일을 만들지 않습니다.
- 지각 사유서에는 학생이 입력한 사유와 **실제로 조회된(REAL) 데이터만** 사실로 넣습니다. MOCK 날씨나 확인되지 않은 교통 지연은 넣지 않습니다.
- API 키와 OAuth 토큰은 `.env`와 DB에만 두고, 로그와 `toString()`에서 숨깁니다.
- 이루리, K-cloud 같은 학내 시스템은 **바로가기 링크만** 제공합니다. 자동 로그인과 크롤링은 하지 않습니다.
