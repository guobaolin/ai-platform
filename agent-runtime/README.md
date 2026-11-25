# agent-runtime（运行面 · Agent 执行引擎）

Python FastAPI "薄执行器"：契约解析 → 调用控制面 inner API → 向 Redis Stream 写事件。
无数据库、无业务规则。契约见 `../contracts/openapi-agent-runtime.yaml`、`../contracts/run-events.md`。

## 启动

```bash
uv sync
uv run uvicorn agent_runtime.main:app --port 8001
```

环境变量（均有默认值）：

| 变量 | 默认 |
|---|---|
| `CONTROL_BASE_URL` | `http://localhost:8080` |
| `RUNTIME_TOKEN` | `platform-runtime-token` |
| `REDIS_URL` | `redis://localhost:6379/0` |

## 端点

- `GET /v1/health`（免鉴权）
- `POST /v1/runs`（202 受理 / 409 runId 重复 / 401）
- `GET /v1/runs/{runId}`（内存状态）
- `POST /v1/runs/{runId}/cancel`

除 health 外均需 `Authorization: Bearer <RUNTIME_TOKEN>`。

## 无外部依赖冒烟

Redis/控制面不可达时服务仍可启动并受理请求：后台任务把 `RUN_ERROR`
尽力写入事件流，发送失败仅记 WARNING 日志，进程不崩溃。

## 测试与检查

```bash
uv run pytest -q
uv run ruff check src tests
```
