package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fguard.client.OpenAiClient;
import com.fguard.client.OpenAiClient.Attachment;
import com.fguard.domain.ProviderMode;
import com.fguard.dto.AiDtos.Explanation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * GPT 를 사용하는 기능의 진입점. OpenAI 가 설정되지 않은 경우 MOCK 결과를 반환하며
 * 결과에 항상 mode(REAL/MOCK)를 포함한다.
 */
@Service
public class AiService {

    static final String SYLLABUS_INSTRUCTIONS = """
            너는 대학 강의계획서에서 성적 평가 정보를 추출하는 도구다.
            규칙:
            1. 문서에 명시된 내용만 추출한다. 문서에 없거나 불분명한 값은 반드시 null 로 두고 uncertainFields 에 필드 경로를 적는다.
            2. 비율(weights)은 100점 만점 기준 퍼센트 숫자로 적는다 (예: 20%% → 20).
            3. absencePenalty/latePenalty 는 '출석 점수 100점 만점 기준' 1회당 감점이다. 문서가 총점 기준 감점만 말하면
               (총점 감점 ÷ 출석 비율 × 100) 으로 환산하고, 환산이 불가능하면 null.
            4. absenceFailThreshold 는 'N회 이상 결석 시 F'의 N. '1/4 이상 결석'처럼 비율이면 totalSessions 가 명시된 경우에만 계산한다.
            5. failCutoff 는 총점 몇 점 미만이 F 인지 명시된 경우에만 숫자로 적는다.
            6. assignments 에는 문서에 날짜가 적힌 과제만 넣는다. 날짜가 없으면 dueDate=null. 날짜를 추측하거나 만들지 않는다.
               dueDate 는 YYYY-MM-DD, dueTime 은 HH:MM(24시간). 연도가 없으면 %d년으로 본다.
            7. evidence 에는 핵심 값의 근거가 된 원문 문장을 짧게 인용한다.
            """;

    static final String EXCUSE_INSTRUCTIONS = """
            너는 대학생이 교수님께 보내는 지각 사유서와 짧은 메시지를 정중한 한국어로 작성하는 도우미다.
            절대 규칙:
            - 학생이 직접 입력한 사유와 '확인된 사실' 목록에 있는 내용만 사실로 쓴다.
            - 확인되지 않은 교통 지연, 사고, 질병, 날씨를 만들어 내지 않는다.
            - 과장하거나 동정을 유도하는 표현을 쓰지 않는다. 사과와 재발 방지 의지를 담는다.
            - letter: 제목/수신/본문/날짜/서명을 포함한 공식 사유서 (학번·이름이 없으면 [학번], [이름] 자리표시).
            - message: 카카오톡 등으로 보낼 3~4문장 메시지.
            - citedFacts: 본문에 사용한 사실 목록. cautions: 학생이 제출 전에 직접 확인해야 할 점.
            """;

    static final String EXPLAIN_INSTRUCTIONS = """
            너는 대학생 학업 코치다. 주어진 계산 결과(JSON)는 프로그램이 규칙으로 계산한 것이므로
            숫자를 바꾸거나 새로운 수치를 만들지 말고, 결과의 의미와 원인을 쉬운 한국어로 2~3문장 summary 로 설명하라.
            actions 에는 오늘 바로 할 수 있는 구체적 행동 2~4개를 적는다. 확률이나 예측 정확도를 주장하지 않는다.
            """;

    private final OpenAiClient openAi;
    private final ObjectMapper mapper;

    public AiService(OpenAiClient openAi, ObjectMapper mapper) {
        this.openAi = openAi;
        this.mapper = mapper;
    }

    public boolean isReal() {
        return openAi.isConfigured();
    }

    public record SyllabusResult(JsonNode json, ProviderMode mode, String model) {}

    public SyllabusResult analyzeSyllabus(String fileName, String contentType, byte[] bytes, int defaultYear) {
        if (!openAi.isConfigured()) {
            return new SyllabusResult(MockData.syllabus(mapper, defaultYear), ProviderMode.MOCK, null);
        }
        OpenAiClient.Result r = openAi.structured(true, JsonSchemaValidator.SYLLABUS,
                SYLLABUS_INSTRUCTIONS.formatted(defaultYear),
                "첨부한 강의계획서(" + fileName + ")에서 평가 정보를 추출해 줘.",
                List.of(new Attachment(fileName, contentType, bytes)));
        return new SyllabusResult(r.json(), ProviderMode.REAL, r.model());
    }

    public record ExcuseResult(JsonNode json, ProviderMode mode, String model, String error) {}

    public ExcuseResult writeExcuse(String context, List<String> confirmedFacts, String reason) {
        if (openAi.isConfigured()) {
            try {
                OpenAiClient.Result r = openAi.structured(false, JsonSchemaValidator.EXCUSE, EXCUSE_INSTRUCTIONS, context, List.of());
                return new ExcuseResult(r.json(), ProviderMode.REAL, r.model(), null);
            } catch (OpenAiClient.OpenAiException e) {
                return new ExcuseResult(MockData.excuse(mapper, confirmedFacts, reason), ProviderMode.MOCK, null, e.getMessage());
            }
        }
        return new ExcuseResult(MockData.excuse(mapper, confirmedFacts, reason), ProviderMode.MOCK, null, null);
    }

    /** 계산 결과 설명. GPT 실패 시 규칙 기반 문장으로 대체(MOCK)하고 오류를 함께 반환한다. */
    public Explanation explain(String topic, Object computed, List<String> fallbackReasons, List<String> fallbackActions) {
        String error = null;
        if (openAi.isConfigured()) {
            try {
                String payload = topic + "\n계산 결과:\n" + mapper.writeValueAsString(computed);
                OpenAiClient.Result r = openAi.structured(false, JsonSchemaValidator.EXPLANATION, EXPLAIN_INSTRUCTIONS, payload, List.of());
                List<String> actions = new ArrayList<>();
                r.json().path("actions").forEach(a -> actions.add(a.asText()));
                return new Explanation(r.json().path("summary").asText(), actions, ProviderMode.REAL, r.model(), null);
            } catch (Exception e) {
                error = e.getMessage();
            }
        }
        String summary = fallbackReasons.isEmpty() ? "특이사항이 없습니다." : String.join(" ", fallbackReasons);
        return new Explanation(summary, fallbackActions, ProviderMode.MOCK, null, error);
    }
}
