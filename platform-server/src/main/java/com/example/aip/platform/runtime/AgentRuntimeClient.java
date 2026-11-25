package com.example.aip.platform.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 按 openapi-agent-runtime.yaml 调用 Python 运行面。 */
@Component
public class AgentRuntimeClient {

    private final RestClient client;

    public AgentRuntimeClient(@org.springframework.beans.factory.annotation.Qualifier("agentRuntimeRestClient") RestClient client) {
        this.client = client;
    }

    /** CreateRunRequest：只传 ID 与用户输入，配置由运行面回查 inner API。 */
    public JsonNode createRun(String runId, String appId, String tenantId, String userId, String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("appId", appId);
        body.put("tenantId", tenantId);
        body.put("userId", userId);
        body.put("query", query);
        try {
            return client.post().uri("/v1/runs").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new RuntimeUnavailableException("agent-runtime POST /v1/runs failed: " + e.getMessage(), e);
        }
    }
}
