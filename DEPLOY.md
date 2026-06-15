# IFmerge CAP — BTP Cloud Foundry 部署手册

> 后端 `ifmerge-cap` 部署到 SAP BTP Cloud Foundry 的可照做清单。
> 认证设计：**入站**（GUI→CAP）用 XSUAA JWT；**出站**（CAP→SAP AI Core）用 BTP **Destination**（名 `ifmerge-aicore`），凭证集中在 Destination，不进 app 配置/环境变量。

---

## 0. 关键对象一览（部署涉及的名字）

| 对象 | 名称 | 来源 |
|---|---|---|
| 应用 | `ifmerge-cap`（mta 模块名 = CF 应用名） | mta.yaml |
| 可执行 jar | `infrastructure/target/ifmerge-cap-srv-exec.jar` | spring-boot repackage |
| XSUAA 实例 | `ifmerge-xsuaa` | mta 资源（按 `xs-security.json`） |
| Destination 服务实例 | `ifmerge-destination` | mta 资源（plan: lite） |
| **Destination（要手建）** | **`ifmerge-aicore`** | Cockpit / 实例 init_data |
| 激活 profile | `prod` | `SPRING_PROFILES_ACTIVE=prod` |

---

## 1. 一次性前提

### 1.1 本地工具
```bash
cf version                      # Cloud Foundry CLI
cf install-plugin multiapps     # MTA 部署插件（cf deploy 命令来自它）
mbt --version                   # Cloud MTA Build Tool
java -version                   # JDK 17
mvn -version                    # Maven
```

### 1.2 BTP 子账号准备
- 已开通 **Cloud Foundry runtime**，并有目标 org / space。
- Entitlements 已加：**xsuaa**（plan: application）、**destination**（plan: lite）。
- 有一份可用的 **SAP AI Core service key**（含 url / clientid / clientsecret / 以及其 token 端点）。

### 1.3 ⚠️ 部署前两个硬前提（务必先解决）
1. **依赖版本对齐**：`pom.xml` 的 `<sap-cloud-sdk.version>` 当前是占位 `5.20.0`，请按你环境确认可用版本，并确保 `connectivity-destination-service` 能提供 `DestinationAccessor` API。
2. **先编译通过**：在 JDK17 机器执行
   ```bash
   mvn -B clean install -DskipTests
   ```
   重点确认 `SapAiCoreClient` / `DeploymentResolver` / `SapAiCoreProperties` 编译无误。

---

## 2. 在 BTP 建 Destination `ifmerge-aicore`

> 出站调 AI Core 的 URL + OAuth2 认证全在这里，app 不持有凭证。

**Cockpit 路径**：子账号 → Connectivity → **Destinations** → New Destination

| 字段 | 值 |
|---|---|
| Name | `ifmerge-aicore` |
| Type | `HTTP` |
| URL | AI Core base，如 `https://api.ai.prod.<region>.aws.ml.hana.ondemand.com/v2` |
| Proxy Type | `Internet` |
| Authentication | `OAuth2ClientCredentials` |
| Token Service URL | `https://<subaccount>.authentication.<region>.hana.ondemand.com/oauth/token` |
| Client ID | AI Core service key 的 `clientid` |
| Client Secret | AI Core service key 的 `clientsecret` |

> 说明：`AI-Resource-Group` 头由 app 自己加（`IFMERGE_AI_RESOURCE_GROUP`，默认 `default`），不放 Destination。
> Destination 建在子账号级即可——绑定了 `ifmerge-destination` 服务实例的 app 都能读到。
> 轮换凭证：以后只在此页改 Client Secret，**无需重新部署**。

---

## 3. 构建 mtar

```bash
cd /path/to/ifmerge-cap
mbt build                       # 读 mta.yaml，内部跑 mvn package
# 产物：mta_archives/ifmerge-cap_1.0.0.mtar
```

---

## 4. 部署（MTA 路线，推荐）

```bash
cf login                        # 登录到目标 org/space（或 cf login --sso）
cf target -o <ORG> -s <SPACE>

cf deploy mta_archives/ifmerge-cap_1.0.0.mtar
# 部署插件会自动：
#   - 创建 ifmerge-xsuaa（按 xs-security.json）
#   - 创建 ifmerge-destination（destination/lite）
#   - 推送 app、绑定上述两个服务、注入 SPRING_PROFILES_ACTIVE=prod 等环境变量
```

如需按环境覆盖非机密参数（model-name/resource-group/destination-name）：
```bash
cp ifmerge-secrets.mtaext.example ifmerge.mtaext   # 编辑后
cf deploy mta_archives/ifmerge-cap_1.0.0.mtar -e ifmerge.mtaext
```

> 注意：`ifmerge-destination` 只是创建了**空的 destination 服务实例**；真正的 `ifmerge-aicore` Destination 仍需第 2 步在 Cockpit 建好（两者配合：服务实例让 app 有读 destination 的权限，Destination 提供具体连接信息）。

---

## 6. 部署后验证（dev space）

