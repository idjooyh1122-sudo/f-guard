package com.fguard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fguard.config.FGuardProperties;
import com.fguard.service.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * OpenAI Responses API 클라이언트 (POST /v1/responses).
 * <ul>
 *   <li>구조화 출력: text.format = {type: json_schema, strict: true}</li>
 *   <li>PDF 입력: input_file(file_data = data URL), 이미지 입력: input_image(image_url = data URL)</li>
 *   <li>응답 JSON 은 로컬 JSON 스키마로 한 번 더 검증한다.</li>
 * </ul>
 * API 키는 헤더로만 전송하며 로그에 남기지 않는다.
 */
@Component
public class OpenAiClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    public record Attachment(String fileName, String contentType, byte[] bytes) {}

    public record Result(JsonNode json, String model) {}

    public static class OpenAiException extends RuntimeException {
        public OpenAiException(String message) { super(message); }
        public OpenAiException(String message, Throwable cause) { super(message, cause); }
    }

    private final FGuardProperties.OpenAi props;
    private final ObjectMapper mapper;
    private final JsonSchemaValidator validator;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiClient(FGuardProperties properties, ObjectMapper mapper, JsonSchemaValidator validator,
                        RestClient.Builder builder) {
        this(properties, mapper, validator, builder.clone()
                .baseUrl(properties.openai().baseUrl())
                .requestFactory(timeouts(properties.openai().timeoutSeconds()))
                .build());
    }

    /** 테스트용: 미리 구성한 RestClient 주입 */
    OpenAiClient(FGuardProperties properties, ObjectMapper mapper, JsonSchemaValidator validator, RestClient restClient) {
        this.props = properties.openai();
        this.mapper = mapper;
        this.validator = validator;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory timeouts(int readSeconds) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(10));
        rf.setReadTimeout(Duration.ofSeconds(Math.max(10, readSeconds)));
        return rf;
    }

    public boolean isConfigured() {
        return props.configured();
    }

    public Result structured(boolean vision, String schemaName, String instructions, String userText,
                             List<Attachment> attachments) {
        if (!isConfigured()) {
            throw new OpenAiException("OpenAI API 키 또는 모델이 설정되지 않았습니다.");
        }
        String model = vision ? props.visionModel() : props.textModel();
        ObjectNode body = buildRequest(model, schemaName, instructions, userText, attachments);

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/v1/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String apiMessage = extractErrorMessage(e.getResponseBodyAsString());
            log.warn("OpenAI API 오류 status={} message={}", e.getStatusCode().value(), apiMessage);
            throw new OpenAiException("OpenAI API 오류(" + e.getStatusCode().value() + "): " + apiMessage, e);
        } catch (RestClientException e) {
            log.warn("OpenAI API 호출 실패: {}", e.getMessage());
            throw new OpenAiException("OpenAI API 에 연결할 수 없습니다: " + e.getMessage(), e);
        }

        String text = extractOutputText(response);
        JsonNode parsed;
        try {
            parsed = mapper.readTree(text);
        } catch (Exception e) {
            throw new OpenAiException("GPT 응답이 JSON 형식이 아닙니다.", e);
        }
        List<String> errors = validator.validate(schemaName, parsed);
        if (!errors.isEmpty()) {
            throw new OpenAiException("GPT 응답이 스키마 검증에 실패했습니다: " + String.join("; ", errors));
        }
        String usedModel = response.path("model").asText(model);
        return new Result(parsed, usedModel);
    }

    ObjectNode buildRequest(String model, String schemaName, String instructions, String userText,
                            List<Attachment> attachments) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", instructions);
        body.put("store", false);

        ArrayNode content = mapper.createArrayNode();
        content.addObject().put("type", "input_text").put("text", userText);
        if (attachments != null) {
            for (Attachment a : attachments) {
                String dataUrl = "data:" + a.contentType() + ";base64," + Base64.getEncoder().encodeToString(a.bytes());
                if ("application/pdf".equals(a.contentType())) {
                    content.addObject().put("type", "input_file").put("filename", a.fileName()).put("file_data", dataUrl);
                } else {
                    content.addObject().put("type", "input_image").put("image_url", dataUrl);
                }
            }
        }
        ArrayNode input = body.putArray("input");
        ObjectNode msg = input.addObject();
        msg.put("role", "user");
        msg.set("content", content);

        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName.replace('-', '_'));
        format.put("strict", true);
        format.set("schema", validator.schemaForOpenAi(schemaName));
        return body;
    }

    String extractOutputText(JsonNode response) {
        if (response == null) throw new OpenAiException("OpenAI 응답이 비어 있습니다.");
        String status = response.path("status").asText("");
        if ("incomplete".equals(status)) {
            throw new OpenAiException("GPT 응답이 중간에 잘렸습니다: " + response.path("incomplete_details").path("reason").asText("unknown"));
        }
        if (response.hasNonNull("error") && response.get("error").isObject()) {
            throw new OpenAiException("OpenAI 오류: " + response.get("error").path("message").asText());
        }
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode c : item.path("content")) {
                String type = c.path("type").asText();
                if ("refusal".equals(type)) {
                    throw new OpenAiException("GPT 가 요청을 거절했습니다: " + c.path("refusal").asText());
                }
                if ("output_text".equals(type)) {
                    return c.path("text").asText();
                }
            }
        }
        throw new OpenAiException("GPT 응답에서 출력 텍스트를 찾을 수 없습니다.");
    }

    private String extractErrorMessage(String body) {
        try {
            return mapper.readTree(body).path("error").path("message").asText("알 수 없는 오류");
        } catch (Exception e) {
            return "알 수 없는 오류";
        }
    }
}
