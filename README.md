# IFmerge CAP (Java)

EBS 接口设计书**解析 · 合并**系统的 SAP CAP for Java 后端。

把数百份格式各异的历史 EBS 接口设计书，通过「LLM 结构识别 + 字段抽取」与「相似度算法 + LLM 命名」两条流水线，转换为合并优化后的标准接口清单。纯 JSON in / JSON out。

| 项目 | 内容 |
|---|---|
| 框架 | SAP CAP for Java 3.0 + Spring Boot 3.2 / JDK 17 |
| 架构 | Hexagonal 四模块（db / domain / application / infrastructure） |
| LLM | SAP AI Core（默认 `claude-4.6-sonnet`，Resilience4j 重试 + 熔断） |
| 持久化 | 无数据库，业务数据活在 JVM 内存（Job TTL 15m） |
| 鉴权 | XSUAA OAuth2 client_credentials |
| 部署 | MTA → BTP Cloud Foundry（`java_buildpack`） |

详细设计见 [`IFMERGE-CAP改造设计书.md`](./IFMERGE-CAP改造设计书.md)，部署见 [`ifmerge-cap部署指南.md`](./ifmerge-cap部署指南.md)。

---

## 项目结构

```
ifmerge-cap/
├── pom.xml                            ← parent（多模块聚合）
├── mta.yaml                           ← BTP CF 部署描述符
├── xs-security.json                  ← XSUAA scope / role 定义
├── db/
│   └── src/main/resources/schema.cds ← 仅声明 namespace（保留扩展点）
├── domain/                            ← 模块 1：纯 Java（enforcer 锁死框架依赖）
│   └── .../domain/
│       ├── analysis/  (model + port + service)
│       ├── merge/     (model + service + port)
│       └── shared/exception/
├── application/                       ← 模块 2：Use Case 编排 + Spring DI
│   └── .../application/
│       ├── analysis/  (AnalyzeDocumentUseCase + Command)
│       ├── merge/     (MergeInterfacesUseCase + Command)
│       └── job/       (Job + JobStore + JobManager)
└── infrastructure/                    ← 模块 3：Spring Boot 主 jar
    └── src/main/
        ├── java/.../infrastructure/
        │   ├── IfmergeApplication.java          ← Spring Boot main
        │   ├── adapter/inbound/                 ← REST Controller + DTO + Mapper
        │   ├── adapter/outbound/ai/             ← SAP AI Core 客户端
        │   ├── adapter/outbound/storage/        ← InMemoryJobStore
        │   └── config/                          ← Async / Security / Cleanup
        └── resources/
            ├── application.yaml / application-prod.yaml
            ├── prompts.yaml                     ← LLM 提示词模板
            ├── logback-spring.xml
            └── cds/                             ← CAP service 定义（脚手架）
```

### Maven 模块依赖

```
infrastructure (Spring Boot main)
       │
       ▼
   application (Spring DI only)
       │
       ▼
     domain (pure Java, 由 maven-enforcer-plugin 锁死，禁止引入 Spring / CAP / POI)
```

---

## 构建运行

```bash
# 全量编译（domain → application → infrastructure）
mvn clean install -DskipTests

# 本地运行（dev 默认 permitAll=true，跳过 XSUAA）
mvn -pl infrastructure -am spring-boot:run
```

- 服务地址：`http://localhost:8080`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`GET /actuator/health`

> 无 Mock 模式：AI 调用一律走真实 SAP AI Core，启动前需在 `application.yaml` 或环境变量中配好 `ifmerge.ai.*` 凭证。脱机联调可用测试中的 `FakeSapAiCoreClient`。

---

## API

服务基础路径 `/api/v1`：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/analyze` | 提交解析任务，立即返回 Job |
| `GET` | `/analyses/{id}` | 轮询解析 Job 状态 |
| `GET` | `/analyses/{id}/result` | 取解析结果 |
| `POST` | `/merge` | 提交合并任务，立即返回 Job |
| `GET` | `/merges/{id}` | 轮询合并 Job 状态 |
| `GET` | `/merges/{id}/result` | 取合并结果 |

请求/响应示例见 [改造设计书 §6](./IFMERGE-CAP改造设计书.md) 与 Swagger UI。

---

## 处理流程

**解析（Analysis）**：`POST /analyze` → Phase 1 结构识别（LLM）→ Phase 2 按 sheet × chunk 抽取字段（LLM）→ 组装 `AnalysisResult`。

**合并（Merge）**：`POST /merge` → `records` 集约为 IF → AI 分类（模块 × 场景）→ AI 概要 + 代表项 → 相似度矩阵（max/avg）+ Union-Find 分组 → AI 生成合并接口名 + 字段去重 → `MergeResult`。

两条流水线均为 `@Async` 提交后立即返回 jobId，客户端轮询 Job 获取进度与结果。

---

## 测试

```bash
# domain 层单元测试（相似度算法 / Union-Find / 提示词构建）
mvn -pl domain test

# 端到端 curl 脚本
scripts/e2e-test.sh
```

---

## 部署

构建 `.mtar` 并部署到 BTP Cloud Foundry：

```bash
mbt build -t ./mta_archives
cf deploy mta_archives/ifmerge-cap_1.0.0.mtar
```

完整前提条件、环境变量、验证与 FAQ 见 **[ifmerge-cap部署指南.md](./ifmerge-cap部署指南.md)**。
