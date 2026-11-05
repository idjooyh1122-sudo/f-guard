package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/** OpenAI 미설정 시 사용하는 시연용 MOCK 결과. 화면에는 항상 MOCK 으로 표시된다. */
final class MockData {
    private MockData() {}

    static JsonNode syllabus(ObjectMapper m, int year) {
        ObjectNode r = m.createObjectNode();
        r.put("courseName", "[MOCK] 자료구조");
        r.put("professor", "[MOCK] 김교수");
        r.put("totalSessions", 30);
        ObjectNode w = r.putObject("weights");
        w.put("attendance", 10); w.put("assignment", 20); w.put("midterm", 30); w.put("final", 40);
        w.putNull("quiz"); w.putNull("other"); w.putNull("otherLabel");
        ObjectNode ar = r.putObject("attendanceRules");
        ar.put("absenceFailThreshold", 8); ar.put("latesPerAbsence", 3);
        ar.put("absencePenalty", 10); ar.putNull("latePenalty");
        ar.put("description", "[MOCK] 결석 1회당 출석점수 1점(총점 기준) 감점, 지각 3회는 결석 1회, 수업일수 1/4 이상 결석 시 F");
        ObjectNode asr = r.putObject("assignmentRules");
        asr.putNull("missingAssignmentFailCount");
        asr.put("latePolicy", "[MOCK] 지각 제출 시 50% 감점");
        ObjectNode fc = r.putObject("failCriteria");
        fc.put("failCutoff", 60);
        fc.put("description", "[MOCK] 총점 60점 미만 F");
        ArrayNode as = r.putArray("assignments");
        ObjectNode a1 = as.addObject();
        a1.put("title", "[MOCK] 과제 1: 연결 리스트 구현"); a1.put("dueDate", year + "-11-12"); a1.put("dueTime", "23:59"); a1.put("evidence", "[MOCK] 11/12(수) 23:59까지 e-class 제출");
        ObjectNode a2 = as.addObject();
        a2.put("title", "[MOCK] 과제 2: 이진 탐색 트리"); a2.put("dueDate", year + "-11-26"); a2.put("dueTime", "23:59"); a2.put("evidence", "[MOCK] 11/26(수) 23:59까지");
        ObjectNode a3 = as.addObject();
        a3.put("title", "[MOCK] 텀 프로젝트 보고서"); a3.putNull("dueDate"); a3.putNull("dueTime"); a3.put("evidence", "[MOCK] 학기 말 제출 (일자 추후 공지)");
        ArrayNode u = r.putArray("uncertainFields");
        u.add("attendanceRules.latePenalty");
        u.add("assignmentRules.missingAssignmentFailCount");
        u.add("assignments[2].dueDate");
        ArrayNode ev = r.putArray("evidence");
        ObjectNode e1 = ev.addObject(); e1.put("field", "weights"); e1.put("quote", "[MOCK] 출석 10%, 과제 20%, 중간 30%, 기말 40%");
        return r;
    }

    static JsonNode excuse(ObjectMapper m, List<String> facts, String reason) {
        ObjectNode r = m.createObjectNode();
        String factLine = facts.isEmpty() ? "" : "\n확인된 사항: " + String.join(", ", facts) + "\n";
        r.put("letter", """
                [MOCK 템플릿 — GPT 미사용]
                지각 사유서

                수신: 담당 교수님
                %s
                사유: %s
                %s
                수업에 늦게 참석하게 되어 진심으로 죄송합니다. 앞으로는 출발 시간을 앞당겨 같은 일이 반복되지 않도록 하겠습니다.

                [학번] [이름] 올림""".formatted(facts.isEmpty() ? "" : facts.getFirst(), reason, factLine));
        r.put("message", "교수님, 안녕하세요. [이름]입니다. 오늘 수업에 늦게 되어 죄송합니다. 사유는 " + reason
                + " 때문입니다. 재발하지 않도록 주의하겠습니다.");
        ArrayNode cf = r.putArray("citedFacts");
        cf.add("학생 입력 사유: " + reason);
        facts.forEach(cf::add);
        ArrayNode c = r.putArray("cautions");
        c.add("MOCK 템플릿입니다. OpenAI API 키를 설정하면 GPT 가 문장을 작성합니다.");
        c.add("제출 전에 [학번], [이름] 자리를 채우세요.");
        return r;
    }
}
