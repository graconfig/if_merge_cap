# IFmerge CAP — 设计书

> 版本：v1.0
> 工程路径：`/data/HuangCX/ifmerge-cap/`

---

## 1. 背景与目标

公司有数百份历史 EBS 接口设计书需要迁移到 SAP，存在三个痛点：
- **规模大**：上万行字段，人工逐份阅读不现实
- **重复多**：不同部门写的接口字段大量重叠，但人工对比效率极低
- **格式杂**：Sheet 命名、列布局不一，难以直接复用

本工程是基于 **SAP CAP for Java** 的后端服务，负责：
1. 接收清洗后的字段数据（JSON）
2. 通过 LLM 完成接口结构识别、字段抽取
3. 通过算法 + LLM 计算字段相似度、识别可合并组、生成新接口名
4. 以 JSON 返回解析与合并结果

业务目标：把"几百份杂乱设计书"转换为"合并优化后的标准接口清单"。

---

## 2. 决策汇总

| # | 决策项 | 选择 |
|---|---|---|
| 1 | 部署目标 | BTP Cloud Foundry |
| 2 | 框架 | SAP CAP for Java 3.0 + Spring Boot 3.2 + JDK 17 |
| 3 | API 协议 | 纯 JSON in / JSON out |
| 4 | 数据持久化 | 不绑定 HANA，纯 JVM 内存 |
| 5 | 项目结构 | Hexagonal 四模块：db / domain / application / infrastructure |
| 6 | 并发模型 | 单实例 + Spring `@Async` |
| 7 | 业务 Service 数 | 2 个（Analysis + Merge） |
| 8 | 工程间串联 | Merge 接收 records[] 直接处理（无状态） |
| 9 | LLM 接入 | SAP AI Core（production）/ Mock（开发）通过开关切换 |
| 10 | 鉴权 | XSUAA OAuth2 client_credentials |
| 11 | HTTP 入口 | Spring REST Controller（`/api/v1/...`） |
| 12 | 提示词管理 | `prompts.yaml` 外部化模板 |
| 13 | 重试策略 | Resilience4j 指数退避 1s / 2s |
| 14 | Job TTL | 15 分钟 |

---

## 3. 架构总览

### 3.1 部署拓扑

```
   [Client] ──HTTPS+JWT──► [CAP Java App] ──OAuth2──► [SAP AI Core]
                          instances:1, memory:1G
```

### 3.2 Maven 模块依赖

```
   infrastructure ──► application ──► domain
                                       ↑ enforcer 锁死框架依赖
   db (CDS namespace)
```

| 模块 | 允许依赖 |
|---|---|
| `domain` | JDK 17 + slf4j-api |
| `application` | domain + spring-context |
| `infrastructure` | application + Spring Boot + CAP + WebClient + 全部 |
| `db` | — |

### 3.3 BTP 服务绑定

| 服务 | 用途 |
|---|---|
| `xsuaa` | OAuth2 + JWT 校验 |
| `destination` | SAP AI Core 凭证管理 |
| `application-logs` | 日志聚合 |

---

## 4. 项目目录结构

```
ifmerge-cap/
├── pom.xml                                    parent
├── db/
│   ├── pom.xml
│   └── src/main/resources/schema.cds
├── domain/
│   ├── pom.xml                                含 enforcer
│   └── src/main/java/.../domain/
│       ├── analysis/{model,service,port}/
│       ├── merge/{model,service,port}/
│       └── shared/exception/
├── application/
│   ├── pom.xml
│   └── src/main/java/.../application/
│       ├── analysis/   (Command + UseCase)
│       ├── merge/      (Command + UseCase)
│       └── job/        (Job + JobStore + JobManager + Status/Type)
└── infrastructure/
    ├── pom.xml
    └── src/main/
        ├── java/.../infrastructure/
        │   ├── IfmergeApplication.java
        │   ├── adapter/
        │   │   ├── inbound/       (Controller + dto + mapper)
        │   │   └── outbound/      (ai + storage)
        │   └── config/            (AsyncConfig + SecurityConfig + JobCleanupScheduler)
        └── resources/
            ├── application.yaml
            ├── prompts.yaml
            ├── logback-spring.xml
            └── cds/               (analysis-service.cds + merge-service.cds)
```

