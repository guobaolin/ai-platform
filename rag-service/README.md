# rag-service（运行面 · 索引与检索）

Python FastAPI "薄执行器"：解析/切分/嵌入/写内存向量库/检索，结果回报控制面 inner API。
无数据库、无业务规则。契约见 `../contracts/openapi-rag-service.yaml`、`../contracts/run-events.md`。

## 启动

```bash
uv sync
uv run uvicorn rag_service.main:app --port 8002
```

环境变量（均有默认值）：

| 变量 | 默认 |
|---|---|
| `CONTROL_BASE_URL` | `http://localhost:8080` |
| `RUNTIME_TOKEN` | `platform-runtime-token` |
| `REDIS_URL` | `redis://localhost:6379/0` |

## 端点

- `GET /v1/health`（免鉴权）
- `POST /v1/index-jobs`（202 受理，后台逐文档：拉文件→切分→嵌入→入库→回报控制面）
- `GET /v1/index-jobs/{jobId}`（内存状态 ACCEPTED/RUNNING/DONE/ERROR）
- `POST /v1/retrieval`（同步检索，余弦相似度 topK）

除 health 外均需 `Authorization: Bearer <RUNTIME_TOKEN>`。

## 说明

- 向量库为进程内 `store.py`（原型期），生产替换 Milvus/pgvector，接口不变。
- 嵌入 provider `hash-v1`：字符 2/3-gram blake2b 哈希 → dim 维 → L2 归一化，确定性、无外部依赖。
- 控制面/Redis 不可达时：请求仍可受理，后台任务尽力回报错误并记 WARNING 日志，进程不崩溃。

## 测试与检查

```bash
uv run pytest -q
uv run ruff check src tests
```
