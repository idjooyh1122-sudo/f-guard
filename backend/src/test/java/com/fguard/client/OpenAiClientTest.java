package com.fguard.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fguard.config.FGuardProperties;
import com.fguard.service.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    MockRestServiceServer server;
    OpenAiClient client;

    @BeforeEach
    void setUp() {
        FGuardProperties props = new FGuardProperties("Asia/Seoul", "http://localhost:5173", new FGuardProperties.Upload(1024),
                new FGuardProperties.OpenAi("sk-test", "https://api.openai.test", "vision-model", "text-model", 30),
                new FGuardProperties.Weather(null, "x"), new FGuardProperties.Travel(null, "x"),
                new FGuardProperties.Google(null, null, null));
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiClient(props, mapper, new JsonSchemaValidator(mapper), builder.build());
    }

    static String responseWithText(String text) throws Exception {
        ObjectMapper m = new ObjectMapper();
        return """
                {"id":"resp_1","status":"completed","model":"text-model-2026",
                 "output":[{"type":"reasoning","summary":[]},
                  {"type":"message","role":"assistant","content":[{"type":"output_text","text":%s}]}]}
                """.formatted(m.writeValueAsString(text));
    }

    @Test
    void 구조화출력_요청형식과_응답파싱() throws Exception {
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("text-model"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.text.format.schema['$schema']").doesNotExist())
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
                .andRespond(withSuccess(responseWithText("{\"summary\":\"요약\",\"actions\":[\"행동1\"]}"), MediaType.APPLICATION_JSON));

        OpenAiClient.Result r = client.structured(false, JsonSchemaValidator.EXPLANATION, "inst", "text", List.of());
        assertThat(r.json().path("summary").asText()).isEqualTo("요약");
        assertThat(r.model()).isEqualTo("text-model-2026");
        server.verify();
    }

    @Test
    void PDF는_input_file_이미지는_input_image로_전송() {
        var body = client.buildRequest("vision-model", JsonSchemaValidator.SYLLABUS, "i", "t", List.of(
                new OpenAiClient.Attachment("a.pdf", "application/pdf", "%PDF".getBytes()),
                new OpenAiClient.Attachment("b.png", "image/png", new byte[]{1, 2})));
        var content = body.path("input").path(0).path("content");
        assertThat(content.path(1).path("type").asText()).isEqualTo("input_file");
        assertThat(content.path(1).path("file_data").asText()).startsWith("data:application/pdf;base64,");
        assertThat(content.path(2).path("type").asText()).isEqualTo("input_image");
        assertThat(content.path(2).path("image_url").asText()).startsWith("data:image/png;base64,");
    }

    @Test
    void 스키마에_맞지않는_응답은_거부() throws Exception {
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(responseWithText("{\"summary\":\"요약\"}"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.structured(false, JsonSchemaValidator.EXPLANATION, "i", "t", List.of()))
                .isInstanceOf(OpenAiClient.OpenAiException.class)
                .hasMessageContaining("스키마 검증");
    }

    @Test
    void API_오류는_메시지만_전달하고_키는_노출하지_않음() {
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Incorrect API key provided\"}}"));
        assertThatThrownBy(() -> client.structured(false, JsonSchemaValidator.EXPLANATION, "i", "t", List.of()))
                .isInstanceOf(OpenAiClient.OpenAiException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("sk-test");
    }

    @Test
    void 거절응답_처리() throws Exception {
        String refusal = """
                {"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"불가"}]}]}""";
        server.expect(requestTo("https://api.openai.test/v1/responses"))
                .andRespond(withSuccess(refusal, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.structured(false, JsonSchemaValidator.EXPLANATION, "i", "t", List.of()))
                .hasMessageContaining("거절");
    }
}