总计：57 个 Java 文件、5 个 pom.xml、3 个 CDS、3 个 yaml/xml。

---

## 5. 数据模型（CDS Schema — 实际状态）

### 5.1 CDS Schema

`db/src/main/resources/schema.cds`：

```cds
namespace handjapan.ifmerge;
```

**仅声明 namespace，不定义任何实体**。整套系统业务数据不落库，全部活在 JVM 内存或请求体中。`db/` 模块保留作为将来扩展点（审计日志、Job 历史等）。

### 5.2 内存数据模型（Java records）

业务数据用 Java record 表示，分布在 `domain/` 模块：

#### 解析侧（analysis）

| record | 字段 |
|---|---|
| `CleanedSheet` | `name, headers, rows` |
| `InterfaceRecord` | `no, documentNumber, ifName, ebsTableName, ebsTableId, itemId, itemName, digitCount, itemDescription, dataType, digitDecimal, devType, isKey, required, remarks` |
| `AnalysisResult` | `documentNumber, ifName, dataSheets, records` |
| `AnalysisResult.DataSheetInfo` | `sheetName, dataStartRow, recordCount` |

#### 合并侧（merge）

| record | 字段 |
|---|---|
| `FieldPair` | `tableId, itemId`（含空白校验） |
| `IFInfo` | `ifName, docNumber, fieldPairs, representativeItem` |
| `SimilarityPair` | `if1Name, if2Name, similarity` |
| `SimilarityMode` | enum `{ MAX, AVG }` |
| `MergeGroup` | `groupingId, module, scenario, memberIfNames, mergedIfName, groupingReason, mergedFields` |
| `MergeResult` | `summary, groups, similarityMatrices` |
| `MergeResult.ScenarioMatrix` | `scenario, axis, maxSimilarity[][], directionalSimilarity[][]` |
| `MergeResult.DirectionalValue` | `rowToCol, colToRow` |

#### Job 模型（application 层）

| record | 字段 |
|---|---|
| `Job` | `id, type, status, progress, phase, createdAt, startedAt, completedAt, result, error` |
| `Job.ErrorInfo` | `code, message, occurredAt` |
| `JobStatus` | enum `{ PENDING, RUNNING, SUCCEEDED, FAILED }` |
| `JobType` | enum `{ ANALYSIS, MERGE }` |

---

## 6. Service 定义

### 6.1 CDS Service

`infrastructure/src/main/resources/cds/analysis-service.cds`：

```cds
service AnalysisService @(path: '/analysis', requires: 'ifmerge.api') {
    action analyze(fileName: String, sheets: array of CleanedSheetDto, options: AnalyzeOptions) returns Job;
    function getJob(id: UUID) returns Job;
    function getResult(id: UUID) returns AnalysisResult;
}
```

`merge-service.cds` 类似。完整 CDS schema 见对应文件。

### 6.2 REST 暴露的端点

| 方法 | 路径 | 入参 | 出参 | 状态码 |
|---|---|---|---|---|
| POST | `/api/v1/analyze` | `AnalyzeRequestDto` | `JobDto` | 201 / 400 |
| GET | `/api/v1/analyses/{id}` | — | `JobDto` | 200 / 404 |
| GET | `/api/v1/analyses/{id}/result` | — | `AnalysisResultDto` | 200 / 404 / 409 |
| POST | `/api/v1/merge` | `MergeRequestDto` | `JobDto` | 201 / 400 |
| GET | `/api/v1/merges/{id}` | — | `JobDto` | 200 / 404 |
| GET | `/api/v1/merges/{id}/result` | — | `MergeResultDto` | 200 / 404 / 409 |
| GET | `/actuator/health` | — | `{status:UP}` | 200 |
| GET | `/swagger-ui.html` | — | Swagger UI | 200 |

