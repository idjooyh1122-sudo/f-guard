-- F-Guard 초기 스키마
-- 해커톤 버전은 단일 사용자(id=1)를 기본으로 하되, 모든 데이터는 user_id 로 소유자를 구분한다.

CREATE TABLE app_user (
    id                  BIGSERIAL PRIMARY KEY,
    display_name        VARCHAR(50)  NOT NULL,
    home_label          VARCHAR(100),
    home_lat            DOUBLE PRECISION,
    home_lng            DOUBLE PRECISION,
    campus_label        VARCHAR(100),
    campus_lat          DOUBLE PRECISION,
    campus_lng          DOUBLE PRECISION,
    prep_minutes        INTEGER      NOT NULL DEFAULT 40 CHECK (prep_minutes BETWEEN 0 AND 300),
    base_travel_minutes INTEGER      NOT NULL DEFAULT 30 CHECK (base_travel_minutes BETWEEN 0 AND 300),
    buffer_minutes      INTEGER      NOT NULL DEFAULT 10 CHECK (buffer_minutes BETWEEN 0 AND 120),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE course (
    id                            BIGSERIAL PRIMARY KEY,
    user_id                       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name                          VARCHAR(100) NOT NULL,
    professor                     VARCHAR(50),
    credits                       INTEGER CHECK (credits BETWEEN 0 AND 10),
    total_sessions                INTEGER CHECK (total_sessions BETWEEN 1 AND 200),
    -- 평가 규칙 (null = 문서/사용자 입력으로 확인되지 않음)
    absence_fail_threshold        INTEGER CHECK (absence_fail_threshold >= 1),
    lates_per_absence             INTEGER CHECK (lates_per_absence >= 1),
    absence_penalty               NUMERIC(5, 2) CHECK (absence_penalty BETWEEN 0 AND 100),
    late_penalty                  NUMERIC(5, 2) CHECK (late_penalty BETWEEN 0 AND 100),
    fail_cutoff                   NUMERIC(5, 2) CHECK (fail_cutoff BETWEEN 0 AND 100),
    missing_assignment_fail_count INTEGER CHECK (missing_assignment_fail_count >= 1),
    rules_note                    TEXT,
    rules_confirmed               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_course_user ON course (user_id);

CREATE TABLE class_schedule (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT   NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=월 ... 7=일 (ISO)
    start_time  TIME     NOT NULL,
    end_time    TIME     NOT NULL,
    room        VARCHAR(50),
    CHECK (end_time > start_time)
);
CREATE INDEX idx_schedule_course ON class_schedule (course_id);

CREATE TABLE grade_component (
    id        BIGSERIAL PRIMARY KEY,
    course_id BIGINT        NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    type      VARCHAR(20)   NOT NULL CHECK (type IN ('ATTENDANCE', 'ASSIGNMENT', 'MIDTERM', 'FINAL', 'QUIZ', 'OTHER')),
    name      VARCHAR(50)   NOT NULL,
    weight    NUMERIC(5, 2) NOT NULL CHECK (weight BETWEEN 0 AND 100),
    score     NUMERIC(5, 2) CHECK (score BETWEEN 0 AND 100) -- 100점 환산 점수, null = 아직 미평가
);
CREATE INDEX idx_component_course ON grade_component (course_id);

CREATE TABLE attendance_record (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT      NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    class_date DATE        NOT NULL,
    status     VARCHAR(10) NOT NULL CHECK (status IN ('ATTENDED', 'LATE', 'ABSENT', 'EXCUSED')),
    note       VARCHAR(200),
    UNIQUE (course_id, class_date)
);

CREATE TABLE assignment (
    id        BIGSERIAL PRIMARY KEY,
    course_id BIGINT        NOT NULL REFERENCES course (id) ON DELETE CASCADE,
    title     VARCHAR(150)  NOT NULL,
    due_at    TIMESTAMPTZ,
    max_score NUMERIC(6, 2) NOT NULL DEFAULT 100 CHECK (max_score > 0),
    score     NUMERIC(6, 2) CHECK (score >= 0),
    status    VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUBMITTED', 'MISSED')),
    source    VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'SYLLABUS')),
    confirmed BOOLEAN       NOT NULL DEFAULT TRUE,
    CHECK (score IS NULL OR score <= max_score)
);
CREATE INDEX idx_assignment_course ON assignment (course_id);

CREATE TABLE syllabus_analysis (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    course_id     BIGINT       REFERENCES course (id) ON DELETE SET NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    file_size     BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'FAILED')),
    provider_mode VARCHAR(10)  NOT NULL CHECK (provider_mode IN ('REAL', 'MOCK')),
    model         VARCHAR(100),
    result_json   JSONB,
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    confirmed_at  TIMESTAMPTZ
);

CREATE TABLE google_credential (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    access_token  TEXT        NOT NULL,
    refresh_token TEXT,
    expires_at    TIMESTAMPTZ NOT NULL,
    scope         TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE calendar_sync (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   BIGINT       NOT NULL UNIQUE REFERENCES assignment (id) ON DELETE CASCADE,
    google_event_id VARCHAR(255) NOT NULL,
    html_link       TEXT,
    synced_due_at   TIMESTAMPTZ  NOT NULL,
    synced_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 기본 사용자 (단일 사용자 데모). 좌표는 강원대학교 춘천캠퍼스 인근.
INSERT INTO app_user (display_name, home_label, home_lat, home_lng, campus_label, campus_lat, campus_lng)
VALUES ('강원대 학생', '집', 37.8813, 127.7298, '강원대학교 춘천캠퍼스', 37.8691, 127.7437);
