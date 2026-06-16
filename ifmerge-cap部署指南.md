# ifmerge-cap 部署指南

## 概览

| 项目 | 内容 |
|---|---|
| 运行时 | SAP CAP for Java 3.0 + Spring Boot 3.2 / JDK 17 |
| 数据库 | 无（业务数据全部活在 JVM 内存 / 请求体，不绑定 HANA） |
| 鉴权 | XSUAA（application plan，OAuth2 client_credentials） |
| LLM | SAP AI Core（env 方式：凭证明文 + 环境变量覆盖） |
| 部署方式 | MTA → Cloud Foundry（`java_buildpack`） |
| 应用模块 | `ifmerge-srv`（MTA ID：`ifmerge-cap`） |
| 服务基础路径 | `POST /api/v1/analyze`、`POST /api/v1/merge` |
| 健康检查 | `GET /actuator/health` |

---

## 前提条件

### 本地工具

```bash
# JDK 17（强制）
java -version

# Maven 3.9+
mvn -v

# MBT 构建工具（生成 .mtar）
npm install -g mbt

# CF CLI（参考官方文档安装）
cf -v

# CF MTA 插件
cf install-plugin multiapps
```

### BTP 平台资源

- Cloud Foundry 环境的 Org / Space 已开通，账号有部署权限
- SAP AI Core 服务实例及 Service Key（提供 `clientid` / `clientsecret` / `url` / `tokenurl`）
- 可创建 `xsuaa`（application plan）服务实例的配额

> 本工程**不依赖 HANA**：所有业务数据（Job、解析/合并结果）仅保存在 JVM 内存中，Job 默认 TTL 15 分钟后清理。无需 HDI 容器，无需 `cds deploy`。

---

## 配置文件准备

### 1. `mta.yaml`

部署描述符 `mta.yaml` 已包含在仓库中。`parameters` 段集中维护 AI Core 凭证与端点，部署前确认/替换以下值：

| 参数 | 说明 |
|---|---|
| `ai-auth-url` | AI Core OAuth token 端点，格式 `https://<subdomain>.authentication.<region>.hana.ondemand.com` |
| `ai-base-url` | AI Core API 基础 URL（含 `/v2`） |
| `ai-resource-group` | AI Core 资源组，默认 `default` |
| `ai-model-name` | LLM 模型部署名，默认 `claude-4.6-sonnet` |
| `ai-client-id` | AI Core Service Key 中的 `clientid` |
| `ai-client-secret` | AI Core Service Key 中的 `clientsecret` |

> ⚠️ **安全提示**：按技术部门方针，AI Core 凭证以**明文**形式写在 `mta.yaml` 的 `parameters` 中并作为环境变量注入。这意味着机密会随仓库 / MTA 包一起分发，请在运维层面控制访问权限。如需避免明文入库，可改用 MTA 扩展描述符（`*.mtaext`，已在 `.gitignore` 中排除，提交 `*.mtaext.example` 雛形即可）。

### 2. 环境变量映射

`mta.yaml` 将上述参数映射为应用环境变量（`IFMERGE_AI_*`），由 `SapAiCoreProperties` 绑定：

| 环境变量 | 来源参数 | 说明 |
|---|---|---|
| `IFMERGE_AI_AUTH_URL` | `ai-auth-url` | AI Core token 端点 |
| `IFMERGE_AI_BASE_URL` | `ai-base-url` | AI Core API URL |
| `IFMERGE_AI_RESOURCE_GROUP` | `ai-resource-group` | 资源组 |
| `IFMERGE_AI_MODEL_NAME` | `ai-model-name` | 模型部署名 |
| `IFMERGE_AI_CLIENT_ID` | `ai-client-id` | OAuth client id |
| `IFMERGE_AI_CLIENT_SECRET` | `ai-client-secret` | OAuth client secret |
| `SPRING_PROFILES_ACTIVE` | （固定 `prod`） | 启用 `application-prod.yaml`，`permitAll=false` |
| `JBP_CONFIG_OPEN_JDK_JRE` | （固定 `17.+`） | 强制 JDK 17 |

其它可选覆盖项（默认值见 `application.yaml`）：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `IFMERGE_SECURITY_PERMITALL` | `false`（prod） | `true` 跳过 JWT 校验（仅 dev） |
| `IFMERGE_ANALYSIS_PHASE1HEADROWS` | 30 | Phase 1 每 sheet 头部行数 |
| `IFMERGE_ANALYSIS_MAXCHUNKROWS` | 100 | Phase 2 单 chunk 最大行数 |
| `IFMERGE_MERGE_DEFAULTTHRESHOLD` | 0.80 | 默认相似度阈值 |
| `IFMERGE_MERGE_DEFAULTMODE` | max | 默认相似度模式（max / avg） |
| `IFMERGE_JOB_TTL` | 15m | Job 内存保留时间 |
| `IFMERGE_JOB_MAXCONCURRENT` | 10 | 同时在跑 Job 上限 |

---

## 部署步骤

### 1. 登录 CF

```bash
cf login -a https://api.cf.<region>.hana.ondemand.com
# 选择正确的 Org 和 Space
```

### 2. 构建 MTA 包

