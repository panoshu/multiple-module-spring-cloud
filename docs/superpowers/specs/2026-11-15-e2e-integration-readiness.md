# 全链路集成测试执行准备（批次 73）

> 生成日期：2026-11-15（批次 74 更新：真实联调已执行，进展与剩余根因见 §9）
> 目标：启动网关 + 双 BFF + auth/annuity/approval/file 共 7 个服务，执行端到端集成测试（对应清单 #12「端到端 HTTP 验证」的运行时部分）
> 当前状态：**联调进行中** —— 已打通至 L3 登录/鉴权链，L4 卡在网关会话注入的 sa-token reactor 限制（§9）

---

## 9. 批次 74：真实联调进展与剩余根因（2026-11-15）

### 9.1 已打通（实测验证）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 7 服务启动 | 全部 fat jar（`spring-boot.run.skip=false` 后产出） | ✅ 7 端口全部监听 |
| PG 建表 | kernel `schema-pg.sql` 执行 | ✅ `t_business_*` 6 张表建成 |
| 登录 | `POST /internet/auth/login`（渠道前缀路径 + channel 传 CodeEnum code `"01"`） | ✅ 返回 token（tokenName=`satoken-internet`，NETAPP→internet StpLogic） |
| 可选计划 | `POST /internet/auth/selectable-plans` + `satoken-internet` header | ✅ HTTP 200（鉴权链通；返回空计划的根因见 9.3-⑤） |
| BFF→annuity | httpexchange 3.5.5 配置迁移后 | ✅ 请求到达 annuity（此前 LB 报「无有效 hostname」） |

### 9.2 本轮修复的缺陷（均已提交）

1. **auth 权限自注册 NPE**（启动即挂）：`PermissionItemRepositoryImpl.save` 对 reconstitute（无 version）实体调用 `syncVersionAfterUpdate`；改为以 DB 现有 version 更新（`3682a82f`）。
2. **MyBatis-Flex 逻辑删除处理器**：默认 Integer 处理器自动追加 `deleted = 0`，真 PG 上报 `boolean = integer`（kernel 恢复任务持续报错、auth/file 同类风险）；注册 `BooleanLogicDeleteProcessor`（kernel/auth/approval 三处，file 为独立 Integer 体系不动），H2 DDL `deleted INT` 同步改 BOOLEAN（`8427bf6a`）。
3. **httpexchange 3.5.5 配置格式**：旧 `httpexchange.clients.<pkg>.url` **完全无效**（正确前缀是 `http-exchange`，结构为全局 `base-url` + `channels[]`）；internet-bff 已迁移并实测打通（其余 5 服务待迁移，见 9.4-②）。
4. **网关 dev 路由三处**：`/auth/**` 误剥 `/auth` 前缀致 404；BFF 路由缺 context-path（PrefixPath=/internet-bff）；缺渠道前缀直通业务服务路由（`/internet/annuity|file|approval/**`）。
5. **annuity SanitizationProperties 强制校验**（首次真实启动触发）：主配置补最小合法脱敏配置。
6. **SessionContextInjector 静默吞异常**：两个跳过分支加 warn 诊断日志（正是它暴露了 9.3-①）。
7. **打包**：8 个启动模块 `spring-boot.run.skip=false` 后全部产出可执行 fat jar；annuity `httpclient5` test→runtime（真实启动 NoClassDefFoundError）。

### 9.3 剩余根因（L4 业务链的三道闸门）