### 6.3 AnalyzeRequest（关键 JSON）

```json
{
  "fileName": "BDN-EPD-OF-093.xlsx",
  "sheets": [
    {
      "name": "エクスポート項目",
      "headers": ["No","EBSテーブル名","EBSテーブルID","項目ID","項目名","桁数"],
      "rows": [["1","受注ヘッダ","OE_ORDER_HEADERS_ALL","ORDER_NUMBER","注文番号","22"]]
    }
  ],
  "options": { "phase1HeadRows": 30, "maxChunkRows": 100 }
}
```

### 6.4 MergeRequest

```json
{
  "records": [
    {"no":1, "documentNumber":"BDN-EPD-OF-093", "ifName":"受注ヘッダ連携",
     "ebsTableId":"OE_ORDER_HEADERS_ALL", "itemId":"ORDER_NUMBER", ...}
  ],
  "options": { "threshold": 0.80, "mode": "max" }
}
```

### 6.5 Job（即时响应 + 轮询）

```json
{
  "id": "uuid",
  "type": "ANALYSIS",
  "status": "PENDING|RUNNING|SUCCEEDED|FAILED",
  "progress": 0,
  "phase": "PHASE1_RUNNING",
  "createdAt": "...", "startedAt": null, "completedAt": null,
  "error": null
}
```

---

## 7. 解析和匹配流程

### 7.1 解析流程（Analysis）

```
POST /api/v1/analyze (fileName, sheets, options)
   │
   ▼
AnalysisController → AnalysisMapper.toCommand() → AnalyzeDocumentUseCase.submit()
   │
   ▼
JobManager.create(ANALYSIS) → Job(PENDING) ────────────► 立即返回 jobId 给客户端
   │
   │ @Async("jobExecutor")
   ▼
runAsync():
   markRunning("PHASE1_RUNNING")
   ↓
   ─── Phase 1：構造識別 ────────────────────────────
   Phase1PromptBuilder.build(fileName, sheets, headRows)
      ├─ formatSheetHead(sheets, 30)         ← 每个 sheet 取前 30 行 → "[列号]值" 文本
      └─ PromptRepository.render("phase1", vars)
   ↓
   aiGateway.analyzePhase1() → Phase1Result(docNumber, ifName, dataSheets[])
   ↓
   updateProgress(20, "PHASE1_COMPLETED")
   ↓
   ─── Phase 2：字段抽取（按 sheet × chunk 循环）─────
   for each dataSheet:
      取 data_start_row 以降 → splitIntoChunks(rows, maxChunkRows=100)
      for each chunk:
         aiGateway.analyzePhase2(chunk, columnMapping) → InterfaceRecord[]
         allRecords.addAll(records)
         updateProgress(20 + 75·i/N, "PHASE2_CHUNK_i_OF_N")
   ↓
   updateProgress(95, "BUILDING_RESULT") → 連番 no を振り直す
   ↓
   markSucceeded(AnalysisResult)
```

### 7.2 合并流程（Merge）

