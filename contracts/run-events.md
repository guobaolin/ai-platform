# Run 事件契约（Redis Stream）

运行面（agent-runtime / rag-service）向 Redis Stream 写入事件，Java 控制面消费。

- Stream key: `run:events:{runId}`
- 写入方式: `XADD run:events:{runId} * payload <JSON字符串>`（payload 为单个字段，值是下面信封的 JSON）
- 消费方式: Java 侧 `XREAD BLOCK` 新事件；断线重连按 Last-Event-ID（Stream entry id）重放
- 保留: 消费完成后 Java 将终态落库；Stream 设置 `XTRIM ~ maxlen 1000` 或 TTL 24h

## 事件信封

```json
{
  "eventId": "<stream entry id，由发布方生成也可由消费方回填，用于幂等>",
  "runId": "uuid",
  "type": "RUN_STARTED | LLM_TOKEN | TOOL_CALL_STARTED | TOOL_CALL_FINISHED | USAGE | RUN_FINISHED | RUN_ERROR | INDEX_PROGRESS",
  "ts": 1730000000000,
  "data": { }
}
```

## 各类型 data 载荷

| type | data |
|---|---|
| RUN_STARTED | `{"appId":"...","tenantId":"..."}` |
| LLM_TOKEN | `{"text":"增量token"}` |
| TOOL_CALL_STARTED | `{"callId":"...","tool":"...","input":{}}` |
| TOOL_CALL_FINISHED | `{"callId":"...","output":"..."}` |
| USAGE | `{"model":"...","promptTokens":123,"completionTokens":45}` |
| RUN_FINISHED | `{"answer":"完整回答"}` |
| RUN_ERROR | `{"code":"...","message":"..."}` |
| INDEX_PROGRESS | `{"datasetId":"...","documentId":"...","stage":"parsing\|splitting\|indexing\|completed\|error","segments":N}` |

## 幂等与顺序

- 同一 run 内事件严格按 Stream 顺序消费；eventId 单调（entry id 天然有序）。
- Java 落库 USAGE 时以 `(runId, eventId)` 为幂等键。
- 控制命令（取消）：Java `PUBLISH run:commands:{runId} {"action":"cancel"}`，运行面订阅 pub/sub 后在下一个循环点中断。
