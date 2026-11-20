package com.trustlens.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trustlens.config.AppProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP client for the internal Playwright browser worker. */
@Component
public class WorkerClient {
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    public WorkerClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public JsonNode createSession(String startUrl, String folder, String label) {
        ObjectNode b = mapper.createObjectNode().put("startUrl", startUrl).put("folder", folder).put("label", label);
        return call("POST", "/sessions", b, 90);
    }

    public JsonNode action(String sessionId, ObjectNode action) {
        return call("POST", "/sessions/" + sessionId + "/actions", action, 90);
    }

    public void close(String sessionId) {
        try {
            call("DELETE", "/sessions/" + sessionId, null, 15);
        } catch (Exception ignored) {
            // session may already be gone
        }
    }

    public boolean healthy() {
        try {
            return "ok".equals(call("GET", "/health", null, 5).path("status").asText());
        } catch (Exception e) {
            return false;
        }
    }

    public ObjectNode newAction(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private JsonNode call(String method, String path, JsonNode body, int timeoutSec) {
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(props.workerUrl() + path))
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .header("content-type", "application/json");
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
            rb.method(method, pub);
            HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = resp.body() == null || resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
            if (resp.statusCode() >= 400) {
                throw new WorkerException(json.path("error").asText("Browser worker error HTTP " + resp.statusCode()));
            }
            return json;
        } catch (WorkerException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkerException("Interrupted");
        } catch (Exception e) {
            throw new WorkerException("Browser worker unreachable: " + e.getMessage());
        }
    }

    public static class WorkerException extends RuntimeException {
        public WorkerException(String m) {
            super(m);
        }
    }
}
