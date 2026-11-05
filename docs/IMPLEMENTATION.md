# 구현 기록 (IMPLEMENTATION.md)

마지막 갱신: 2026-11-05 (1일차)

## 1. 개발 환경 점검 결과 (클라우드 개발 컨테이너)

| 항목 | 결과 |
|---|---|
| JDK | OpenJDK 21.0.12 ✅ |
| 빌드 | Maven 3.9.11 ✅ (Gradle 미설치 → Maven 채택) |
| Node / npm | v22.23 / 10.9 ✅ |
| PostgreSQL | 16.15 설치됨 → 서비스 시작, `fguard`, `fguard_test` DB 생성 ✅ |
| Git | 2.43, 빈 폴더였으므로 `git init` |
| IntelliJ | 컨테이너에는 없음 → `.run/` 실행 구성 파일만 제공 (로컬 IntelliJ 실행은 아직 검증 안 함) |
| 네트워크 | Maven Central·npm ✅ / **api.openai.com, apis.data.go.kr 차단(403)**, Google API 호스트는 연결 가능 |

→ 결론적으로 OpenAI와 기상청 API는 이 환경에서 **실제 호출을 검증하지 못했습니다**. 두 클라이언트는 MockRestServiceServer 단위 테스트(요청 형식과 응답 파싱)와 MOCK 모드 동작으로만 검증했습니다.

## 2. 설계 결정

### 2.1 AI가 하는 일과 코드가 하는 일
| 기능 | 계산 주체 | GPT 역할 |
|---|---|---|
| 지각 위험 점수 | `LatenessRiskCalculator` (규칙 점수 합) | 결과 설명, 대응 행동 제안 |
| F 위험 진단 | `GradeRiskCalculator` (점수 구간 [lower, upper]) | 결과 설명 |
| 강의계획서 | GPT가 추출 → 스키마·의미 검증 → **사용자 검토 후 저장** | 비정형 문서에서 구조화 데이터 추출 |
| 지각 사유서 | 사실 목록은 서버가 구성 | 정중한 문장 작성 |

### 2.2 F 위험 진단 알고리즘
- 평가 항목마다 최저점(지금 확정된 점수)과 최고점(남은 평가를 모두 만점)을 구합니다.
  - 출석: `100 − 환산결석×결석감점 − 남은지각×지각감점`이 최고점. 최저점은 남은 회차를 모두 결석한 경우(전체 회차를 모르면 0점)입니다.
  - 과제: 과제마다 같은 비중으로 가정합니다. 미제출이거나 마감이 지난 과제는 0점, 채점된 과제는 실제 점수, 나머지는 0~100점입니다.
  - 점수를 입력한 항목은 그 점수로 고정, 나머지는 0~100점입니다.
- 결과 판정:
  - 최고점 < F 기준: **F 확정** (규칙 미확인이면 '위험')
  - 최저점 ≥ F 기준: 안전
  - 그 사이: 남은 평가에서 필요한 비율로 판정 (80% 이상 위험, 50% 이상 주의, 그 미만 낮음)
- 결석·과제 F 규칙에 도달하면 확정. 결석 여유가 1회면 '위험', 2회면 '주의' 이상으로 올립니다.
- 평가 비율 합계가 100%가 아니거나 F 기준이 없으면 **계산 불가**로 표시합니다.

### 2.3 지각 위험 점수 (확률 아님)
| 요인 | 점수 | 이동시간 보정 |
|---|---|---|
| 눈·비/눈 | 30 | +40% |
| 비 | 20 | +20% |
| 소나기 | 15 | +15% |
| 강수확률 60% 이상 (강수 없음 예보) | 10 | +10% |
| 풍속 9m/s 이상 | 10 | +10% |
| 최근 30일 지각률 30% 이상 / 15% 이상 / 그 외 | 20 / 10 / 5 | – |
| 9시 30분 이전 수업 | 5 | – |
| (당일) 지금 준비해도 지각 / 여유가 버퍼 미만 / 15분 안에 준비 시작 | 50 / 25 / 10 | – |

