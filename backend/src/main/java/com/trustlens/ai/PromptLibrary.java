package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads prompt templates (prompts/NAME.txt) and their JSON output schemas (prompts/NAME.schema.json). */
@Component
public class PromptLibrary {
    private final ObjectMapper mapper;
    private final Map<String, String> texts = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();

    public PromptLibrary(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String system(String name) {
        return texts.computeIfAbsent(name, n -> read("prompts/" + n + ".txt"));
    }

    public JsonNode schema(String name) {
        return schemas.computeIfAbsent(name, n -> {
            try {
                return mapper.readTree(read("prompts/" + n + ".schema.json"));
            } catch (IOException e) {
                throw new IllegalStateException("Bad schema " + n, e);
            }
        });
    }

    private String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing prompt resource " + path, e);
        }
    }
}