```
POST /api/v1/merge (records, options)
   │
   ▼
MergeController → MergeMapper.toCommand() → MergeInterfacesUseCase.submit()
   │
   ▼
JobManager.create(MERGE) → Job(PENDING) ─────────────► 立即返回 jobId
   │
   │ @Async
   ▼
runAsync():
   markRunning("AGGREGATING")
   ↓
   ─── 1. records → IFInfo 集約 ────────────────────
   IFAggregator.aggregate(records) → Map<ifName, IFInfo>
      ├─ groupby ifName
      └─ 抽出 (EBSテーブルID, 項目ID) → FieldPair set
   ↓
   updateProgress(10, "CLASSIFYING")
   ↓
   ─── 2. AI 分類（モジュール × シナリオ）─────────────
   classificationGateway.classify(ifInfos) → Map<category, CategoryInfo>
   ↓
   updateProgress(30, "GENERATING_IF_INFO")
   ↓
   ─── 3. AI 概要 + 代表項目 ──────────────────────
   namingGateway.generateAllIfInfo(ifInfos) → Map<ifName, IFSummary>
   ↓
   updateProgress(50, "SIMILARITY")
   ↓
   ─── 4. module × scenario 毎に分組 ─────────────
   for each module / scenario:
      pairs   = similarityCalculator.buildMatrix(threshold, mode)   ← max or avg
      groups  = grouper.groupSimilarIFs(pairs)                       ← UnionFind
      for each group:
         groupingId   = allocator.next(module)                       ← FI001, FI002, ...
         mergedName   = namingGateway.generateMergedIfName(memberNames, ifInfos)
         mergedFields = deduplicator.dedupe(memberRecords)           ← (tableId, itemId) 去重
         reason       = reasonBuilder.build(...)                     ← 直接相似 or 推移性
         allGroups.add(MergeGroup(...))
      matrix = buildScenarioMatrix(...)                              ← max + directional
   ↓
   updateProgress(95, "BUILDING_RESULT")
   ↓
   markSucceeded(MergeResult{summary, groups, similarityMatrices})
```

### 7.3 进度上报粒度

| 阶段 | progress | phase |
|---|---|---|
| 提交 | 0 | `PENDING` |
| Phase1 中 | 10 | `PHASE1_RUNNING` |
| Phase1 完成 | 20 | `PHASE1_COMPLETED` |
| Phase2 chunk i/N | 20 + 75·i/N | `PHASE2_CHUNK_i_OF_N` |
| 结果组装 | 95 | `BUILDING_RESULT` |
| 完成 | 100 | `COMPLETED` |

合并侧类似：`AGGREGATING → CLASSIFYING → GENERATING_IF_INFO → SIMILARITY → MERGING_{module}_{scenario} → BUILDING_RESULT`。

---

## 8. 配置管理

### 8.1 `application.yaml` 关键项

```yaml
spring.application.name: ifmerge-cap
server.port: 8080

ifmerge:
  ai.mock: true                        # MockSapAiCoreClient 切换
  security.permitAll: true             # dev 模式跳过 JWT
  analysis:
    phase1HeadRows: 30
    maxChunkRows: 100
    timeout: 5m
  merge:
    defaultThreshold: 0.80
    defaultMode: max
  job:
    ttl: 15m
    cleanupInterval: 600000
    maxConcurrent: 10
  prompts.path: classpath:prompts.yaml

resilience4j.retry.instances.sap-ai-core:
  max-attempts: 3
  wait-duration: 1s
  exponential-backoff-multiplier: 2

resilience4j.circuitbreaker.instances.sap-ai-core:
  failure-rate-threshold: 50
  wait-duration-in-open-state: 30s

springdoc:
  swagger-ui.path: /swagger-ui.html
  api-docs.path: /v3/api-docs
```

### 8.2 双模式切换矩阵

| 场景 | `ai.mock` | `security.permitAll` |
|---|---|---|
| 本地开发 / CI | true | true |
| BTP CF dev space | false | true |
| BTP CF qa / production | false | false |

### 8.3 `prompts.yaml`

5 个模板键，沿用原 Python 工程：

| key | 占位符 | 调用阶段 |
|---|---|---|
| `phase1` | `{file_name}`, `{sheet_head_text}` | 解析 Phase 1 |
| `phase2` | `{file_name}`, `{doc_number}`, `{if_name}`, `{chunk_text}`, `{col_*}` | 解析 Phase 2 |
| `classify_interfaces` | `{count}`, `{if_info_block}` | 合并：分类 |
| `generate_all_if_info` | `{count}`, `{if_info_block}` | 合并：概要 + 代表项 |
| `generate_merged_if_name` | `{if_info_block}` | 合并：新接口名 |