- 권장 출발 = 수업 시작 − 보정 이동시간 − 도착 여유
- 준비 시작 = 권장 출발 − 준비 시간
- 단계: 0–24 낮음, 25–49 보통, 50–74 높음, 75+ 매우 높음
- 실제 지각 기록이 쌓이면 가중치를 학습으로 바꿀 수 있습니다 (3순위 과제).

### 2.4 OpenAI 연동 (`OpenAiClient`)
- `POST /v1/responses`, `text.format = {type: "json_schema", strict: true, schema}`를 사용합니다.
- PDF는 `input_file`(`file_data` = data URL), 이미지는 `input_image`(`image_url` = data URL)로 보냅니다.
- 스키마 파일 `resources/schemas/*.schema.json` 하나를 OpenAI strict 스키마와 서버 측 networknt 검증에 같이 씁니다.
- 응답 처리: `incomplete`, `refusal`, HTTP 오류를 각각 구분해 예외로 만들고, 메시지에 API 키를 포함하지 않습니다.
- 모델명은 코드에 넣지 않고 `.env`의 `OPENAI_VISION_MODEL` / `OPENAI_TEXT_MODEL`로 받습니다. 이 환경에서 OpenAI 문서에 접근할 수 없어서 기본값을 정하지 않았습니다.
- ⚠️ 요청과 응답 형식은 Responses API 문서 지식을 바탕으로 작성했고, **이 환경에서는 실제 호출로 검증하지 못했습니다**. 키를 넣은 뒤 강의계획서 한 건으로 꼭 확인하세요.

### 2.5 기상청 단기예보 (`WeatherClient`)
- `GET {base}/getVilageFcst?serviceKey&pageNo&numOfRows&dataType=JSON&base_date&base_time&nx&ny`
- 발표시각(02·05·…·23시)이 지나고 15분 뒤 가장 최근 회차를 씁니다. 위경도는 LCC 공식으로 격자 좌표로 바꿉니다(서울시청 → 60,127 테스트 통과).
- 사용 카테고리: PTY(강수형태), POP, TMP, WSD, PCP. 응답은 30분 캐시합니다.
- 키가 없거나 호출에 실패하면 MOCK 날씨(`mode=MOCK`)를 반환하고, 화면에 MOCK으로 표시합니다.
- ⚠️ 이 환경에서는 apis.data.go.kr 접근이 차단되어 실제 응답을 확인하지 못했습니다(파싱은 예시 응답으로 테스트).

### 2.6 이동시간
- 기본값은 사용자가 설정한 "평소 이동 시간"(대중교통 기준)입니다.
- 선택 사항으로 카카오모빌리티 길찾기 `GET /v1/directions`(자동차 경로, `KakaoAK` 헤더)를 붙였습니다. 이것도 이 환경에서 문서와 실제 호출을 확인하지 못해 **기본값은 꺼져 있습니다**.
- 대중교통 경로 API(ODsay, TAGO 등)는 공식 문서를 확인하지 못해 연동하지 않았습니다.

### 2.7 Google Calendar
- OAuth 웹 서버 흐름(`state` 검증 10분, `access_type=offline`), 토큰 자동 갱신을 구현했습니다.
- 이벤트 ID를 `fguardu{userId}a{assignmentId}`(base32hex 규칙)로 정해 중복 생성을 막습니다. `calendar_sync` 테이블에 마감일 스냅샷을 저장합니다.
  - 마감일이 같으면 건너뜁니다(`SKIPPED_DUPLICATE`). 캘린더에서 삭제됐으면 다시 등록합니다.
  - 마감일이 바뀌었으면 기존 일정을 수정합니다(`UPDATED`). 409 충돌이면 기존 이벤트를 조회해 수정합니다.
- 등록한 뒤 GET으로 다시 조회해 `verified`를 확인합니다.
- 알림은 `reminders.overrides` popup 4320분(D-3), 1440분(D-1)입니다.
- 연동 없이 쓸 수 있도록 `.ics` 내보내기(VALARM D-3·D-1)도 제공합니다.
- ⚠️ OAuth 클라이언트가 없어 실제 Google 일정 등록은 검증하지 못했습니다. 미설정 시 오류 처리와 .ics 생성만 테스트했습니다.

