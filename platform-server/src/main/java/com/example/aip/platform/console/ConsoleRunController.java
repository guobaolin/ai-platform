package com.example.aip.platform.console;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.example.aip.platform.domain.Run;
import com.example.aip.platform.domain.RunStatus;
import com.example.aip.platform.repo.AiAppRepository;
import com.example.aip.platform.repo.RunRepository;
import com.example.aip.platform.relay.RunEventRelay;
import com.example.aip.platform.runtime.AgentRuntimeClient;
import com.example.aip.platform.runtime.RuntimeUnavailableException;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Console API：原型期无鉴权，面向前端。 */
@RestController
public class ConsoleRunController {

    private final AiAppRepository appRepo;
    private final RunRepository runRepo;
    private final AgentRuntimeClient agentRuntime;
    private final StringRedisTemplate redis;
    private final RunEventRelay relay;
    private final ExecutorService streamExecutor;

    public ConsoleRunController(AiAppRepository appRepo, RunRepository runRepo, AgentRuntimeClient agentRuntime,
                                StringRedisTemplate redis, RunEventRelay relay, ExecutorService streamExecutor) {
        this.appRepo = appRepo;
        this.runRepo = runRepo;
        this.agentRuntime = agentRuntime;
        this.redis = redis;
        this.relay = relay;
        this.streamExecutor = streamExecutor;
    }

    /** 发起一次对话 run：控制面预生成 runId 并落库，再异步提交运行面。 */
    @PostMapping("/api/apps/{appId}/chat")
    public ResponseEntity<?> chat(@PathVariable String appId, @RequestBody ChatRequest req) {
        if (appRepo.findById(appId).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "app not found: " + appId));
        }
        if (req == null || req.query() == null || req.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query is required"));
        }
        String runId = UUID.randomUUID().toString();
        String tenantId = "default-tenant";
        String userId = (req.userId() == null || req.userId().isBlank()) ? "anonymous" : req.userId();

        Run run = new Run();
        run.setId(runId);
        run.setAppId(appId);
        run.setTenantId(tenantId);
        run.setUserId(userId);
        run.setQuery(req.query());
        run.setStatus(RunStatus.RUNNING);
        run.setCreatedAt(Instant.now());
        runRepo.save(run);

        try {
            agentRuntime.createRun(runId, appId, tenantId, userId, req.query());
        } catch (RuntimeUnavailableException e) {
            // 提交失败：run 直接置 FAILED，前端拿到 502，不会悬挂在 RUNNING
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
            return ResponseEntity.status(502).body(Map.of("error", String.valueOf(e.getMessage())));
        }
        return ResponseEntity.ok(Map.of("runId", runId));
    }

    /** SSE 事件流：XREAD 中继循环跑在虚拟线程上，支持 lastEventId 断点重放。 */
    @GetMapping(value = "/api/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String runId,
                             @RequestParam(name = "lastEventId", required = false) String lastEventId) {
        // timeout=-1：由中继循环内部的 5 分钟空闲超时负责收尾
        SseEmitter emitter = new SseEmitter(-1L);
        streamExecutor.submit(() -> relay.pump(runId, lastEventId, emitter));
        return emitter;
    }

    @GetMapping("/api/runs/{runId}")
    public ResponseEntity<?> getRun(@PathVariable String runId) {
        Run run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            return ResponseEntity.status(404).body(Map.of("error", "run not found"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", run.getId());
        out.put("appId", run.getAppId());
        out.put("status", run.getStatus());
        out.put("query", run.getQuery());
        out.put("answer", run.getAnswer());
        out.put("promptTokens", run.getPromptTokens());
        out.put("completionTokens", run.getCompletionTokens());
        out.put("createdAt", run.getCreatedAt());
        out.put("finishedAt", run.getFinishedAt());
        return ResponseEntity.ok(out);
    }

    /** 取消：PUB/sub 命令通道 + 本地状态先行置 CANCELLED（Redis 不可达不影响落库）。 */
    @PostMapping("/api/runs/{runId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String runId) {
        Run run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            return ResponseEntity.status(404).body(Map.of("error", "run not found"));
        }
        try {
            redis.convertAndSend("run:commands:" + runId, "{\"action\":\"cancel\"}");
        } catch (Exception ignored) {
            // 命令通道发布失败仍记录本地取消意图
        }
        if (run.getStatus() == RunStatus.RUNNING) {
            run.setStatus(RunStatus.CANCELLED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
        }
        return ResponseEntity.ok(Map.of("runId", runId, "status", run.getStatus()));
    }
}