模板加载：`YamlPromptRepository` 启动时读取，占位符通过 `String.replace` 展开。

---

## 9. 错误处理

### 9.1 HTTP 状态码映射

| 情境 | 状态码 | 响应 |
|---|---|---|
| 入力 fileName / sheets / records 为空 | 400 | `BadRequest` |
| JWT 缺失（prod） | 401 | Spring Security 默认 |
| Scope 不足（prod） | 403 | Spring Security 默认 |
| Job 不存在 / TTL 过期 | 404 | `ErrorResponseDto("JOB_NOT_FOUND")` |
| Job 未完成时取结果 | 409 | `ErrorResponseDto("JOB_NOT_COMPLETED")` |
| UseCase 抛异常 | 200 + Job FAILED | Job.error 含 code + message |
| 服务器异常 | 500 | Spring 默认 |

### 9.2 错误响应格式

```json
{
  "code": "JOB_NOT_FOUND",
  "message": "Analysis job abc-123 not found or expired",
  "occurredAt": "2026-06-02T10:05:00Z"
}
```

### 9.3 内部异常分级

| 异常 | 来源 | 处理 |
|---|---|---|
| `AnalysisException` | domain | UseCase catch → markFailed |
| `MergeException` | domain | 同上 |
| `IllegalArgumentException` | DTO 校验 | Controller 400 |
| `RuntimeException` | AI 调用 | Resilience4j retry → 全失败 markFailed |
| `JobStore.findById empty` | Mapper | Controller 404 |

### 9.4 重试策略

- 装饰对象：`SapAiCoreClient` 的 LLM 调用方法
- 实例名：`sap-ai-core`
- 最多 3 次尝试，等待：1s、2s（指数退避，与原 Python `wait=2^attempt` 一致）
- 全部失败抛出，UseCase 标 Job FAILED

---

## 10. 日志与 token 统计

### 10.1 日志配置

`logback-spring.xml` 关键项：

```xml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{40} - %msg%n</pattern>
    </encoder>
</appender>

<root level="INFO">
    <appender-ref ref="STDOUT" />
</root>

<logger name="com.handjapan.ifmerge" level="INFO" />
<logger name="com.sap.cds" level="WARN" />
<logger name="org.springframework" level="WARN" />
```

### 10.2 日志级别约定

| 包 | 级别 |
|---|---|
| `com.handjapan.ifmerge.adapter.inbound.*` | INFO（每次 HTTP 请求记一行） |
| `com.handjapan.ifmerge.application.*` | INFO（Job 状态变更、阶段切换） |
| `com.handjapan.ifmerge.domain.*` | DEBUG（算法细节，默认不输出） |
| `com.handjapan.ifmerge.infrastructure.adapter.outbound.ai.*` | INFO（每次 LLM 调用） |
| `com.sap.cds` / `org.springframework` | WARN |

### 10.3 关键日志埋点

| 位置 | 日志内容 |
|---|---|
| `AnalysisController.analyze` | `POST /api/v1/analyze: fileName=..., sheets=N` |
| `AnalyzeDocumentUseCase.runAsync` | `Job {id}: Phase1 開始`, `Phase1 完了`, `Phase2 chunk i/N` |
| `MergeInterfacesUseCase.runAsync` | `Job {id}: 集約後 N IFs`, `分類完了`, `合并完了 N groups` |
| `SapAiCoreClient.analyzePhase1` | `Phase1 開始: fileName=, prompt length=` |
| `JobCleanupScheduler.cleanup` | `evicted N expired jobs` |

### 10.4 Token 统计

每次 LLM 调用记录：
- `llmCalls`：调用次数
- `llmTokensIn`：输入 token 总数（从 SAP AI Core 响应 `usage.inputTokens` 抽取）
- `llmTokensOut`：输出 token 总数
- `totalDurationMs`：累计耗时

统计在 `SapAiCoreClient` 内部按 Job 维度累加，通过 `AnalysisResult.statistics` / `MergeResult.statistics` 返回客户端。