`mta.yaml` 中已声明 custom builder，会自动执行 `mvn -B -q clean package -DskipTests`，产物为可执行 jar `infrastructure/target/ifmerge-cap-srv-exec.jar`：

```bash
mbt build -t ./mta_archives
# 产物：mta_archives/ifmerge-cap_1.0.0.mtar
```

> 如需先单独验证 Maven 构建：`mvn clean package`（多模块顺序：domain → application → infrastructure）。

### 3. 部署

```bash
cf deploy mta_archives/ifmerge-cap_1.0.0.mtar
```

首次部署会自动创建以下服务并绑定到 `ifmerge-srv`：

- `ifmerge-xsuaa` — XSUAA（application plan，descriptor 见 `xs-security.json`）

> 无 HANA / HDI 服务，无需数据库初始化步骤。

### 4. 验证

```bash
# 查看应用状态
cf apps

# 查看启动日志
cf logs ifmerge-srv --recent

# 查看服务绑定
cf services

# 健康检查（应返回 {"status":"UP"}）
curl https://ifmerge-srv.cfapps.<region>.hana.ondemand.com/actuator/health
```

---

## 重新部署

```bash
# 正常更新
mbt build -t ./mta_archives
cf deploy mta_archives/ifmerge-cap_1.0.0.mtar

# 如果 XSUAA 实例损坏（create failed 状态）
cf delete-service ifmerge-xsuaa -f
cf deploy mta_archives/ifmerge-cap_1.0.0.mtar
```

---

## 本地开发

```bash
# 全量编译
cd /data/HuangCX/ifmerge-cap
mvn clean install -DskipTests

# 启动（dev 默认 permitAll=true，跳过 XSUAA）
mvn -pl infrastructure -am spring-boot:run
# 服务地址：http://localhost:8080
# Swagger UI：http://localhost:8080/swagger-ui.html
```

> 本地运行**无 Mock 模式**：AI 调用一律走真实 SAP AI Core，启动前需在 `application.yaml` 或环境变量中配好真实 `ifmerge.ai.*` 凭证；如需脱机联调，可在测试中用 `FakeSapAiCoreClient` 替换 AI 网关。

本地敏感配置建议放在 `application-local.yaml`（已在 `.gitignore` 中排除），通过 `--spring.profiles.active=local` 加载。

---

## API 接口

服务基础路径：`/api/v1`

| 方法 | 路径 | 说明 | 状态码 |
|---|---|---|---|
| `POST` | `/analyze` | 提交设计书解析任务，立即返回 Job | 201 / 400 |
| `GET` | `/analyses/{id}` | 轮询解析 Job 状态 | 200 / 404 |
| `GET` | `/analyses/{id}/result` | 取解析结果 | 200 / 404 / 409 |
| `POST` | `/merge` | 提交接口合并任务，立即返回 Job | 201 / 400 |
| `GET` | `/merges/{id}` | 轮询合并 Job 状态 | 200 / 404 |
| `GET` | `/merges/{id}/result` | 取合并结果 | 200 / 404 / 409 |
| `GET` | `/actuator/health` | 健康检查 | 200 |
| `GET` | `/swagger-ui.html` | Swagger UI | 200 |

### analyze 请求示例

```json
POST /api/v1/analyze
Content-Type: application/json
Authorization: Bearer <xsuaa-token>

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

响应（即时）：

```json
{ "id": "abc-123", "type": "ANALYSIS", "status": "PENDING", "progress": 0 }
```

### 获取 XSUAA Token（prod 集成测试）

```bash
TOKEN=$(curl -s -X POST $XSUAA_TOKEN_URL/oauth/token \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d "grant_type=client_credentials" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" \
  https://ifmerge-srv.cfapps.<region>.hana.ondemand.com/api/v1/analyses/abc-123
```

---

## 常见问题

| 错误 | 原因 | 解决 |
|---|---|---|
| `Invalid xsappname` | `${org}` 展开后含空格 | `xs-security.json` 中 `xsappname` 固定为 `ifmerge-cap`，不引用 `${org}` |
| `401 Unauthorized` | prod 下缺少 / 无效 JWT | 用 `client_credentials` 取 XSUAA token 后放入 `Authorization` 头 |
| `403 Forbidden` | 缺少 `$XSAPPNAME.Api` scope | 在 BTP Cockpit 为用户/客户端分配 `IFmergeApiUser` 角色 |
| AI 调用全部失败 / 重试耗尽 | AI Core 凭证或端点错误 | 核对 `mta.yaml` 中 `ai-client-id/secret/auth-url/base-url`，确认资源组存在该模型部署 |
| `create failed` 服务状态 | 上次部署中途失败留下损坏实例 | `cf delete-service ifmerge-xsuaa -f` 后重新部署 |
| 应用启动后 `/actuator/health` 502 | JDK 版本不符 / 内存不足 | 确认 `JBP_CONFIG_OPEN_JDK_JRE` 为 `17.+`，必要时调高 `memory`（默认 1024M） |
| `404 JOB_NOT_FOUND` | Job 已超过 TTL（默认 15m）被清理 | 重新提交任务；如需延长保留改 `IFMERGE_JOB_TTL` |
| `409 JOB_NOT_COMPLETED` | Job 未完成时取结果 | 先轮询 `/{id}` 直到 `status=SUCCEEDED` 再取 `/result` |
```