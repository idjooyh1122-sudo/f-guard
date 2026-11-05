package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fguard.domain.ProviderMode;
import com.fguard.domain.WeatherSnapshot;
import com.fguard.dto.AiDtos.ExcuseRequest;
import com.fguard.dto.AiDtos.ExcuseResponse;
import com.fguard.entity.ClassSchedule;
import com.fguard.entity.Course;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 지각 사유서 생성. GPT 에는 "확인된 사실"만 전달하고, 날씨는 실제(REAL) 기상청 데이터일 때만 사실로 인용한다.
 */
@Service
@Transactional(readOnly = true)
public class ExcuseService {
    private final CourseService courseService;
    private final LatenessService latenessService;
    private final AiService ai;

    public ExcuseService(CourseService courseService, LatenessService latenessService, AiService ai) {
        this.courseService = courseService;
        this.latenessService = latenessService;
        this.ai = ai;
    }

    public ExcuseResponse generate(ExcuseRequest req) {
        List<String> facts = new ArrayList<>();
        List<String> extraCautions = new ArrayList<>();
        LocalTime classStart = null;
        String courseLine = "과목 미지정";
        if (req.courseId() != null) {
            Course c = courseService.require(req.courseId());
            int dow = req.classDate().getDayOfWeek().getValue();
            classStart = c.getSchedules().stream().filter(s -> s.getDayOfWeek() == dow)
                    .map(ClassSchedule::getStartTime).findFirst().orElse(null);
            courseLine = "과목: " + c.getName() + (c.getProfessor() != null ? " (" + c.getProfessor() + " 교수님)" : "");
            facts.add(courseLine);
        }
        facts.add("수업 날짜: " + req.classDate() + (classStart != null ? " " + classStart + " 시작" : ""));
        if (req.minutesLate() != null) facts.add("지각 시간(학생 입력): 약 " + req.minutesLate() + "분");

        WeatherSnapshot weather = null;
        boolean weatherCited = false;
        if (req.includeWeather()) {
            weather = latenessService.weatherFor(req.classDate().atTime(classStart != null ? classStart : LocalTime.of(9, 0)));
            if (weather.mode() == ProviderMode.REAL) {
                facts.add("%s(%s 기준): %s, 강수확률 %s%%".formatted(weather.source(), weather.forecastTime(),
                        weather.precipType().label, weather.precipProbability() == null ? "-" : weather.precipProbability()));
                weatherCited = true;
            } else {
                extraCautions.add("실제 기상 데이터를 조회하지 못해 날씨 정보는 사유서에 넣지 않았습니다.");
            }
        }
        extraCautions.add("교통 지연 정보는 확인된 데이터가 없어 포함하지 않았습니다. 사실이라면 직접 추가하세요.");

        String context = """
                학생이 입력한 지각 사유: %s
                확인된 사실:
                - %s
                학생 이름: %s / 학번: %s
                위 정보만 사용해서 지각 사유서(letter)와 메시지(message)를 작성해 줘.
                """.formatted(req.reason(), String.join("\n- ", facts),
                blankTo(req.studentName(), "[이름]"), blankTo(req.studentId(), "[학번]"));

        AiService.ExcuseResult r = ai.writeExcuse(context, facts, req.reason());
        JsonNode j = r.json();
        List<String> cited = new ArrayList<>();
        j.path("citedFacts").forEach(x -> cited.add(x.asText()));
        List<String> cautions = new ArrayList<>();
        j.path("cautions").forEach(x -> cautions.add(x.asText()));
        cautions.addAll(extraCautions);
        String letter = j.path("letter").asText();
        String message = j.path("message").asText();
        if (r.mode() == ProviderMode.MOCK) {
            letter = fill(letter, req);
            message = fill(message, req);
        }
        return new ExcuseResponse(letter, message, cited, cautions, weather, weatherCited, r.mode(), r.model(), r.error());
    }

    private static String fill(String s, ExcuseRequest req) {
        if (req.studentName() != null && !req.studentName().isBlank()) s = s.replace("[이름]", req.studentName());
        if (req.studentId() != null && !req.studentId().isBlank()) s = s.replace("[학번]", req.studentId());
        return s;
    }

    private static String blankTo(String s, String d) {
        return s == null || s.isBlank() ? d : s;
    }
}
