package com.fguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** classpath:schemas/*.schema.json 을 로드해 GPT 응답을 검증한다. */
@Component
public class JsonSchemaValidator {

    public static final String SYLLABUS = "syllabus-analysis";
    public static final String EXCUSE = "excuse-letter";
    public static final String EXPLANATION = "explanation";

    private final ObjectMapper mapper;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonNode> raw = new ConcurrentHashMap<>();
    private final Map<String, JsonSchema> compiled = new ConcurrentHashMap<>();

    public JsonSchemaValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** OpenAI 구조화 출력(text.format.schema)에 보낼 스키마. $schema 키는 제거한다. */
    public ObjectNode schemaForOpenAi(String name) {
        ObjectNode copy = rawSchema(name).deepCopy();
        copy.remove("$schema");
        return copy;
    }

    public List<String> validate(String name, JsonNode value) {
        JsonSchema schema = compiled.computeIfAbsent(name, n -> factory.getSchema(rawSchema(n)));
        Set<ValidationMessage> errors = schema.validate(value);
        return errors.stream().map(ValidationMessage::getMessage).sorted().toList();
    }

    private JsonNode rawSchema(String name) {
        return raw.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource("schemas/" + n + ".schema.json").getInputStream()) {
                return mapper.readTree(in);
            } catch (IOException e) {
                throw new UncheckedIOException("스키마 로드 실패: " + n, e);
            }
        });
    }
}
