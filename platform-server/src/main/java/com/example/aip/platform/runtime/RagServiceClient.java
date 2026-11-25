package com.example.aip.platform.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 按 openapi-rag-service.yaml 调用 RAG 运行面。 */
@Component
public class RagServiceClient {

    private final RestClient client;

    public RagServiceClient(@org.springframework.beans.factory.annotation.Qualifier("ragServiceRestClient") RestClient client) {
        this.client = client;
    }

    /** CreateIndexJobRequest：embeddingProfile 固定 hash-v1/64，chunk 500/50。 */
    public void submitIndexJob(String jobId, String datasetId, String documentId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("provider", "hash-v1");
        profile.put("dim", 64);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("datasetId", datasetId);
        body.put("documentIds", List.of(documentId));
        body.put("embeddingProfile", profile);
        body.put("chunkSize", 500);
        body.put("chunkOverlap", 50);
        try {
            client.post().uri("/v1/index-jobs").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new RuntimeUnavailableException("rag-service POST /v1/index-jobs failed: " + e.getMessage(), e);
        }
    }

    public JsonNode retrieve(String datasetId, String query, Integer topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetId", datasetId);
        body.put("query", query);
        if (topK != null) {
            body.put("topK", topK);
        }
        try {
            return client.post().uri("/v1/retrieval").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new RuntimeUnavailableException("rag-service POST /v1/retrieval failed: " + e.getMessage(), e);
        }
    }
}
