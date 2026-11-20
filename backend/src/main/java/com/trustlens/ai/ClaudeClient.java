package com.trustlens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trustlens.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal Anthropic Messages API client. Every call forces a single tool call so the model
 * returns JSON that matches a declared JSON schema (structured output).
 */
@Component
public class ClaudeClient {
    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public ClaudeClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return props.claudeConfigured();
    }

    /** Builds a user-message content array. */
    public ArrayNode content() {
        return mapper.createArrayNode();
    }

    public void addText(ArrayNode content, String text) {
        content.addObject().put("type", "text").put("text", text);
    }

    public void addImage(ArrayNode content, String mediaType, String base64) {
        ObjectNode img = content.addObject();
        img.put("type", "image");
        img.putObject("source").put("type", "base64").put("media_type", mediaType).put("data", base64);
    }

    public JsonNode callTool(String system, ArrayNode userContent, String toolName, String toolDescription,
                             JsonNode inputSchema, int maxTokens) {
        if (!isConfigured()) throw new ClaudeException("ANTHROPIC_API_KEY is not configured");
        ObjectNode req = mapper.createObjectNode();
        req.put("model", props.anthropicModel());
        req.put("max_tokens", maxTokens);
        req.put("system", system);
        ArrayNode msgs = req.putArray("messages");
        ObjectNode user = msgs.addObject();
        user.put("role", "user");
        user.set("content", userContent);
        ObjectNode tool = req.putArray("tools").addObject();
        tool.put("name", toolName);
        tool.put("description", toolDescription);
        tool.set("input_schema", inputSchema);
        req.putObject("tool_choice").put("type", "tool").put("name", toolName);

        String body;
        try {
            body = mapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new ClaudeException("Could not serialize request: " + e.getMessage());
        }
        ClaudeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest httpReq = HttpRequest.newBuilder(URI.create(props.anthropicBaseUrl() + "/v1/messages"))
                        .timeout(Duration.ofSeconds(300))
                        .header("content-type", "application/json")
                        .header("x-api-key", props.anthropicApiKey())
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 429 || code == 529 || code >= 500) {
                    last = new ClaudeException("Claude API temporarily unavailable (HTTP " + code + ")");
                    Thread.sleep(2000L * attempt);
                    continue;
                }
                if (code != 200) {
                    throw new ClaudeException("Claude API error HTTP " + code + ": " + truncate(resp.body(), 400));
                }
                JsonNode root = mapper.readTree(resp.body());
                for (JsonNode block : root.path("content")) {
                    if ("tool_use".equals(block.path("type").asText()) && toolName.equals(block.path("name").asText())) {
                        return block.path("input");
                    }
                }
                throw new ClaudeException("Claude response did not contain the expected structured output");
            } catch (ClaudeException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ClaudeException("Interrupted while calling Claude");
            } catch (Exception e) {
                log.warn("Claude call attempt {} failed: {}", attempt, e.toString());
                last = new ClaudeException("Claude call failed: " + e.getMessage());
            }
        }
        throw last != null ? last : new ClaudeException("Claude call failed");
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "…");
    }

    public static class ClaudeException extends RuntimeException {
        public ClaudeException(String m) {
            super(m);
        }
    }
}