集中日志中也以 `[TOKEN] jobId=... call=phase1 in=8450 out=2200 ms=18000` 格式记录，便于离线分析。

### 10.5 BTP 日志聚合

production 部署时通过 `application-logs` 服务绑定，自动收集 STDOUT 到 BTP Application Logging。Kibana 上按 `correlation_id` / `job_id` 索引检索。

---

## 11. 环境变量

### 11.1 应用配置覆盖

| 变量 | 默认值 | 说明 |
|---|---|---|
| `IFMERGE_AI_MOCK` | true | `false` 切到真 SAP AI Core |
| `IFMERGE_SECURITY_PERMITALL` | true | `false` 启用 XSUAA JWT 校验 |
| `IFMERGE_ANALYSIS_PHASE1HEADROWS` | 30 | Phase 1 每个 sheet 头部行数 |
| `IFMERGE_ANALYSIS_MAXCHUNKROWS` | 100 | Phase 2 单 chunk 最大行数 |
| `IFMERGE_MERGE_DEFAULTTHRESHOLD` | 0.80 | 默认相似度阈值 |
| `IFMERGE_MERGE_DEFAULTMODE` | max | 默认相似度模式 |
| `IFMERGE_JOB_TTL` | 15m | Job 内存保留时间 |
| `IFMERGE_JOB_MAXCONCURRENT` | 10 | 同时在跑 Job 上限 |
| `SERVER_PORT` | 8080 | HTTP 端口 |
| `LOGGING_LEVEL_COM_HANDJAPAN_IFMERGE` | INFO | 应用日志级别 |

### 11.2 BTP CF 注入

| 变量 | 内容 | 由谁注入 |
|---|---|---|
| `VCAP_SERVICES` | 含 xsuaa / destination / application-logs 凭证 | BTP CF 自动 |
| `VCAP_APPLICATION` | 应用 metadata（org、space、URL） | BTP CF 自动 |

### 11.3 JVM 调优

| 变量 | 推荐值 |
|---|---|
| `JBP_CONFIG_OPEN_JDK_JRE` | `[ jre: { version: 17.+ } ]` |
| `JBP_CONFIG_JAVA_OPTS` | `[ java_opts: "-Xss512k -XX:MaxRAMPercentage=75" ]` |

---

## 12. 测试方法

### 12.1 单元测试

| 测试文件 | 用例数 | 覆盖范围 |
|---|---|---|
| `SimilarityCalculatorTest` | 5 | max/avg 双模式、subset、disjoint、empty 边界 |
| `UnionFindTest` | 4 | 初始独立组、union 合并、传递性、未知元素异常 |
| `Phase1PromptBuilderTest` | 9 | `formatSheetHead` 各种边界 + 模板渲染 |

执行：
```bash
mvn -pl domain test
```

### 12.2 端到端 curl 测试（Mock 模式）

```bash
# ① 启动
cd /data/HuangCX/ifmerge-cap
mvn -pl infrastructure -am spring-boot:run

# ② 提交解析
curl -X POST http://localhost:8080/api/v1/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "BDN-EPD-OF-093.xlsx",
    "sheets": [{
      "name": "エクスポート項目",
      "headers": ["No","EBSテーブル名","EBSテーブルID","項目ID","項目名","桁数"],
      "rows": [["1","受注ヘッダ","OE_ORDER_HEADERS_ALL","ORDER_NUMBER","注文番号","22"]]
    }],
    "options": { "phase1HeadRows": 30, "maxChunkRows": 100 }
  }'
# → { "id": "abc-123", "status": "PENDING", ... }

# ③ 轮询
curl http://localhost:8080/api/v1/analyses/abc-123

# ④ 取结果
curl http://localhost:8080/api/v1/analyses/abc-123/result

# ⑤ 合并
curl -X POST http://localhost:8080/api/v1/merge \
  -H "Content-Type: application/json" \
  -d '{
    "records": [ /* ④ 的 records 数组 */ ],
    "options": { "threshold": 0.80, "mode": "max" }
  }'
```