| # | 根因 | 证据 | 修复方向 |
|---|---|---|---|
| ① | **sa-token 1.45 reactor 上下文限制**：`SaTokenContextForThreadLocal` 仅在 `SaReactorFilter` 的 setAuth 回调期间绑定；`SessionContextInjector`（SCG GlobalFilter，order -150）在其后执行时调用 `stpLogic.getLoginIdAsString()` 抛 `SaTokenContext 上下文尚未初始化`（原被静默吞掉）→ `X-Account-Id`/`X-Session-Context` **从未注入** → auth `currentAccountId()` 与 kernel 会话校验全部「未登录」 | gateway 日志：`渠道会话读取异常... channel=INTERNET`；annuity：`X-Session-Context header 缺失或验签失败` | 注入器改用 **ReactiveStringRedisTemplate 直读 sa-token 存储**（`satoken-internet:internet:login:token:<token>` → loginId；token-session JSON → 上下文字段），签名逻辑 `applySignedHeaders` 可复用；或把注入逻辑并入 SaReactorFilter 回调内 |
| ② | **网关 setAuth 第 3 分支用默认 StpLogic**：非渠道前缀路径（如 `/auth/**`）走 `StpUtil.checkLogin()`（默认 satoken 类型），多渠道 token（satoken-internet）不被识别 → auth 管理接口经网关全部 401（业务码 0002） | 代码：`SaTokenGatewayConfiguration` setAuth 第 3 分支 | 改为遍历本网关启用渠道逐个 `checkLogin`（与 resolveLoginChannel 同思路）；或 auth 管理路径统一挂渠道前缀 |
| ③ | **BFF→annuity 会话头透传缺失**：httpexchange 3.5.5 仅支持静态 headers，无请求级动态透传；BFF 转发后 annuity 收不到签名头 | annuity 日志：`X-Session-Context header 缺失`；internet-bff 500 | 该库不支持 → 需在 BFF 侧加透传拦截器（从 RequestContextHolder 复制签名头），或 BFF 场景改由前端直调 + BFF 仅做无会话聚合 |
| ④ | **其余 5 服务的 httpexchange 配置待迁移**：auth/file/approval/annuity 的 local 配置仍是无效旧格式 → **权限校验链（PermissionCheckApi→auth）在真环境同样不生效** | 同 ② 的库结构分析 | 按 3.5.5 格式批量迁移（`http-exchange.channels[].base-url + clients[]`） |
| ⑤ | **可选计划为空**：assignment 命中（Total:1）但渠道过滤依赖 `ProductGateway.findPlan`；本地已启用 InMemoryProductGateway（`auth.org-directory.enabled=true`，计划数=2）后需重验（此前验证被 ①②干扰：select-plan 未成功） | auth 日志：Stub→InMemory 切换日志已出现 | ①②修复后重验；若仍空则查 ChannelAccessPolicy 过滤逻辑 |

### 9.4 下一步顺序

1. 修复 ①（注入器 Redis 直读版）——解锁 X-Account-Id/X-Session-Context 全链
2. 修复 ②（setAuth 渠道遍历）——解锁 auth 管理接口经网关访问
3. 批量迁移 ④ 的 5 个服务 httpexchange 配置——解锁权限校验链（@RequirePermission→auth）
4. 重验 ⑤ → select-plan 成功 → 重跑 L4 全链路（建批次→上传→解析→推进→审批）
5. ③ 作为设计缺口立项（BFF 会话透传）

### 9.5 测试环境快照

- 服务进程均为脱离会话信号的方式启动（setsid），日志 `/tmp/svc-<name>.log`
- 测试账号：U1001（密码已重置为 `Test123456`，原 BCrypt hash 备份：`$2a$10$btaFmHvMsMnonYKu2aHjZuRisuOk9ujYIlJoQO97eLKla23VLcufC`）
- 测试数据：`t_auth_assignment` 新增 `asg-e2e-0001`（U1001/AGENT_REVIEW/PLAN=P-1）
- 登录契约要点：channel 传 CodeEnum code（NETAPP→`"01"`）；NETAPP 渠道必须走 `/internet/**` 前缀（sa-token StpLogic 按路径前缀路由）
- 已知工具坑：`pgrep -f`/`pkill -f` 会匹配自身命令行（三次误杀），统一改用 `ps -C java` 取 pid；zsh 不分词未引用变量、`>` 受 noclobber 限制需 `rm -f` 先行

---

## 1. 启动方案（已就绪）

- 脚本：`scripts/local/start-all-services.sh` —— 按依赖顺序启动 7 个服务，每服务等待端口就绪后继续，进程日志写 `/tmp/svc-<name>.log`，pid 写 `/tmp/svc-<name>.pid`
- 顺序与端口：