### 2.8 기타
- 로그인이 없는 단일 사용자 모드입니다(`CurrentUser` = 1). 모든 테이블에 `user_id`로 소유권을 걸어 두어 로그인 기능을 붙이기 쉽게 했습니다.
- 업로드 파일은 매직 바이트로 PDF, PNG, JPEG, WEBP만 받고, 최대 10MB입니다. 원본 파일은 저장하지 않고 분석 결과만 저장합니다.
- 이루리, K-cloud는 정확한 공식 주소를 확인하지 못해, 대시보드 빠른 링크를 `VITE_QUICK_LINKS`로 설정하게 했습니다(기본값은 강원대 홈페이지).

## 3. 데이터베이스 (Flyway V1)
`app_user`, `course`(평가 규칙 포함), `class_schedule`, `grade_component`, `attendance_record`(과목+날짜 UNIQUE), `assignment`, `syllabus_analysis`(JSONB), `google_credential`, `calendar_sync`(assignment UNIQUE)
- 범위(CHECK), 외래키 ON DELETE CASCADE, 시간 순서(end_time > start_time), 점수 ≤ 만점 같은 제약을 걸었습니다.

## 4. 테스트 결과 (2026-11-05)

| 대상 | 결과 |
|---|---|
| `GradeRiskCalculatorTest` | 8/8 ✅ (결석 F, 지각 환산, F 확정, 안전, 필요비율 역산, 계산 불가, 과제 미제출 시뮬레이션, 결석 여유) |
| `LatenessRiskCalculatorTest` | 4/4 ✅ |
| `KmaWeatherTest` | 3/3 ✅ (격자 변환, 발표시각, 응답 파싱) |
| `OpenAiClientTest` | 5/5 ✅ (요청 형식, PDF/이미지 입력, 스키마 위반 거부, API 오류, 거절) |
| `SyllabusValidationTest` | 4/4 ✅ |
| `ApiIntegrationTest` (실제 PostgreSQL) | 6/6 ✅ (CRUD, 대시보드, 업로드·확정, 사유서, 캘린더, 지각 위험) |
| Frontend Vitest | 11/11 ✅ |
| 브라우저 E2E (Playwright, 수동 스크립트) | 대시보드 → 샘플 → AI 설명 → 시뮬레이션 → 출석 기록 → 계획서 업로드·확정 → 사유서, 모바일 3화면. 콘솔 오류 0, 가로 스크롤 없음 ✅ |

## 5. 완료 조건 점검

| # | 조건 | 상태 |
|---|---|---|
| 1 | IntelliJ에서 Spring Boot 실행 | ⚠️ 실행 구성은 제공했지만 컨테이너에 IntelliJ가 없어 미검증 (`java -jar`, `mvn`으로는 실행 확인) |
| 2 | React 웹 서버 실행 | ✅ |
| 3 | 프론트·백엔드 통신 | ✅ (Vite 프록시 `/api`) |
| 4 | PostgreSQL 연결 | ✅ |
| 5 | 과목·출석 관리 | ✅ |
| 6 | 강의계획서 GPT 분석 | ⚠️ MOCK과 클라이언트 단위 테스트만 완료. 실제 GPT 호출은 키와 네트워크가 필요해 미검증 |
| 7 | F학점 위험 계산 테스트 | ✅ |
| 8 | 지각 사유서 생성 | ⚠️ MOCK 템플릿 경로만 검증. GPT 경로는 미검증 |
| 9 | 외부 API 또는 명시적 Mock | ✅ MOCK을 화면에 명시. 실제 기상청·Google 호출은 미검증 |
| 10 | README 설치·실행 방법 | ✅ |

## 6. 남은 과제 (우선순위 순)
1. OpenAI 키와 모델 설정 후 실제 강의계획서 2~3건으로 추출 정확도 확인, 프롬프트 다듬기
2. 기상청 키로 실제 예보 확인, Google OAuth 클라이언트로 일정 등록 확인
3. 대중교통 이동시간 API 공식 문서 확인 후 연동
4. 로그인(다중 사용자), 개인별 지각 위험 가중치 고도화
5. 학사 공지 데이터 연동 (기획서 충족표 항목)
