# SynPharm · AI 药物研发平台

从靶点到候选的一体化药物研发工作台：**需求基线 → 候选生成 → 评估与编辑 → 决策** 四步各自留下
可回放的快照与指纹；另有一条独立产品线做**分子预测**（PPI / DTI / DDI）与三维结构可视化。

整个项目围绕一条底线设计：**算不出来就说算不出来**。
指标缺失时留空并如实告知覆盖率、判定不了标「待判定」、证据不全的候选进不了决策表 ——
不用估算值或默认值冒充结论。

---

## 目录

- [目录结构](#目录结构)
- [业务主线](#业务主线)
- [快速开始](#快速开始)
- [服务与数据所有权](#服务与数据所有权)
- [网关](#网关)
- [技术栈与版本上界](#技术栈与版本上界)
- [开发约定](#开发约定)
- [端到端验证](#端到端验证)
- [排障](#排障)
- [安全说明](#安全说明)
- [已知边界](#已知边界)
- [文档索引](#文档索引)
- [修订记录](#修订记录)

---

## 目录结构

**Java 服务直接暴露在仓库根**，根目录的 [`pom.xml`](./pom.xml) 就是 Maven 多模块聚合工程 ——
IDE 打开仓库根即可识别全部服务，`mvn -pl synpharm-core spring-boot:run` 一条命令启单个服务。

```
synpharm-workspace/            ← git 仓库根 = Maven 多模块根
├── pom.xml                    聚合工程（BoM + 版本 + 编译配置）
├── Dockerfile                 全部 Java 服务共用（MODULE 参数选择目标，构建上下文=仓库根）
├── maven-settings.xml         阿里云镜像
│
├── synpharm-common/           公共库：信封 / 错误码 / 身份头 / 过滤器 / JSON
├── synpharm-api/              服务间契约：Feign 接口 + 跨服务 DTO
├── synpharm-gateway/          边缘网关（唯一鉴权点）
├── synpharm-auth/             身份与访问
├── synpharm-core/             设计域：需求分析 / 生成 / 编辑 / 决策
├── synpharm-platform/         平台治理：工具注册表 + 能力探测
├── synpharm-asset/            文件资产：内容寻址 + 引用计数
├── synpharm-predict/          预测域：DTI / PPI / DDI
│
├── synpharm-frontend/         Vue 3 + Vite
├── synpharm-fastapi/          算法引擎
├── synpharm-deploy/           编排 / SQL / 文档
└── e2e-*.ps1                  端到端验证脚本（14 个）
```

> **2026-11-04 两处结构调整**（细节见 [微服务拆分执行记录](./synpharm-deploy/docs/architecture/微服务拆分执行记录.md)）：
> ① Java 服务从 `synpharm-cloud/` 扁平化到仓库根；
> ② `platform` / `asset` 从 `core` 重新独立成服务，路径收敛为 `/api/platform/**`，
> 每个网关前缀唯一对应一个服务，不再依赖路由声明顺序。

---

## 业务主线

```
需求分析      八块基线（靶点 / 结构 / 分子形式 / 硬约束 / 软约束 / 指标 / 配额 / 风险）
   ↓          不齐不允许冻结；冻结后的快照是后续唯一输入
候选生成      去重 → 粗筛 → 多样性配额，逐分子留下结构化原因
   ↓
评估与编辑    按版本冻结分数 → 诊断缺口 → 匹配动作 → 评估影响面
   ↓
决策          证据装配 → 硬约束复核 → 业务否决 → 分桶排序 → 名额分配 → 确认与回放
```

### 三条不可绕过的规则

1. **证据先于判断** —— 没有冻结证据包的候选进不了决策表。
2. **否决不可绕过** —— BLOCK 级否决与硬约束不达标只能改数据后重新决策，
   不能在决策层手动改成「入选」。
3. **确认即不可改** —— 确认后任何去向都不能再改；要改就新建一版并指向上一版，历史只增不删。

---

## 快速开始

### 1. 准备环境变量

**仓库里不含任何真实口令**，所有 `.env` 都被忽略，只提供模板：

```bash
cd synpharm-deploy
cp .env.example .env
# 按注释填写：数据库口令、JWT 密钥、内部令牌、算法引擎 API Key
# 随机值可用：openssl rand -hex 32
```

### 2. 构建分子编辑器子应用（必须）

二维画布的化学引擎是独立构建的第三方产物，约 30 MB，**不入库**：

```bash
cd synpharm-frontend
npm install
npm run build:ketcher     # 生成 public/ketcher/，缺它画布白屏
```

### 3. 启动整套服务

```bash
cd synpharm-deploy
docker compose -f docker-compose.dev.yml up -d
```

首次启动会自动创建各服务独立数据库账号与 schema，并执行 `sql/` 下的初始化脚本。
全部就绪约需 5～8 分钟（`mvn-setup` 全量 `clean install` + 6 个服务依次启动）。

### 4. 访问

| 入口 | 地址 | 说明 |
|---|---|---|
| 前端 | http://localhost:15174 | 端口取 `.env` 的 `FRONTEND_DEV_PORT` |
| 网关（唯一 API 入口） | http://localhost:8080 | `BACKEND_PORT` |
| Nacos 控制台 | http://localhost:18848/nacos | `nacos` / `nacos` |

未注册时可用**游客身份**进入，功能完整、数据归属自己；项目列表点「一键体验」会自动跑完
建项目 → 冻结基线 → 生成候选 → 派生一版 → 生成决策。

### 5. 常用命令

```powershell
# 只看某几个服务的日志
docker compose -f docker-compose.dev.yml logs -f core platform

# 改了 synpharm-common / synpharm-api（被多模块依赖）→ 必须重跑一次性构建
cd synpharm-deploy
docker compose -f docker-compose.dev.yml run --rm mvn-setup

# 改了某个服务的代码 → 重启该容器即可（命令是 mvn clean spring-boot:run）
docker restart synpharm-dev-core

# 改了前端 → **必须**重启 dev server（原因见「排障」最后几行）
docker restart synpharm-dev-frontend

# 停止 / 清空数据重来
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml down -v
```

### 6. 本机直跑（不动容器）

```powershell
# 1) 只把基础设施放容器里
docker compose --env-file .env -f synpharm-deploy/docker-compose.dev.yml up -d mysql redis rabbitmq nacos

# 2) 先在根目录安装公共模块，再进模块启动
cd d:\synpharm-workspace
mvn -B -DskipTests install
mvn -B -pl synpharm-gateway spring-boot:run `
  "-Dspring-boot.run.jvmArguments=-DNACOS_SERVER_ADDR=localhost:18848 -DNACOS_DISCOVERY_ENABLED=true -DJWT_SECRET=<见 .env>"
```

> 本机直跑时数据库连 `localhost:15307`、Redis 连 `localhost:16380`（见 `.env`）。

---

## 服务与数据所有权

每个服务**独占一个 schema**，是该 schema 里所有表**唯一**的写入方（拆分铁律 R2）；
跨服务只经 HTTP/Feign，不建跨 schema 外键、不做跨服务 JOIN。

| 服务 | 端口 | schema | 独占的表 / 职责 | 对外前缀 |
|---|---|---|---|---|
| `synpharm-gateway` | 8080 | — | 路由、JWT 验签、身份头注入、追踪 ID、限流预留 | `/api/**` 唯一入口 |
| `synpharm-auth` | 8081 | `synpharm_auth` | `sys_user` `sys_login_log`；注册 / 登录 / 验证码 / 个人中心 / 登录审计 | `/api/auth/**` `/api/users/**` |
| `synpharm-predict` | 8082 | `synpharm_predict` | 预测域 10 张表（`predict_task` `predict_result` `user_favorite` `batch_task` `batch_task_item` `ddi_supported_drug` + 二期 `agent_*`） | `/api/predict/**` `/api/results/**` `/api/favorites/**` `/api/tasks/**` `/api/batch/**` |
| `synpharm-core` | 8083 | `synpharm_core` | 设计域 39 张表：项目 / 靶点 / 约束 / 生成 / 候选 / 编辑 / 评估 / 决策 / 报告 / 湿实验 / 数据资产库 | `/api/design/**` `/api/library/**` `/api/evaluation/**` |
| `synpharm-platform` | 8084 | `synpharm_platform` | `design_tool`；工具注册表 + `@Scheduled` 10 分钟能力探测 | `/api/platform/**` + 内网 `/internal/tools/**` |
| `synpharm-asset` | 8085 | `synpharm_asset` | `file_asset`；内容寻址（SHA256 去重）+ 引用计数 | 内网 `/internal/files/**`（**不经网关**） |

**跨服务调用只有一处**：`core` 的命令面板经 Feign 读 `platform` 的 `/internal/tools/registry`。
契约定义在 [`synpharm-api`](./synpharm-api)，内部令牌与用户身份头由
`InternalTokenFeignInterceptor` 自动注入；`platform` 不可用时 `core` 返回
**503 `DEPENDENCY_UNAVAILABLE`**，而不是静默返回空清单。

> `synpharm` 是一个**空 schema**，仅因 compose 的 `MYSQL_DATABASE` 指向它而保留。
> **不要往里建表** —— 业务表一律归各自的 schema。

---

## 网关

### 路由表（6 条，按声明顺序匹配）

| 路由 id | 目标 | 匹配前缀 |
|---|---|---|
| `auth` | `lb://synpharm-auth` | `/api/auth/**`、`/api/users/**` |
| `core` | `lb://synpharm-core` | `/api/design/**` |
| `core-library` | `lb://synpharm-core` | `/api/library/**` |
| `core-evaluation` | `lb://synpharm-core` | `/api/evaluation/**` |
| `platform` | `lb://synpharm-platform` | `/api/platform/**` |
| `predict` | `lb://synpharm-predict` | `/api/predict/**`、`/api/results/**`、`/api/favorites/**`、`/api/tasks/**`、`/api/batch/**` |

**没有兜底路由**：未声明的 `/api/**` 前缀由网关直接返回带信封的
`404 RESOURCE_NOT_FOUND`，而不是转发到一个没有该端点的服务再报 500。

### 网关做了什么

| 能力 | 说明 |
|---|---|
| **剥离伪造身份头** | 无条件移除客户端传入的 `X-User-Id` / `X-User-Role` / `X-User-Email` / `X-Token-Id` / `X-Internal-Token` |
| **JWT 验签 + 身份头注入** | `IdentityHeaderGlobalFilter` + `JwtVerifier`（HS256，与 auth 同密钥同 claim） |
| **是否拦截** | `synpharm.gateway.auth.enforce`（当前 `.env` 为 `true` —— 网关是唯一鉴权点） |
| **Token 黑名单** | 读 Redis `token:blacklist:{jti}` |
| **追踪 ID** | `X-Request-Id` / `X-Trace-Id` 生成与回写 |
| **CORS** | `synpharm.gateway.cors.enabled`（默认 `false`，避免与下游重复响应头） |
| **限流** | 未实现，规划用 Sentinel |

**各服务不自己验签 JWT**：只信任网关注入的身份头，并由
[`InternalRequestFilter`](./synpharm-common/src/main/java/com/synpharm/common/security/InternalRequestFilter.java)
校验内部令牌 —— 未携带正确 `X-Internal-Token` 时**一律按匿名处理**（fail-closed）。
`/internal/**` 更是只认内部令牌，网关不路由该前缀，公网不可达。

### 公开路径（无需 Token）

`/api/auth/login`、`/api/auth/register`、`/api/auth/captcha/send`、`/api/auth/password/reset`、
`/api/platform/algorithm-health`。

`/api/library/**` 刻意**不在**公开清单里：库里有用户私有数据集，匿名放行会让任何人不带
Token 就能探测「某个 id 是否存在」。

---

## 技术栈与版本上界

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | **3.3.5** | 与已退役单体一致 |
| Spring Cloud | **2023.0.3** | **这是能用的最新版**，上界受 gateway 限制，见下 |
| Spring Cloud Alibaba | **2023.0.3.4** | 父 POM 为 `spring-cloud-dependencies-parent:4.1.0` |
| Nacos Server | **v2.4.3** | 与 Alibaba 内置 `nacos-client 2.4.3` 对齐 |
| JDK | **17** | |
| 前端 | Vue 3 + Vite 5 + TypeScript | |
| 算法引擎 | FastAPI (Python 3.11) | `/v1/**` + `X-API-Key`，不碰数据库 |

### ⚠️ 为什么不能用 Spring Cloud 2023.0.4+

Gateway **4.1.6 起**在 `RemoveHopByHopHeadersFilter` 里调用 `HttpHeaders.headerSet()` ——
这是 **Spring Framework 6.2** 才有的方法，而 Boot 3.3.5 带的是 Framework **6.1.14**。
症状不是启动失败，而是**运行期**抛 `NoSuchMethodError`，表现为网关所有转发请求 500。

| Spring Cloud | Gateway | 与 Boot 3.3.5 |
|---|---|---|
| **2023.0.3** | 4.1.5 | ✅ 可用 |
| 2023.0.4 / 0.5 | 4.1.6 | ❌ 需要 Framework 6.2 |
| 2023.0.6 | 4.1.9 | ❌ 同上 |

要上 2023.0.4+ 必须同时把 Boot 升到 3.4.x（Framework 6.2），那是独立变更。
升级前先验证：`javap -c -classpath spring-cloud-gateway-server-<ver>.jar
org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilter | findstr headerSet`

---

## 开发约定

### 后端

1. **启动类必须写 `@SpringBootApplication(scanBasePackages = "com.synpharm")`** ——
   否则 `synpharm-common` 里的内部身份过滤器 / 全局异常处理器 / `MybatisPlusConfig` 不会被装配。
2. **`spring.application.name` 必须等于** [`ServiceNames`](./synpharm-common/src/main/java/com/synpharm/common/constant/ServiceNames.java)
   里登记的常量（网关的 `lb://` 直接用它）。
3. **不建跨服务外键**，跨域引用只存 id 列（冗余的 `project_id` / `candidate_id` 本来就有）。
4. **`Result.success()` 返回 `code = 0`，不是 `200`**。前端 `request.ts` 已按 `code !== 0` 判错；
   写新的 HTTP 客户端时不要照抄旧习惯。
5. **归属校验失败一律 404**，不用 403 —— 403 等于告诉对方「这个 id 存在」。
6. **改了 `synpharm-common` / `synpharm-api` 必须重跑 `mvn-setup`**（它们的产物通过共享
   `/root/.m2` 分发给各服务）。

### 加入一个新服务模块

1. 在根 `pom.xml` 的 `<modules>` 加一行；
2. 新建模块，`<parent>` 用 `<relativePath>../pom.xml</relativePath>`，依赖 `synpharm-common`；
3. 启动类加 `@SpringBootApplication(scanBasePackages = "com.synpharm")`；
4. `spring.application.name` 同步进 `ServiceNames`；
5. 网关路由表**在兜底之前**插入该服务前缀（本工程已无兜底，但仍应显式声明）；
6. 两套 compose 加服务；
7. 新 schema 建独立账号，只授该 schema 权限；
8. **同步 Dockerfile 的 COPY 列表**（所有模块的 `pom.xml` 都必须复制，否则 Maven 校验
   `<modules>` 时因目录缺失整体失败）+ 更新 `.dockerignore` 时别把新模块挡掉。

### 前端

- 下拉选择一律用 [`SpSelect.vue`](./synpharm-frontend/src/components/ui/SpSelect.vue)
  （自绘箭头与展开面板，键盘可达、teleport 到 body）。**不要写原生 `<select>`** ——
  它的箭头与选项列表由操作系统绘制，无法贴合主题。
- `SpSelect` 的宽度用 props 表达（`block` / `width` / `minWidth`），**不要用页面里 scoped
  的宽度类**：组件是多根节点（按钮 + Teleport），Vue 只给根节点打自己的 scopeId，
  父组件的 scopeId 不会落到那个 button 上，`父页面类[data-v-parent]` 永远匹配不到。
- 按钮 / 横幅 / 标签等跨页控件统一取 [`styles/base.scss`](./synpharm-frontend/src/styles/base.scss)
  的全局类。

---

## 端到端验证

### 后端单元测试（528 个）

```bash
mvn -o test                       # 全模块
mvn -o -pl synpharm-core test     # 单模块（425 个）
```

| 模块 | 用例数 |
|---|---|
| `synpharm-core` | 425 |
| `synpharm-predict` | 59 |
| `synpharm-platform` | 22 |
| `synpharm-gateway` | 17 |
| `synpharm-api` | 5 |
| `synpharm-auth` / `synpharm-asset` | 0（无测试） |

### 链路验证（14 套 / 410 项断言）

每个脚本都用**真实接口**跑完整链路，打印 `PASS / FAIL` 与最终的 `RESULT pass=N fail=0`：

| 脚本 | 断言 | 覆盖 |
|---|---|---|
| `e2e-baseline.ps1` | 59 | 需求基线八块与冻结 |
| `e2e-decision.ps1` | 50 | 决策 D0~D8：证据 / 否决 / 分桶 / 名额 / 调整 / 风险接受 / 确认 / 回放 |
| `e2e-edit.ps1` | 47 | 缺口诊断 / 动作匹配 / 影响面 / 版本树 |
| `e2e-generation-library.ps1` | 42 | 库选种子、分面筛选、跨批次去重 |
| `e2e-generation.ps1` | 37 | 生成漏斗与逐分子去向 |
| `e2e-decision-ui.ps1` | 36 | 决策页每个取数与写操作 |
| `e2e-baseline-edit.ps1` | 34 | 靶点绑定 / 解绑、约束集新建与激活、风险预设 |
| `e2e-evaluation.ps1` | 21 | 指标目录、DDI 白名单、候选预测回写（幂等 / 越权 404） |
| `e2e-candidate-version.ps1` | 20 | 画布改结构 → 版本树新增一版 → 重新评估 |
| `e2e-baseline-board.ps1` | 16 | 基线看板八块状态与缺项直达 |
| `e2e-target-library.ps1` | 14 | 靶点 → 结构 → 口袋三层数据链 |
| `e2e-workbench.ps1` | 13 | 分子精修台：生成 → 选候选 → 派生 |
| `e2e-canvas-candidate.ps1` | 11 | 二维画布 → 候选 → 评估冻结 → 进决策 |
| `e2e-new-project.ps1` | 10 | 建项目全链路与幂等 |

```powershell
.\e2e-decision.ps1     # 任选一个跑
```

> 这些脚本会**真实写库**（新建项目 / 候选 / 决策），适合开发环境，不要指向生产。

---

## 排障

| 现象 | 原因 / 处理 |
|---|---|
| 网关 `503`，日志 `Unable to find instance for synpharm-xxx` | 服务没注册上。检查其 `NACOS_DISCOVERY_ENABLED=true` 与 `NACOS_SERVER_ADDR` |
| `core` 的 `/api/design/commands` 返回 `503 DEPENDENCY_UNAVAILABLE` | `synpharm-platform` 没起或 Nacos 里没有它。这是**刻意**的：宁可报错也不返回空清单（空清单会让人误以为引擎缺能力） |
| 启动报 `JWT 密钥长度不足` | `JWT_SECRET` 缺失或不足 32 字节。网关与 auth 必须用**同一个**（fail-fast 是刻意设计） |
| 启动报 `spring.config.import must contain a nacos: entry` | 类路径上有 nacos-config 但没写 import。各服务已在默认 profile 设 `import-check.enabled=false`，新增模块请照抄 |
| 运行期 `NoSuchMethodError: HttpHeaders.headerSet()` | Spring Cloud ≥2023.0.4 的 gateway 4.1.6+ 需要 Framework 6.2。**只能用 2023.0.3** |
| 端口报 `access a socket in a way forbidden` | 落在 Windows 的 Hyper-V/WSL 保留段。`netsh interface ipv4 show excludedportrange protocol=tcp` 查，改用 15000 以上的端口 |
| 改前端**看不到任何变化** | **Windows 下 Docker bind mount 不传递文件变更通知**，Vite dev server 不会重载。`vue-tsc` 0 错、`vite build` 通过、模块请求 200 **都发现不了**。判定：比对 `docker exec synpharm-dev-frontend wc -c /app/src/xxx.vue` 与 `http://localhost:15174/src/xxx.vue` 的字节数；处理：`docker restart synpharm-dev-frontend`（20~40s） |
| 容器里所有 Java 服务起不来 / `Child module ... does not exist` | 根 `pom.xml` 的 `<modules>` 与模块目录不一致，或 **Dockerfile 的 COPY 列表漏了某个模块的 `pom.xml`** |
| 中文写进库变成 `?` 或 `å°åˆ†å­` | 两回事：`?` 是 PowerShell `Invoke-RestMethod` 按系统代码页编码 JSON 造成（**不可逆**，只能用 `.sql` 文件 + `--default-character-set=utf8mb4` 重造）；`å°åˆ†å­` 是历史上「UTF-8 当 latin1 再存一次」的双重编码（重跑种子脚本可修） |
| 分子画布（Ketcher）白屏 / iframe 里出现整个主应用 | `public/ketcher/` 产物不存在。Vite 的 SPA fallback 对缺失路径**返回 index.html 而非 404**，iframe 就把主应用装了进去。处理：`npm run build:ketcher`，然后重启 dev server |
| PowerShell 向无 `@RequestBody` 的 POST 传 `$null` 报 `Content-Type 'application/x-www-form-urlencoded' is not supported` | PS 5.1 无 Body 时默认带 form-urlencoded 头。写脚本时传 `@{}` 而不是 `$null` |
| 想查「中文被双重编码」却写 `WHERE col LIKE '%å%'` | **危险**：`utf8mb4_0900_ai_ci` 下 `'å' LIKE 'a'` 为真，会命中所有含英文字母的字符串，把正确的中文也转成不可逆的 `?`。正确判定需两条同时成立：① `latin1→binary→utf8mb4` 还原后与原值字节不同；② 还原结果不含 `?` |

---

## 安全说明

- **口令不入库**：`.env` 全被 `.gitignore` 排除，只保留 `*.env.example` 模板。克隆后请生成新值。
- **构建产物不入库**：`node_modules/`、`target/`、`public/ketcher/`（约 30 MB 化学引擎）、`dist/`。
- **本地备份不入库**：`_synpharm-*.zip` / `_synpharm-*.sql` 是迁移前的本地安全副本，含数据库转储。
- **机密不进镜像**：`.dockerignore` 排除 `**/.env`；构建上下文是仓库根，
  非 Java 服务（前端 715 MB / `.git` 120 MB）也已排除，避免每次构建传输。
- **归属校验 fail-closed**：所有服务在内部再判一次归属，不依赖「请求来自网关」这个假设。

---

## 已知边界

- **需求基线是「可补齐」而非「可任意编辑」**：分子形式在建项目时确定，基线页只读；
  结构来自绑定靶点自带的 PDB，页内不导入结构。靶点、约束、指标、风险点可在补齐向导里直接改
  （约束的保存语义是**新建一版再激活**，不改写已激活的历史版本）。
- **序列类分子**（抗体 / 多肽）：计算层目前只提供小分子理化指标，画布与版本写回也仅适用于小分子。
- **自动编辑动作**（按缺口推荐「加取代基」并自动生成结构）依赖 `crem` 引擎，尚未部署；
  当前是人在画布上改、系统负责把结果合法地记进版本树。
- **阶段 7（湿实验回流）与阶段 8（真编辑引擎接入）尚未开始。**
- **预测域与设计域尚未打通**：评估冻结的 `weight_coverage` 目前只有 33.3%（小分子 10 个指标里
  只有 QED / LIPINSKI_PASS / PAINS_HITS / BRENK_HITS 能算，权重 0.35 / 1.05）。
  剩下 6 个里有 4 个的语义本来就是预测域的产物：

  | 指标码 | 权重 | 应该来自 |
  |---|---|---|
  | `DTI_CONFIDENCE` | 0.30 | DTI 预测置信度 |
  | `SELECTIVITY_INDEX` | 0.10 | 靶点 vs 反靶点的 DTI 差值 |
  | `VINA_AFFINITY` | 0.10 | 对接打分（需要口袋，即基线块 2） |
  | `DDI_PROBABILITY` | 0.05 | DDI 预测概率 |

  这 4 个能回填的话覆盖率从 33.3% 升到 **85.7%**。最小闭环：`core` 加内部端点读 `predict`
  既有结果 → 新增映射表 `metric_prediction_source`（口径放表里而非代码里，才可解释）→
  只在**评估冻结**时拉取（不进生成阶段，否则毫秒级流水线变分钟级），取不到就记进 `missingMetrics`
  并把 `realness`（REAL / SIMULATED）写进 `scores[].note`。
  **明确不做**：不让预测值绕过硬约束；不在候选排序里偷偷混入预测分。

---

## 文档索引

| 文档 | 内容 |
|---|---|
| [`synpharm-deploy/docs/architecture/`](./synpharm-deploy/docs/architecture) | 服务拆分方案、架构设计、[微服务拆分执行记录](./synpharm-deploy/docs/architecture/微服务拆分执行记录.md) |
| [`synpharm-deploy/docs/design/detailed/`](./synpharm-deploy/docs/design/detailed) | 数据层 / 接口层 / 实现级详细设计 + 三阶段业务设计（需求分析 / 生成 / 编辑 / 决策 / 评价体系 / 数据资产库） |
| [`synpharm-deploy/docs/conventions/`](./synpharm-deploy/docs/conventions) | 接口与错误码 / 数据与配置 / 事务消息与安全 / 工程规范 |
| [`synpharm-deploy/docs/deploy/`](./synpharm-deploy/docs/deploy) | 部署与容器化指南、上线流程 |
| [`synpharm-deploy/docs/development/`](./synpharm-deploy/docs/development) | 问题总账、未修复功能清单、开发规范 |
| [`synpharm-deploy/docs/uml/`](./synpharm-deploy/docs/uml) | 业务流程与设计域 UML（Mermaid 内嵌） |
| [`synpharm-frontend/README.md`](./synpharm-frontend/README.md) | 前端构建与页面说明 |
| [`synpharm-fastapi/README.md`](./synpharm-fastapi/README.md) | 算法引擎接口与模型 |
| [`synpharm-deploy/README.md`](./synpharm-deploy/README.md) | 编排文件与脚本说明 |

---

## 修订记录

> 完整技术决策与逐阶段实施记录见
> [SpringCloud 微服务重构方案](./synpharm-deploy/docs/architecture/SpringCloud微服务重构方案.md) 的修订记录。

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0.0 | 2026-10-14 | 首版：聚合工程 + common + gateway；Nacos 接入；前端代理改指网关 |
| v1.1.0 | 2026-10-14 | 阶段 1：auth 从单体迁出；`ErrorCode` 收敛为单一枚举；成功码统一为 `0` |
| v1.2.0 | 2026-10-14 | 阶段 2A：design 模块与 schema；删 6 条跨 schema 外键 |
| v1.2.1 | 2026-10-14 | 阶段 2B：知识库 / 约束集 / 项目 / 轮次 27 个端点 |
| v1.3.0 | 2026-10-14 | 阶段 3：asset 独立（内容寻址）；阶段 5：platform 独立（工具注册表 + 能力探测） |
| v1.5.0 | 2026-10-15 | 阶段 4：predict 切流；单体预测域下线 |
| v1.7.0～v1.10.0 | 2026-10-17 | 设计域 2B 各切片 + 技术债收口（H-25 / H-26 / H-27） |
| v1.12.0 | 2026-10-19 | 单体源码目录删除（零端点后 3 天）；`.idea` / 脚本 / 编排引用同步 |
| v1.13.0 | 2026-10-22 | **服务收敛**：design + asset + platform 合并为 `synpharm-core`（21 表 / 单库单账号） |
| **v2.0.0** | **2026-11-04** | **服务拆分做到底**：① platform / asset 重新独立成服务（各占一个 schema）；② 新增 `synpharm-api` 承载 Feign 契约，内部令牌头自动注入；③ 路径收敛为 `/api/platform/**`，旧的 `/api/design/tools\|capabilities`、`/api/system/**` 变 404；④ 设计域命令面板改 Feign，platform 停机返回 **503** 而非空清单；⑤ **Java 服务扁平化到仓库根**，根 `pom.xml` 即多模块聚合，`.dockerignore` 相应重写。实测：9 模块编译通过、单测 528、Nacos 6 服务、14 套 e2e 410 项全绿 |