| 顺序 | 服务 | 端口 | 启动参数 |
|---|---|---|---|
| 1 | auth-service | 18085 | （主配置 `include: local` 自动加载本地配置） |
| 2 | file-service | 18082 | 同上 |
| 3 | approval-service | 18081 | 同上 |
| 4 | annuity-service | 18090 | `--spring.profiles.active=local`（批次 47 治理：不无条件 include local） |
| 5 | intranet-bff | 18091 | 同 auth |
| 6 | internet-bff | 18092 | `--server.port=18092`（默认 18090 与 annuity 冲突，按实例覆盖） |
| 7 | channel-gateway | 18080 | `--spring.profiles.active=dev`（dev 实例为三渠道联调入口） |

- jar 产出：8 个启动模块 pom 已加 `spring-boot.run.skip=false`（覆盖父 pom 的 true），`mvn clean install -DskipTests` 产出可执行 fat jar；integration-starter 原本已有该配置
- kernel 侧能力已随本次构建生效：Redis 分布式锁（启动期 PING fail-fast）、MDC 流程标识（`traceId/applicationId/stepExecutionId/requestNo/businessType`）随 annuity 的 logback-spring.xml 输出

## 2. 当前阻塞：基础设施下线

2026-11-15 10:16 探测结果（对比批次 72 时三者全通）：

| 依赖 | 地址 | 状态 |
|---|---|---|
| Redis | 127.0.0.1:6379 | ❌ CLOSED（auth 启动即失败：sa-token/jetcache remote 连接被拒） |
| PostgreSQL | 127.0.0.1:5432/pgdb | ❌ CLOSED |
| Nacos | 127.0.0.1:8848 | ❌ CLOSED |

- WSL 内无 redis/postgresql/nacos 安装、无 docker；Windows 宿主侧（172.22.208.1）端口同样未监听
- **需恢复原有实例**（PG 的 `schema_demo` 已含 48 张表 + 业务数据；若换新实例需重建表与数据，见 §4）

## 3. 数据库就绪状态

`schema_demo` 已建表 48 张，包括 kernel 6 张（批次 72 前由本会话执行 `business-core-infrastructure/schema-pg.sql` 建成）：

- `t_business_application` / `t_business_batch` / `t_business_form` / `t_business_step_execution` / `t_business_external_interaction` / `t_business_event_receipt`
- auth 全套 `t_auth_*`、approval 5 张、file 5 张、annuity 4 张 + `t_id_generator` + 事件存储 2 张

若恢复的是原实例则直接可用；换新实例需按各服务 `schema-pg.sql` 重放 DDL 并准备业务数据。

## 4. 全链路测试用例设计（环境恢复后执行）

| 层 | 用例 | 验证点 |
|---|---|---|
| L1 活性 | 7 个端口连通 + 各服务日志无 ERROR；权限自注册日志（`PermissionScanner` / `PermissionRegistrationRunner` 上报成功）；锁装配日志 `Initialized Lock4jDistributedLock` | 服务健康、权限项自注册、分布式锁真实生效 |
| L2 网关路由 | 经 `channel-gateway:18080` 分别路由 auth（`/auth/**`）、annuity（`/annuity/**`）、approval（`/approval/**`）、file（`/file/**`）、两个 BFF | Nacos 发现 + 路由转发 |
| L3 权限链 | auth 登录（`t_auth_user` 既有账号）拿 token → 带 token 经 intranet-bff 调 approval 查询接口 | 登录 → 会话 → `@RequirePermission` 实时校验 → 返回数据 |
| L4 业务链 | 经 internet-bff/gateway：创建批次 → 申请上传 token → 上传 Excel → 确认上传触发解析 → kernel 裂变申请单 → 自动推进至 USER_TASK 挂起 → intranet-bff 发起审批 → 审批通过 → kernel 回调推进 | kernel↔file↔approval 全链路（requestNo 幂等、回调关联、事件推进、审批人解析 SAME_PLAN/LEVEL_UP） |

前置数据需求：`t_auth_user` 可登录账号、`t_file_template_config` 活跃模板、`t_approval_flow` 匹配规则与节点（若原实例数据保留则已具备）。

## 5. 维护约定

- 环境恢复后：直接 `bash scripts/local/start-all-services.sh`，按 §4 逐层执行并在本文回填结果
- 服务停止：`for f in /tmp/svc-*.pid; do kill $(cat $f); done`
- 已知启动坑（均已修复）：annuity `httpclient5` test scope（运行时 NoClassDefFoundError）→ runtime；internet-bff 与 annuity 端口冲突 → 启动参数覆盖
