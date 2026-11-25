package com.example.aip.platform.llm;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * LLM 代理的 mock provider：无 Key 联调时由控制面直接生成确定性流式回复。
 * 契约（openapi-control-inner.yaml）：每事件 data 为 {"text":"..."}，最后一条 data 为 [DONE]。
 */
@Service
public class MockLlmStreamService {

    /** 2-4 字符切块，循环使用保证"2-4"区间。 */
    private static final int[] CHUNK_SIZES = {3, 2, 4};

    private final ObjectMapper mapper;
    private final ExecutorService executor;

    public MockLlmStreamService(ObjectMapper mapper, ExecutorService streamExecutor) {
        this.mapper = mapper;
        this.executor = streamExecutor;
    }

    public SseEmitter stream(JsonNode request) {
        String reply = buildReply(request);
        // 30ms/块的节奏不能在 Tomcat 请求线程 sleep，放到虚拟线程
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.submit(() -> {
            try {
                int i = 0;
                int ci = 0;
                while (i < reply.length()) {
                    int size = CHUNK_SIZES[ci++ % CHUNK_SIZES.length];
                    String chunk = reply.substring(i, Math.min(reply.length(), i + size));
                    i += size;
                    emitter.send(SseEmitter.event()
                            .data(mapper.writeValueAsString(Map.of("text", chunk)), org.springframework.http.MediaType.APPLICATION_JSON));
                    Thread.sleep(30);
                }
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                emitter.complete();
            }
        });
        return emitter;
    }

    private String buildReply(JsonNode request) {
        String lastUser = "";
        JsonNode messages = request.path("messages");
        if (messages.isArray()) {
            for (JsonNode m : messages) {
                if ("user".equals(m.path("role").asText())) {
                    lastUser = m.path("content").asText("");
                }
            }
        }
        String snippet = lastUser.length() > 80 ? lastUser.substring(0, 80) : lastUser;
        return "Mock reply to: " + snippet + "。这是一段用于演示流式回传的中文回答。";
    }
}
