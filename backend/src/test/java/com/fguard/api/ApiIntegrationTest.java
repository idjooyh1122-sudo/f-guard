package com.fguard.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 PostgreSQL(fguard_test DB)에 대해 전체 API 흐름을 검증한다.
 * 외부 API 키는 비워 두어 MOCK 경로가 사용되는지도 함께 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/fguard_test}",
        "fguard.openai.api-key=",
        "fguard.weather.service-key=",
        "fguard.travel.kakao-rest-key=",
        "fguard.google.client-id=",
        "fguard.google.client-secret="
})
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE course, syllabus_analysis, google_credential RESTART IDENTITY CASCADE");
    }

    JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    static final String COURSE = """
            {"name":"운영체제","professor":"박교수","credits":3,"totalSessions":30,
             "absenceFailThreshold":8,"latesPerAbsence":3,"absencePenalty":10,"failCutoff":60,"rulesConfirmed":true,
             "schedules":[{"dayOfWeek":1,"startTime":"09:00","endTime":"10:15","room":"공학관 101"}],
             "components":[{"type":"ATTENDANCE","name":"출석","weight":10},{"type":"ASSIGNMENT","name":"과제","weight":20},
                           {"type":"MIDTERM","name":"중간","weight":30,"score":50},{"type":"FINAL","name":"기말","weight":40}]}""";

    @Test
    void 과목_출석_과제_CRUD와_진단() throws Exception {
        JsonNode created = json(mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(COURSE))
                .andExpect(status().isCreated()).andReturn());
        long id = created.get("id").asLong();
        assertThat(created.get("weightSum").decimalValue()).isEqualByComparingTo("100");
        assertThat(created.get("schedules")).hasSize(1);

        mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // 출석: 같은 날짜는 갱신(upsert)
        mvc.perform(put("/api/courses/" + id + "/attendance").contentType(MediaType.APPLICATION_JSON)
                .content("{\"classDate\":\"2026-11-02\",\"status\":\"LATE\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/courses/" + id + "/attendance").contentType(MediaType.APPLICATION_JSON)
                .content("{\"classDate\":\"2026-11-02\",\"status\":\"ABSENT\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/courses/" + id + "/attendance/summary"))
                .andExpect(jsonPath("$.absent").value(1)).andExpect(jsonPath("$.late").value(0));

        // 과제
        JsonNode a = json(mvc.perform(post("/api/courses/" + id + "/assignments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"과제1\",\"dueAt\":\"2026-11-20T23:59:00+09:00\",\"status\":\"PENDING\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(put("/api/assignments/" + a.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"과제1\",\"maxScore\":10,\"score\":20,\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SCORE"));

        // 진단 + 가정 시뮬레이션
        mvc.perform(get("/api/courses/" + id + "/diagnosis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis.effectiveAbsences").value(1))
                .andExpect(jsonPath("$.diagnosis.status").exists());
        mvc.perform(post("/api/courses/" + id + "/diagnosis/what-if").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extraAbsences\":7,\"extraLates\":0,\"missAssignmentIds\":[]}"))
                .andExpect(jsonPath("$.diagnosis.status").value("F_CONFIRMED"));
        mvc.perform(post("/api/courses/" + id + "/diagnosis/explain"))
                .andExpect(jsonPath("$.mode").value("MOCK"))
                .andExpect(jsonPath("$.summary").isNotEmpty());

        // 검증 오류
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(delete("/api/courses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/courses/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void 샘플데이터와_대시보드() throws Exception {
        mvc.perform(post("/api/demo/seed")).andExpect(status().isOk()).andExpect(jsonPath("$.createdCourses").value(3));
        mvc.perform(post("/api/demo/seed")).andExpect(status().isConflict());
        JsonNode d = json(mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andReturn());
        assertThat(d.get("courseCount").asInt()).isEqualTo(3);
        assertThat(d.get("riskCourses")).hasSize(3);
        assertThat(d.get("todayClasses").size()).isGreaterThanOrEqualTo(2);
        assertThat(d.path("integrations").path("openai").path("mode").asText()).isEqualTo("MOCK");
        assertThat(d.path("integrations").path("weather").path("mode").asText()).isEqualTo("MOCK");
        // 샘플 '대학영어'는 F 기준 미확인 → 계산 불가
        boolean hasCannot = false;
        for (JsonNode c : d.get("riskCourses")) hasCannot |= "CANNOT_CALCULATE".equals(c.path("diagnosis").path("status").asText());
        assertThat(hasCannot).isTrue();
    }

    @Test
    void 강의계획서_업로드_검증_분석_확정() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ-not-a-pdf-at-all".getBytes());
        mvc.perform(multipart("/api/syllabus/analyze").file(exe))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));

        MockMultipartFile pdf = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", "%PDF-1.4 dummy content".getBytes());
        JsonNode an = json(mvc.perform(multipart("/api/syllabus/analyze").file(pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.mode").value("MOCK"))
                .andReturn());
        assertThat(an.get("validationIssues").toString()).contains("과제 3의 마감일이 문서에 없습니다");

        ObjectNode edited = (ObjectNode) an.get("result").deepCopy();
        edited.put("courseName", "자료구조");
        String body = mapper.writeValueAsString(mapper.createObjectNode().set("result", edited));
        JsonNode confirmed = json(mvc.perform(post("/api/syllabus/" + an.get("id").asLong() + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andReturn());
        long courseId = confirmed.get("courseId").asLong();

        mvc.perform(get("/api/courses/" + courseId))
                .andExpect(jsonPath("$.name").value("자료구조"))
                .andExpect(jsonPath("$.rulesConfirmed").value(true))
                .andExpect(jsonPath("$.components.length()").value(4))
                .andExpect(jsonPath("$.failCutoff").value(60));
        // 날짜 없는 과제는 마감일 null 로 저장 (AI 가 날짜를 만들지 않음)
        JsonNode as = json(mvc.perform(get("/api/courses/" + courseId + "/assignments")).andReturn());
        assertThat(as).hasSize(3);
        long withoutDate = 0;
        for (JsonNode x : as) if (x.get("dueAt").isNull()) withoutDate++;
        assertThat(withoutDate).isEqualTo(1);

        // 형식이 틀린 결과는 확정 불가
        ObjectNode broken = (ObjectNode) edited.deepCopy();
        broken.remove("weights");
        mvc.perform(post("/api/syllabus/" + an.get("id").asLong() + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(mapper.createObjectNode().set("result", broken))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SCHEMA_INVALID"));
    }

    @Test
    void 지각사유서_MOCK은_확인되지않은_날씨를_인용하지_않음() throws Exception {
        long id = json(mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(COURSE)).andReturn()).get("id").asLong();
        mvc.perform(post("/api/excuse").contentType(MediaType.APPLICATION_JSON).content("""
                        {"courseId":%d,"classDate":"2026-11-02","reason":"알람을 잘못 맞춰 늦게 일어났습니다","minutesLate":15,
                         "studentName":"홍길동","includeWeather":true}""".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MOCK"))
                .andExpect(jsonPath("$.weatherCited").value(false))
                .andExpect(jsonPath("$.letter").value(org.hamcrest.Matchers.containsString("홍길동")))
                .andExpect(jsonPath("$.letter").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("강수확률"))))
                .andExpect(jsonPath("$.cautions[*]").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("날씨 정보는 사유서에 넣지 않았습니다"))));
    }

    @Test
    void 캘린더_미설정시_명확한_오류와_ICS_내보내기() throws Exception {
        mvc.perform(post("/api/demo/seed")).andExpect(status().isOk());
        mvc.perform(get("/api/calendar/google/status")).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(get("/api/calendar/google/auth-url"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("GOOGLE_NOT_CONFIGURED"));
        String ics = mvc.perform(get("/api/calendar/export.ics")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(ics).startsWith("BEGIN:VCALENDAR").contains("BEGIN:VEVENT").contains("TRIGGER:-PT4320M").contains("TRIGGER:-PT1440M");
    }

    @Test
    void 지각위험_조회() throws Exception {
        long id = json(mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(COURSE)).andReturn()).get("id").asLong();
        // 2026-11-02 은 월요일
        mvc.perform(get("/api/courses/" + id + "/lateness").param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risk.weather.mode").value("MOCK"))
                .andExpect(jsonPath("$.risk.recommendedDeparture").exists())
                .andExpect(jsonPath("$.risk.factors[*].code").value(org.hamcrest.Matchers.hasItem("WEATHER_RAIN")));
        mvc.perform(get("/api/courses/" + id + "/lateness").param("date", "2026-11-03"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NO_CLASS"));
    }
}
