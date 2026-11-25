package com.example.aip.platform.relay;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.example.aip.platform.domain.Run;
import com.example.aip.platform.domain.RunStatus;
import com.example.aip.platform.repo.RunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 事件中继：把 Redis Stream（run:events:{runId}）里的运行面事件转发为 SSE。
 * 关键设计点：
 * 1. XREAD BLOCK 2s 轮询循环在虚拟线程上执行，Redis 不可达时容错退避，不崩溃；
 * 2. 以 (runId, eventId) 内存集合做幂等，SSE 断线按 lastEventId 重放时不会重复落库；
 * 3. USAGE / RUN_FINISHED / RUN_ERROR 在转发同时落库，收到终态事件后主动 complete。
 */
@Service
public class RunEventRelay {

    private static final Logger log = LoggerFactory.getLogger(RunEventRelay.class);
    private static final Duration XREAD_BLOCK = Duration.ofSeconds(2);
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final StringRedisTemplate redis;
    private final RunRepository runRepo;
    private final ObjectMapper mapper;
    private final Map<String, Set<String>> seenEvents = new ConcurrentHashMap<>();

    public RunEventRelay(StringRedisTemplate redis, RunRepository runRepo, ObjectMapper mapper) {
        this.redis = redis;
        this.runRepo = runRepo;
        this.mapper = mapper;
    }

    /** 在独立线程内跑 XREAD 循环，通过 emitter 持续推送。 */
    public void pump(String runId, String lastEventId, SseEmitter emitter) {
        String key = "run:events:" + runId;
        String cursor = (lastEventId == null || lastEventId.isBlank()) ? "0" : lastEventId;
        long lastActivity = System.currentTimeMillis();
        Set<String> seen = seenEvents.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet());
        try {
            // stream 尚不存在（运行面未写入）或 Redis 不可达：先发注释帧保持连接，由循环容错
            if (!streamExists(key)) {
                emitter.send(SseEmitter.event().comment("stream-not-ready"));
            }
            while (true) {
                if (System.currentTimeMillis() - lastActivity > IDLE_TIMEOUT_MS) {
                    emitter.complete();
                    return;
                }
                List<MapRecord<String, Object, Object>> records;
                try {
                    records = redis.opsForStream().read(
                            StreamReadOptions.empty().block(XREAD_BLOCK).count(100),
                            StreamOffset.create(key, ReadOffset.from(cursor)));
                } catch (Exception redisErr) {
                    // Redis 不可用：退避 1s 继续，受总空闲超时约束
                    sleep(1000);
                    continue;
                }
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> rec : records) {
                    String eventId = rec.getId().getValue();
                    cursor = eventId;
                    lastActivity = System.currentTimeMillis();
                    // (runId, eventId) 幂等：重放场景下重复条目只发库不重复处理
                    boolean fresh = seen.add(runId + ":" + eventId);
                    Object payloadObj = rec.getValue().get("payload");
                    String payload = payloadObj == null ? null : String.valueOf(payloadObj);
                    if (payload == null) {
                        continue;
                    }
                    String type = envelopeType(payload);
                    emitter.send(SseEmitter.event().name(type == null ? "MESSAGE" : type).data(payload).id(eventId));
                    if (fresh) {
                        applySideEffect(runId, payload, type);
                    }
                    if ("RUN_FINISHED".equals(type) || "RUN_ERROR".equals(type)) {
                        emitter.complete();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // 客户端断开等：静默收敛
            log.debug("relay end for run {}: {}", runId, e.toString());
            emitter.complete();
        }
    }

    private boolean streamExists(String key) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    private String envelopeType(String payload) {
        try {
            return mapper.readTree(payload).path("type").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 消费副作用：落库终态与用量，全部按事件类型幂等（同一 eventId 只处理一次）。 */
    private void applySideEffect(String runId, String payload, String type) {
        if (type == null) {
            return;
        }
        try {
            JsonNode env = mapper.readTree(payload);
            JsonNode data = env.path("data");
            switch (type) {
                case "USAGE" -> runRepo.findById(runId).ifPresent(run -> {
                    if (data.hasNonNull("promptTokens")) run.setPromptTokens(data.get("promptTokens").asInt());
                    if (data.hasNonNull("completionTokens")) run.setCompletionTokens(data.get("completionTokens").asInt());
                    runRepo.save(run);
                });
                case "RUN_FINISHED" -> updateTerminal(runId, RunStatus.FINISHED, data.path("answer").asText(null));
                case "RUN_ERROR" -> updateTerminal(runId, RunStatus.ERROR, null);
                default -> { }
            }
        } catch (Exception e) {
            log.warn("side effect failed for run {} type {}: {}", runId, type, e.toString());
        }
    }

    private void updateTerminal(String runId, RunStatus status, String answer) {
        Run run = runRepo.findById(runId).orElse(null);
        if (run == null || run.getStatus() == status) {
            return;
        }
        // 已被取消/超时兜底改写的 run 不复活，仅记录
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.FAILED) {
            run.setStatus(status);
            if (answer != null) {
                run.setAnswer(answer);
            }
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
