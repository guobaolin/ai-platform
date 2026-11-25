# AI 应用管理平台 · 控制面/运行面拆分垂直切片

Java 17 + Spring Boot 3.x 控制面（`platform-server`）+ Python FastAPI 运行面
（`agent-runtime` Agent 执行引擎、`rag-service` 索引与检索），验证"控制面持有数据、
运行面无状态执行"架构的可行性。完整分析见 [docs/可行性报告.md](docs/可行性报告.md)。

## 跨语言六原则（本骨架的实现依据）

1. 数据所有权归控制面：业务表全在 Java，Python 无库无状态
2. 交接传 ID 不传对象：请求体只带 runId/appId/tenantId + 用户输入
3. 文件传 key 不传字节：原型期 key 约定 `document:{id}`
4. 事件回流走 Redis Stream：`run:events:{runId}`，Java 中继 SSE 并落库
5. 控制命令反向走 pub/sub：`run:commands:{runId}`
6. 凭据不外发：LLM 出口收敛在 Java inner API（provider=mock 可无 Key 联调）

## 目录

```
contracts/          跨语言契约（先冻结后开发，两侧实现不得偏离）
  openapi-agent-runtime.yaml   运行面 Agent API（Python :8001）
  openapi-rag-service.yaml     运行面 RAG API（Python :8002）
  openapi-control-inner.yaml   控制面内部 API（Java :8080，Bearer RUNTIME_TOKEN）
  run-events.md                Redis Stream 事件信封 Schema
platform-server/    Java 控制面：管理面 CRUD、run/索引状态机、事件中继、inner API、mock LLM 代理
agent-runtime/      Python Agent 执行引擎（薄执行器）
rag-service/        Python 索引+检索（hash-v1 嵌入 + 内存向量库，接口与生产可替换）
```

## 本地启动（四步）

```bash
# 0) 依赖：JDK17+、Maven、uv、Docker；若在带 HTTP 代理的受限环境，启动前 unset 代理变量
docker run -d --name aip-redis -p 6379:6379 redis:7-alpine

# 1) Java 控制面 :8080（H2 内存库，自动播种 demo-app / demo-ds）
cd platform-server && mvn -q -DskipTests package && java -jar target/platform-server-0.1.0.jar

# 2) Agent 运行时 :8001
cd agent-runtime && uv sync && uv run uvicorn agent_runtime.main:app --port 8001

# 3) RAG 服务 :8002
cd rag-service && uv sync && uv run uvicorn rag_service.main:app --port 8002
```

## 端到端演示

链路 A（Agent 对话）：

```bash
RUN=$(curl -s -X POST http://localhost:8080/api/apps/demo-app/chat \
  -H 'Content-Type: application/json' -d '{"query":"介绍一下控制面和运行面解耦的好处"}')
RID=$(echo $RUN | python3 -c "import sys,json;print(json.load(sys.stdin)['runId'])")
curl -N "http://localhost:8080/api/runs/$RID/events"        # SSE：RUN_STARTED→LLM_TOKEN×N→USAGE→RUN_FINISHED
curl -s http://localhost:8080/api/runs/$RID                  # DB 终态：FINISHED + tokens 已落库（计费依据）
```

链路 B（知识库索引+检索）：

```bash
curl -s -X POST http://localhost:8080/api/datasets/demo-ds/documents \
  -H 'Content-Type: application/json' -d '{"name":"架构说明","content":"控制面负责配置管理，数据所有权归控制面。运行面是无状态执行器……"}'
curl -s http://localhost:8080/api/datasets/demo-ds/documents/<documentId>/indexing-status   # COMPLETED
curl -s -X POST http://localhost:8080/api/datasets/demo-ds/retrieve \
  -H 'Content-Type: application/json' -d '{"query":"数据所有权归谁","topK":2}'
```

## 已验证（2026-11-25 本机联调）

- 链路 A：17 个 LLM_TOKEN 事件经 Redis Stream 由 Python 回流 Java 并 SSE 中继；USAGE(7/12) 与 answer 落库，状态机 RUNNING→FINISHED。
- 链路 B：文档→rag-service 拉文件/切分/嵌入/入库→回报→Java 状态 COMPLETED→检索命中。
- 容错：运行面不可达时 chat 返回 502 且 Run 置 FAILED（超时兜底扫描另有 @Scheduled 60s）。
- 断线重放：`GET /api/runs/{id}/events?lastEventId=<entryId>` 从断点续传。

## 联调踩坑记录（对真实部署有价值）

1. JDK HttpClient 默认尝试 h2c 升级，uvicorn 降级时丢 body → 出站 RestClient 已强制 `HTTP_1_1`（`platform-server/.../config/AppConfig.java`）。
2. Spring RestClient 对象 body 不自动带 `Content-Type: application/json`，FastAPI 会视为空 body → 已显式设置。
3. 受限环境注入的 `HTTP_PROXY/NO_PROXY` 会干扰 JDK/httpx 对 localhost 的调用（httpx 0.28 解析 `[::1]` 报错）→ 本机启动需 unset；生产内网无此问题。

## 从骨架到生产（对应报告 M2-M4）

- 管理面：接入 Spring Security/OIDC、真实用户/租户/权限表
- LLM：inner API 把 provider=mock 换成真实凭据代理（或下发 run 级短期凭证）
- RAG：hash-v1 嵌入换真实模型；内存向量库换 Milvus/pgvector（接口不变）
- 事件：Redis Stream 加 TTL/XTRIM 策略；USAGE 幂等键 (runId,eventId) 上 DB
- 部署：docker-compose 起全栈 → K8s；契约文件进 CI 做双侧校验