```bash
# 6.1 应用起来了？
cf apps
cf logs ifmerge-cap --recent | tail -50      # 看启动日志

# 6.2 健康检查（无需鉴权）
curl https://<APP_ROUTE>/actuator/health          # 期望 {"status":"UP"}

# 6.3 鉴权生效？（prod 应拒绝无 token 的业务请求）
curl -i https://<APP_ROUTE>/api/v1/merges/00000000-0000-0000-0000-000000000000
# 期望 401（说明 XSUAA JWT 校验已开启）—— ⚠️ 见第 11 节风险

# 6.4 取一个 token（client_credentials），跑端到端
#     从 xsuaa 服务键拿 GUI/测试用凭证：
cf create-service-key ifmerge-xsuaa ifmerge-key
cf service-key ifmerge-xsuaa ifmerge-key          # 记下 url / clientid / clientsecret

TOKEN=$(curl -s -X POST "<xsuaa-url>/oauth/token" \
  -u "<clientid>:<clientsecret>" \
  -d "grant_type=client_credentials" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

# 6.5 带 token 调真实解析（会真打 AI Core，验证 Destination 链路）
curl -s -X POST https://<APP_ROUTE>/api/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"fileName":"BDN-TEST-001.xlsx","sheets":[{"name":"エクスポート項目","headers":["No","表名","表ID","項目ID","桁"],"rows":[["1","受注","OE_HDR","ORDER_NUMBER","10"]]}],"options":{"phase1HeadRows":30,"maxChunkRows":100}}'
# 期望 201 + jobId；随后轮询：
# curl https://<APP_ROUTE>/api/v1/analyses/<jobId> -H "Authorization: Bearer $TOKEN"
```

**验证通过标准**：health=UP、无 token 业务请求 401、带 token 的 /analyze 返回 201 且 Job 最终 SUCCEEDED、日志出现 `[TOKEN]` 行（说明真实调用 AI Core 成功）。

---

## 7. GUI 接入

GUI 用 **client_credentials** 调本后端，凭证来自 `ifmerge-xsuaa` 的 service key（第 6.4 步）：
- `XSUAA_TOKEN_URL` = service key 的 `url`
- `CLIENT_ID` / `CLIENT_SECRET` = service key 的 `clientid` / `clientsecret`
- `CAP_BASE_URL` = `https://<APP_ROUTE>`

> 这套是 GUI↔CAP 的 XSUAA 凭证，**与** Destination 里 CAP↔AI Core 的凭证**完全无关**，两套独立。

---

## 8. 本地运行（实跑 AI Core）

本地无 destination 服务，用 SAP Cloud SDK 的 `destinations` 环境变量提供同名 Destination：
```bash
export destinations='[{"name":"ifmerge-aicore","url":"https://api.ai.prod.<region>.aws.ml.hana.ondemand.com/v2","authentication":"OAuth2ClientCredentials","tokenServiceURL":"https://<subaccount>.authentication.<region>.hana.ondemand.com/oauth/token","clientId":"sb-...","clientSecret":"..."}]'

mvn -pl infrastructure -am spring-boot:run -Dspring-boot.run.profiles=local
```
（详见 `application-local.yaml.example`。本地 `permitAll=true`，免 token。）

---

## 9. 运维常用

```bash
# 切换大模型（model-name 是普通环境变量，可直接改）
cf set-env ifmerge-cap-srv IFMERGE_AI_MODEL_NAME "<new-model>"
cf restage ifmerge-cap-srv
#   前提：该模型在你的 AI Core 有 RUNNING 的 deployment

# 轮换 AI Core 凭证：Cockpit 改 Destination ifmerge-aicore 的 Client Secret（免重部署）

# 看日志 / token 用量
cf logs ifmerge-cap-srv --recent | grep '\[TOKEN\]'
```

---

## 10. 故障排查

| 现象 | 可能原因 / 处理 |
|---|---|
| 启动报 `no JwtDecoder` / oauth2 相关 | 第 11 节风险：XSUAA 自动配置未触发；检查 `ifmerge-xsuaa` 绑定，或加显式 `issuer-uri` |
| 调用报 `DestinationNotFound` / 找不到 ifmerge-aicore | Cockpit 未建 Destination，或 `ifmerge-destination` 未绑定 |
| `没有匹配的 RUNNING deployment` | `IFMERGE_AI_MODEL_NAME` 在 AI Core 没有对应 RUNNING 部署；改 model 或设 `IFMERGE_AI_DEPLOYMENT_ID` |
| 首次 LLM 调用 401/403 | Destination 的 clientId/secret 或 tokenServiceURL 配错 |
| 编译失败（找不到 DestinationAccessor） | SDK 版本/坐标未对齐（第 1.3 节） |
| health UP 但业务 500 | 看 `cf logs`；常见为 AI Core 网络不通或 resource-group 不对 |

---

## 11. 已知风险与待办

1. **prod JWT 解码未实测**：`SecurityConfig` 用 `.jwt(jwt -> {})` 空配置，依赖 SAP 安全库从 VCAP 的 xsuaa 绑定自动建 JwtDecoder。**首次上 dev space 重点验证 6.3**；若启动报 no JwtDecoder，需加显式 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 或自定义 decoder。
2. **SDK 依赖版本**：`sap-cloud-sdk.version` 为占位，部署前对齐（第 1.3 节）。
3. **旧 secret 轮换**：历史上 AI Core 凭证曾明文进过 git，建议在 BTP 重新生成 service key 并更新 Destination。
4. **buildpack**：默认 `java_buildpack`；若你的环境用 SAP 提供的 `sap_java_buildpack`，在 mta.yaml 改之。
5. **region/URL**：`xs-security.json` 的 `redirect-uris`、Destination 的 URL/region 需与你实际 landscape 一致。

---

## 12. 一页流程图

```
[JDK机] mvn clean install            ← 先确认编译通过 + SDK 版本对齐
   │
[Cockpit] 建 Destination ifmerge-aicore（OAuth2ClientCredentials）
   │
mbt build → ifmerge-cap_1.0.0.mtar
   │
cf deploy *.mtar [-e ifmerge.mtaext]
   ├─ 自动建/绑 ifmerge-xsuaa + ifmerge-destination
   └─ 注入 SPRING_PROFILES_ACTIVE=prod + IFMERGE_AI_*（非机密）
   │
[dev验证] health=UP → 无token 401 → 带token /analyze 201 → Job SUCCEEDED + [TOKEN]
   │
[GUI] 用 ifmerge-xsuaa service key 配置 → 正式联调
```