### 12.3 Swagger UI

`http://localhost:8080/swagger-ui.html` 自动列出全部端点，可交互调用。

### 12.4 健康检查

`GET /actuator/health` 返回 `{"status":"UP"}` 即服务就绪。

### 12.5 BTP 集成测试

production 部署后用 `client_credentials` 取 JWT，附在请求头测试：

```bash
TOKEN=$(curl -s -X POST $XSUAA_TOKEN_URL/oauth/token \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d "grant_type=client_credentials" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" \
  https://ifmerge-srv.cfapps.<region>.hana.ondemand.com/api/v1/analyses/abc-123
```

---

## 13. 与原 Python 的模块对应关系

### 13.1 算法层

| Python 代码 | Java 实现 |
|---|---|
| `if_grouper.py:24-91` `group_by_if` | `IFAggregator.aggregate()` |
| `similarity_calculator.py:42-49` max | `SimilarityCalculator.calculate(MAX)` |
| `similarity_calculator.py:51-61` avg | `SimilarityCalculator.calculate(AVG)` |
| `merge_grouper.py:24-58` UnionFind | `UnionFind.find()` / `union()` |
| `merge_grouper.py:78-103` 分组 | `MergeGrouper.groupSimilarIFs()` |
| `cli.py:223-229` 模块连号 | `GroupIdAllocator.next(module)` |
| `result_generator.py:195-226` 根拠 | `GroupingReasonBuilder.build()` |
| `template_filler.py:142-145` 去重 | `FieldDeduplicator.dedupe()` |

### 13.2 AI 调用层

| Python 代码 | Java 实现 |
|---|---|
| `ai_analyzer.py:46-74` Phase1 prompt | `Phase1PromptBuilder.build()` |
| `ai_analyzer.py:318-330` `_format_sheet_head` | `Phase1PromptBuilder.formatSheetHead()` |
| `ai_analyzer.py:140-198` Phase2 prompt | `SapAiCoreClient.analyzePhase2()` |
| `ai_analyzer.py:366-375` chunk 分割 | `AnalyzeDocumentUseCase.splitIntoChunks()` |
| `ai_analyzer.py:289-315` retry | `application.yaml::resilience4j.retry` |
| `ai_classifier.py:26-169` 分類 | `SapAiCoreClient.classify()` + prompts.yaml |
| `ai_generator.py:236-382` 概要+代表項 | `SapAiCoreClient.generateAllIfInfo()` |
| `ai_generator.py:384-459` 合并名 | `SapAiCoreClient.generateMergedIfName()` |
| `prompt_config.py:11-48` PromptConfig | `YamlPromptRepository` |

### 13.3 编排与入口

| Python 代码 | Java 实现 |
|---|---|
| `main.py` 解析全流程 | `AnalyzeDocumentUseCase.runAsync()` |
| `cli.py:54-100` 合并全流程 | `MergeInterfacesUseCase.runAsync()` |
| `argparse` CLI | `AnalysisController` / `MergeController`（REST） |
| `scanner.py` 文件扫描 | （客户端持有，CAP 接收 JSON） |
| `parser.py` 应答解析 | `SapAiCoreClient` 内部 |
| `result_generator.py:42-178` 结果写 Excel | （客户端持有） |
| `matrix_exporter.py` 矩阵写 Excel | （客户端持有） |

### 13.4 数据模型

| Python dataclass | Java record |
|---|---|
| `CleanedSheet` | `CleanedSheet` |
| `InterfaceRecord` | `InterfaceRecord` |
| `IFInfo` | `IFInfo` |
| `OutputRow` | （拆为 `MergeGroup` + `MergeResult.Summary`） |
| 元组 `(tableId, itemId)` | `FieldPair` record |
| 元组 `(if1, if2, sim)` | `SimilarityPair` record |
