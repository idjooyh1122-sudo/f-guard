package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyllabusValidationTest {
    final ObjectMapper mapper = new ObjectMapper();
    final JsonSchemaValidator validator = new JsonSchemaValidator(mapper);

    @Test
    void MOCK_결과는_스키마를_통과한다() {
        JsonNode mock = MockData.syllabus(mapper, 2026);
        assertThat(validator.validate(JsonSchemaValidator.SYLLABUS, mock)).isEmpty();
    }

    @Test
    void 필수필드_누락과_추가필드는_스키마_오류() {
        ObjectNode bad = (ObjectNode) MockData.syllabus(mapper, 2026);
        bad.remove("weights");
        bad.put("guess", "x");
        List<String> errors = validator.validate(JsonSchemaValidator.SYLLABUS, bad);
        assertThat(errors).isNotEmpty();
    }

    @Test
    void 잘못된_날짜와_범위는_의미검증_오류() {
        ObjectNode r = (ObjectNode) MockData.syllabus(mapper, 2026);
        ((ObjectNode) r.get("assignments").get(0)).put("dueDate", "11월 12일");
        ((ObjectNode) r.get("failCriteria")).put("failCutoff", 160);
        List<String> issues = SyllabusService.semanticIssues(r);
        assertThat(issues).anyMatch(s -> s.startsWith("[오류]") && s.contains("마감일 형식"));
        assertThat(issues).anyMatch(s -> s.startsWith("[오류]") && s.contains("F 기준 점수"));
        assertThat(issues).anyMatch(s -> s.startsWith("[확인]") && s.contains("과제 3의 마감일이 문서에 없습니다"));
    }

    @Test
    void 파일_시그니처로_형식_판별() {
        byte[] pdf = "%PDF-1.7 aaaaaaaa".getBytes();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] exe = {'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(FileTypeDetector.detect(pdf)).contains("application/pdf");
        assertThat(FileTypeDetector.detect(png)).contains("image/png");
        assertThat(FileTypeDetector.detect(exe)).isEmpty();
    }
}
