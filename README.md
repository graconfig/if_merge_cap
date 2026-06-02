# IFmerge CAP (Java)

EBS 接口设计书解析・合并系统的 CAP for Java 后端。

> 这是骨架代码（v1.0 skeleton），关键 Spring Bean / interface / CDS 文件已就位，**核心业务代码标记为 `TODO`，需在 Phase 1-2 阶段填充**。

---

## 项目结构

```
ifmerge-cap/
├── pom.xml                            ← parent
├── db/
│   ├── pom.xml
│   └── src/main/resources/schema.cds  ← 空（保留扩展点）
├── domain/                            ← 模块 1：纯 Java（零框架）
│   ├── pom.xml                        ← 含 enforcer 锁死纯度
│   └── src/main/java/.../domain/
│       ├── analysis/  (model + port)
│       ├── merge/     (model + service + port)
│       └── shared/exception/
├── application/                       ← 模块 2：Use Case 编排
│   ├── pom.xml
│   └── src/main/java/.../application/
│       ├── analysis/  (UseCase + Command)
│       ├── merge/     (UseCase + Command)
│       └── job/       (Job + JobStore + JobManager)
└── infrastructure/                    ← 模块 3：Spring Boot 主 jar
    ├── pom.xml
    └── src/main/
        ├── java/.../infrastructure/
        │   ├── IfmergeApplication.java          ← Spring Boot main
        │   ├── adapter/inbound/                  ← CAP handlers
        │   ├── adapter/outbound/ai/             ← SAP AI Core 客户端
        │   ├── adapter/outbound/storage/        ← InMemoryJobStore
        │   └── config/                          ← Async / Security / Cleanup
        └── resources/
            ├── application.yaml
            ├── prompts.yaml
            ├── logback-spring.xml
            └── cds/                              ← analysis-service.cds / merge-service.cds
```

详细设计见 [`/data/IFmerge/DESIGN_CAP.md`](../../IFmerge/DESIGN_CAP.md)。

---

## 依赖关系（Maven）

```
infrastructure (Spring Boot main)
       │
       ▼
   application (Spring DI only)
       │
       ▼
     domain (pure Java)
```

`domain/pom.xml` 通过 `maven-enforcer-plugin` 锁死，**禁止引入 Spring Boot / CAP / POI 等框架**。

---

## 构建运行

```bash
# 进入项目目录
cd /data/HuangCX/ifmerge-cap

# 全量编译
mvn clean install -DskipTests

# 本地运行
cd infrastructure
mvn spring-boot:run
```

访问 `http://localhost:8080/swagger-ui.html` 查看 API（需配置 XSUAA 才能调用）。

---

## 完成度

| 模块 | 状态 |
|---|---|
| `domain` 数据模型（records） | ✅ 完成 |
| `domain` 相似度算法 / Union-Find / GroupingReason / FieldDeduplicator | ✅ 完成 |
| `domain` Port 接口 | ✅ 完成 |
| `application` Job 管理 | ✅ 完成 |
| `application` AnalyzeUseCase Phase1+Phase2 编排 | ⚠️ TODO（Phase 2 chunk 循环未实现） |
| `application` MergeUseCase 完整编排 | ⚠️ TODO（分类→分组→AI 命名循环未实现） |
| `infrastructure` Spring Boot main + AsyncConfig + Security + JobCleanup | ✅ 完成 |
| `infrastructure` InMemoryJobStore | ✅ 完成 |
| `infrastructure` YamlPromptRepository | ✅ 完成 |
| `infrastructure` SapAiCoreClient（实际 HTTP 调用） | ⚠️ TODO（仅占位） |
| `infrastructure` CAP Handlers（DTO 映射） | ⚠️ TODO（仅占位） |
| CDS Service 定义 | ✅ 完成 |
| `prompts.yaml` 模板 | ✅ 完成（与 Python 原工程一致） |
| `application.yaml` | ✅ 完成 |
| `mta.yaml`、`xs-security.json` | ❌ 未生成（请单独要求） |

---

## 下一步

按 `DESIGN_CAP.md` 的迁移路径推进：

1. **Phase 1（2 周）**：填充 `domain/` 中 TODO 的算法（已有骨架，主要补 service 类实现 + 单元测试）
2. **Phase 2（2 周）**：实现 `SapAiCoreClient` 与 prompt 渲染逻辑
3. **Phase 3（1-2 周）**：完善 CAP handlers 的 DTO 映射，集成 XSUAA
4. **Phase 4（1 周）**：联调 GUI 端 + 与 Python 旧版对比测试
5. **Phase 5（1 周）**：部署 BTP
