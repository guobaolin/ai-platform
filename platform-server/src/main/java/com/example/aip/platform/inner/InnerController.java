package com.example.aip.platform.inner;

import java.util.List;
import java.util.Map;

import com.example.aip.platform.domain.AiApp;
import com.example.aip.platform.domain.Document;
import com.example.aip.platform.domain.DocumentStatus;
import com.example.aip.platform.llm.MockLlmStreamService;
import com.example.aip.platform.repo.AiAppRepository;
import com.example.aip.platform.repo.DocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Inner API：仅供 Python 运行面调用（openapi-control-inner.yaml），
 * Bearer 令牌校验由 InnerAuthConfig 的 Filter 完成（仅 /inner/**）。
 */
@RestController
@RequestMapping("/inner")
public class InnerController {

    private final AiAppRepository appRepo;
    private final DocumentRepository docRepo;
    private final MockLlmStreamService mockLlm;

    public InnerController(AiAppRepository appRepo, DocumentRepository docRepo, MockLlmStreamService mockLlm) {
        this.appRepo = appRepo;
        this.docRepo = docRepo;
        this.mockLlm = mockLlm;
    }

    public record ModelConfig(String provider, String model, Double temperature) { }

    public record AppConfigView(String appId, String name, ModelConfig modelConfig,
                                String systemPrompt, List<Object> tools) { }

    @GetMapping("/apps/{appId}/config")
    public ResponseEntity<?> appConfig(@PathVariable String appId) {
        AiApp app = appRepo.findById(appId).orElse(null);
        if (app == null) {
            return ResponseEntity.status(404).body(Map.of("error", "app not found"));
        }
        AppConfigView view = new AppConfigView(
                app.getAppId(),
                app.getName(),
                new ModelConfig(app.getModelProvider(), app.getModel(), app.getTemperature()),
                app.getSystemPrompt(),
                List.of());
        return ResponseEntity.ok(view);
    }

    /** LLM 代理：当前实现 mock provider，SSE 流式返回。 */
    @PostMapping(value = "/llm/invoke", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter llmInvoke(@RequestBody JsonNode request) {
        return mockLlm.stream(request);
    }

    /** 对象存储原型：key 约定 document:{id}，直接返回库内文本。 */
    @GetMapping(value = "/files", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> file(@RequestParam("key") String key) {
        if (key == null || !key.startsWith("document:")) {
            return ResponseEntity.status(404).body(null);
        }
        Document doc = docRepo.findById(key.substring("document:".length())).orElse(null);
        if (doc == null || doc.getContent() == null) {
            return ResponseEntity.status(404).body(null);
        }
        return ResponseEntity.ok(doc.getContent());
    }

    public record IndexResultRequest(String jobId, String status, Integer segmentCount, String error) { }

    /**
     * 索引结果回报，按 jobId 幂等：仅接受与文档当前 jobId 一致的回报（过期/重复回报直接 200 忽略），
     * 且已处于同态时不重复写库。
     */
    @PostMapping("/documents/{documentId}/index-result")
    public ResponseEntity<?> indexResult(@PathVariable String documentId, @RequestBody IndexResultRequest req) {
        Document doc = docRepo.findById(documentId).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(404).body(Map.of("error", "document not found"));
        }
        if (req.jobId() == null || !req.jobId().equals(doc.getJobId())) {
            return ResponseEntity.ok(Map.of("documentId", documentId, "applied", false));
        }
        DocumentStatus target = "completed".equals(req.status()) ? DocumentStatus.COMPLETED
                : "error".equals(req.status()) ? DocumentStatus.ERROR : null;
        // 重复回报（同 jobId、同终态）不再写库，直接返回 applied=false
        if (target == null || doc.getStatus() == target) {
            return ResponseEntity.ok(Map.of("documentId", documentId, "applied", false));
        }
        doc.setStatus(target);
        if (target == DocumentStatus.COMPLETED && req.segmentCount() != null) {
            doc.setSegmentCount(req.segmentCount());
        }
        docRepo.save(doc);
        return ResponseEntity.ok(Map.of("documentId", documentId, "applied", true));
    }
}
