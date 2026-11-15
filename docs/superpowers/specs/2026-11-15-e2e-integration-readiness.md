# 全链路集成测试执行准备（批次 73）

> 生成日期：2026-11-15（批次 74 更新：真实联调已执行，进展与剩余根因见 §9）
> 目标：启动网关 + 双 BFF + auth/annuity/approval/file 共 7 个服务，执行端到端集成测试（对应清单 #12「端到端 HTTP 验证」的运行时部分）
> 当前状态：**服务层与网关入口均已打通** —— 7 服务启动、网关登录、internet-bff 批次详情/聚合链路通过（§10）；§9.3 的网关会话问题已在本轮修复

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

- 脚本：`scripts/local/start-all-services.sh` —— 按依赖顺序启动 7 个业务服务 + integration（共 8 个），每服务等待端口就绪后继续，进程日志写 `/tmp/svc-<name>.log`，pid 写 `/tmp/svc-<name>.pid`
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

---

## 6. 批次 75：服务层全链路真实联调成功（2026-11-15 19:36）

> 目标：去掉 annuity 的文件侧 Mock，真实调用 file-service 解析 Excel，并打通
> file → kernel → approval → kernel 的跨服务闭环。网关/BFF/auth 会话链（§9.3 ①②③）
> 仍未修复，本轮在 **服务端口直连 + 本地关闭权限切面** 的前提下验证业务主链。

### 6.1 测试拓扑

| 服务 | 端口 | 启动参数 | 状态 |
|---|---|---|---|
| file-service | 18082 | `--spring.profiles.active=local --permission.enforcement.enabled=false` | ✅ |
| approval-service | 18081 | 同上 | ✅ |
| annuity-service | 18090 | 同上 | ✅ |
| 基础设施 | PG 5432 / Redis 6379 / Nacos 8848 | Docker | ✅ |

- 三者均需 `--enable-preview`（`VirtualThreadExecutor` 使用 JDK 25 preview API；缺失时首次异步派发即 `UnsupportedClassVersionError`，见 `scripts/local/start-all-services.sh` 已补）。
- file-service 本地启用 `file.demo.template-seed.enabled=true`，启动时自动播种 `FORM_DETAIL` 模板（幂等，已有 ACTIVE 配置则跳过）。
- approval-service 本地新增 `spring-boot-starter-data-redis` 依赖，审批结果事件才能经 Redis 到达 kernel。

### 6.2 实测证据（最终成功批次）

| 步骤 | 关键值 / 结果 |
|---|---|
| 年金表单创建 | batch `20261115000081`，form `2026111501M7FFWA9ABADHAFA77P8Z5WHX`，`WAITING_UPLOAD` |
| 上传 token + 直传 | annuity `upload-token` → file-service `ApplyUploadTokenResponse` → `POST /api/file/access/upload` 返回 fileId `2026111501M7FFWADSRVV72RJ8F49F15V6` |
| 确认真实文件 | annuity `confirm-upload` 成功；FormUploadedEvent AFTER_COMMIT 触发解析 |
| file-service 解析 | 真实 Excel（`docs/excel/annuity_form_detail.xlsx`）：`SUCCESS`，1 个 subTask，splitKey=`PLAN-FINAL-193600`，2 行有效 |
| kernel 裂变 | `FileParsedEventDTO` 经 Redis 到达 annuity；创建 application `2026111501M7FFWAQSJCFKPVMR7SAJFPAY` |
| 明细摄入 | `AnnuityDetailIngestionAction` 通过 `fetchParsedRows` 分页拉取，员工明细 2 行入库 |
| 自动推进 | DATA_VERIFICATION → MATERIAL_PREPARATION → APPROVAL（USER_TASK 挂起） |
| 提交审批 | `/annuity/core/application/advance`：interaction `11`，requestNo `SE-54`，状态 `WAITING_CALLBACK` |
| 审批通过 | approval instance `61834878789877760`，`APPROVED`；事件 `ApprovalInstanceApprovedEventDTO` 经 Redis 到达 annuity |
| 回调闭环 | kernel `completeWaitingStep`：application `COMPLETED/COMPLETED`，interaction `SUCCEEDED`，batch `COMPLETED`，successCount=1 |
| 员工批次 | `t_annuity_employee_batch`：total=2，processed=2；明细 2 行 |

### 6.3 本轮修复的缺陷（按链路顺序）

1. **file-service 启动序列化崩溃**：`SourceTemplateDef`/`RegionStrategy` 无 Jackson 契约 → 新增 `TemplateConfigJson`（Map 契约 + sealed 接口多态 mix-in），模板配置可写库/读库。
2. **模板配置路由规则未持久化**：`TemplateConfigConverter.toDO` 漏映射 `routingRules` → 已补；`ApplyUploadTokenUseCase` 的 FORM 预检可命中。
3. **TaskSplitter 不支持属性级 splitKey**：`sourcePath` 无 `.` 时永远返回 `default` → 改为按属性值拆分（`planNo`）。
4. **Excel 模板播种缺失**：新增 `DemoTemplateSeeder` + `form_detail_base.yaml` / `form_detail_source_annuity.yaml`；本地自动准备 `FORM_DETAIL` 模板。
5. **上传 token 链路断裂**：
   - kernel `applyUploadToken` 只允许 PDF/图片 → 补 xlsx/xls/csv；
   - 请求未携带 batch/customer/product → 网关接口新增带业务上下文重载，controller 从 SessionClaims 透传；
   - file-service `FileAccessConverter` 对空 batch/scope 直接构造值对象抛 500 → 改为可空并明确拒绝。
6. **file-service 访问流水写入失败**：`t_file_access_log.deleted` 为 boolean，DO 为 Integer → DO 改 Boolean。
7. **文件元数据更新静默丢失**：`completeUpload` 先 `markUpdated` 再保存，DO 版本已+1 而 DB 仍为旧值 → `FileMetadataRepositoryImpl.save` 对齐 kernel 的版本回退/校验模式；并新增 `prepareUpload` 在写存储前登记原始文件名（避免 storageKey 出现字面量 `null`）。
8. **区域解析器未装配**：`RegionStateMachine` 的 `Map<RegionType, RegionParser>` 无法被 Spring 按枚举键注入 → 移除 `@DomainService`，在 `FileDomainServiceConfiguration` 显式 `@Bean` 装配。
9. **file-service 解析完成事件自激循环**：Repository 用 Spring publisher 发布 → `FileDomainEventBridge` 再入 EventBus → `SpringEventDispatcher` 又本地发布同一领域事件 → 无限重复/`sys_event_store` 主键冲突。改为 Repository 直接 `eventBus.publish`，删除冗余 Bridge。
10. **跨服务 Redis 事件反序列化失败**：record 是 final class，`DefaultTyping.NON_FINAL` 不写 `@class` → 改 `EVERYTHING`，`FileParsedEventDTO`/审批事件才能跨进程还原。
11. **JDK25 preview 启动缺失**：本地启动脚本补 `--enable-preview`；否则 `VirtualThreadExecutor` 抛 `UnsupportedClassVersionError`。
12. **annuity 主 ObjectMapper 不兼容**：裸 `ObjectMapper` 缺 JavaTimeModule 且 `FAIL_ON_UNKNOWN_PROPERTIES` 开启 → HTTP 客户端无法序列化 `expiresAt`、无法反序列化 `ApiResult` 的 `success/failure` 派生字段。改为 `findAndRegisterModules + disable(FAIL_ON_UNKNOWN_PROPERTIES)`。
13. **annuity JSONB 写入失败**：`t_annuity_employee_detail.materials` 为 jsonb，JDBC 以 varchar 写入 → 本地 JDBC URL 补 `stringtype=unspecified`。
14. **kernel 锁模板误报失败**：`Lock4jDistributedLock.lockAndRun` 用 `Optional.map`，任务返回 null（`advanceStep`）时 map 塌缩为 empty，任务成功执行并释放锁后仍抛「获取锁失败」→ 改显式 `isEmpty` 判断 + try-with-resources；新增回归测试。
15. **审批流加载崩溃**：`ApprovalFlowConverter.toDomain` 先以空节点重建聚合，违反“节点非空”不变量 → 增加带节点列表的一次性重建；`ApprovalFlowRepositoryImpl.load/findByStatus` 统一走该路径。
16. **审批流创建 500**：
    - `ApprovalFlowConverter` ObjectMapper 缺 Jdk8Module（MatchRules 含 Optional）→ `findAndRegisterModules`；
    - `t_approval_flow.business_type` NOT NULL 但转换器 ignore → 从 matchRules 提取落库；
    - Snowflake Long ID 被默认 `%p%d%s` 二次格式化溢出 → `LongTypeHandler` 对 SNOWFLAKE 直接返回原始 Long。
17. **审批实例 requestNo 丢失**：`convertToInstanceWithDetails` 二次重建未透传 requestNo → 审批结果事件 requestNo 为 null，kernel 无法按协作键关联。已补。
18. **审批实例/节点/记录更新静默丢失**：与文件元数据同类的“领域版本已+1、DO WHERE 版本不匹配”问题 → 三处保存统一回退 DO 版本并校验更新行数。
19. **审批结果事件不跨服务**：approval 仓储用 `ApplicationEventPublisher`（仅本进程）+ starter 缺 Redis 依赖 → 改 `EventBus` + 补 `spring-boot-starter-data-redis`。
20. **DB 结构漂移**：旧库缺 `t_file_parse_task.request_no`、`t_approval_instance.request_no` → 两个 `schema-pg.sql` 增加幂等 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`；本轮已在本地库执行。
21. **fat jar 异常日志反吞异常**：annuity 自定义 logback 使用 `%wEx`，Tomcat/JULI 线程上 packaging data 计算偶发 `NoClassDefFoundError(ClassPackagingData)`，导致 batch/detail 请求挂起 → 改 `%rEx`（不计算 packaging data），batch/detail 恢复正常。
22. **表单上传伪 FileId**：`AnnuityAppService.uploadForm` 旧实现用 `idService.nextId(FileId)` 伪造文件并挂到表单，解析必然读不到文件 → 改为先创建 `WAITING_UPLOAD` 空表单，真实文件走 confirm-upload；年金 planType/initialContribution 存入 `AnnuityFormDraftStore`，裂变时由 `ApplicationSpawnInitializer` SPI 复制到申请单维度。

### 6.4 剩余风险 / 后续项

| # | 风险 | 建议 |
|---|---|---|
| R1 | 网关/BFF/auth 会话链仍被 §9.3 ①②③ 阻塞，本轮未走浏览器入口 | 按 §9.3 修 `SessionContextInjector` Redis 直读 + setAuth 渠道遍历 + BFF 头透传后重跑 L1-L4 |
| R2 | annuity 的 `AnnuityFormDraftStore` / `AnnuityApplicationExtensionStore` 仍是进程内 Map | 生产改为 DB 聚合（按 formId/applicationId 持久化），否则重启/多实例丢扩展字段 |
| R3 | 审批流 `business_type` 与 `matchRules` 冗余；`ApprovalNodeDTO.nodeType` 创建时用 `ApproverType`、查询时回填 `NodeType`，往返不一致 | 拆分为 `approverType`/`nodeType` 两个字段，补契约测试 |
| R4 | file-service `dispatchParsing` 仍把 fileName 写成 `<fileId>.json`、fileSize=0（仅元数据展示问题） | 由 kernel 网关把表单真实 fileName/size 传入 |
| R5 | 旧库缺列靠 `ALTER TABLE IF NOT EXISTS` 手工补 | 引入 Flyway/Liquibase，按版本管理各服务 schema |
| R6 | `%rEx` 仅加在 annuity logback | 其他 fat jar 若出现同类 `ClassPackagingData` 报错，统一改用 `%rEx` |
| R7 | 本地联调通过 `--permission.enforcement.enabled=false` 绕过权限 | 仅限本地；生产必须保持默认开启（`PermissionReadinessValidator` fail-fast） |

### 6.5 复跑命令（服务层）

```bash
# 构建 & 启动（三个服务，均带 preview 与本地权限关闭）
mvn -q -DskipTests install
java --enable-preview -jar file-service/file-starter/target/file-service.jar \
  --spring.profiles.active=local --permission.enforcement.enabled=false &
java --enable-preview -jar approval-service/approval-starter/target/approval-service.jar \
  --spring.profiles.active=local --permission.enforcement.enabled=false &
java --enable-preview -jar annuity-service/annuity-starter/target/annuity-service.jar \
  --spring.profiles.active=local --permission.enforcement.enabled=false &

# 表单创建 → upload-token → file-service 直传 → confirm-upload → 自动解析/裂变/推进
# APPROVAL 挂起后：POST /annuity/core/application/advance
# 审批通过：POST /api/approval/instances/approve（携带签名 X-Account-Id / X-Account-Sig）
# 预期：application COMPLETED、interaction SUCCEEDED、batch COMPLETED
```

### 6.6 本轮验证的自动化测试

- kernel：500 tests，0 failures / 0 errors
- file-service（domain/application/infrastructure）：334 tests，0 failures / 0 errors；`docs/excel/*.xlsx` 被 .gitignore 排除，新增 `ExcelFixtureExtension` 在测试前自动生成夹具，仓库无需提交二进制
- approval-service（domain/application/infrastructure）：全绿
- annuity-starter：`AnnuityEndToEndTest` 6、`AutoAdvanceEnabledEndToEndTest` 2、`AnnuityFormUploadFlowTest` 1，全绿
- shared-lock：新增 `Lock4jDistributedLockTest`（null 返回值回归）全绿
- shared-permission / shared-event：全绿

### 6.7 结构整改后的最终服务层回归（2026-11-15 20:42）

在 K-07/K-08/A-04/S-04/S-05/S-01/S-06/S-08~S-17 与 Phase 4 部分整改完成后，重新打包并启动 file/approval/annuity，执行同一条真实链路：

| 项目 | 结果 |
|---|---|
| 批次 | `20261115000111`（PLAN-FINAL-REPAIR-2042） |
| 表单 / 文件 | `2026111501M7FKKSHH23PX1QQWJG307NHX` / `2026111501M7FKKST17RTXH5PZRTAB7D89` |
| 申请单 | `2026111501M7FKKTJ3DZWSWXV2592VJA17` → `COMPLETED/COMPLETED`，version 6 |
| 外部协作 | `SUCCEEDED`，external_task_id=`ApprovalInstanceId[value=61851222193209344]` |
| 审批实例 | `APPROVED`，version 2，requestNo=`SE-84` |
| 员工明细 | `t_annuity_employee_detail` 中该申请单对应批次 2 条 |
| 批次聚合 | 创建后初始 `PROCESSING`，由恢复任务收敛为 `COMPLETED/1/0`（update_time 20:42:12） |

说明：批次详情在申请单完成后由 `ExternalInteractionRecoveryJob` 的批次对账周期
（`core.kernel.recovery.fixed-delay`，默认 60s）收敛为 `COMPLETED`，不是状态丢失；
申请单本身已是权威终态。本轮恢复任务一次对账 8 个活跃批次。

### 6.8 全仓自动化回归（2026-11-15 20:48）

结构整改完成后执行全仓 `mvn test`：

- 292 个 surefire 报告，**1525 tests，0 failures，0 errors，2 skipped**
- 覆盖 kernel、file、approval、auth、annuity、integration、BFF、gateway、shared-*、arch-tests
- 网关上下文测试在 test profile 下用 `@MockitoBean ReactiveStringRedisTemplate` 补齐被排除的 Redis 自动配置；
  ArchUnit 监听器唯一性规则已按 K-07 调整为「外部 DTO 仅 adapter 入站、kernel 消息监听器持有回执网关」。

## 10. 全 7 服务入口 E2E（2026-11-15 21:09）

### 10.1 启动

`scripts/local/start-all-services.sh` 启动 auth/file/approval/annuity/integration/intranet-bff/internet-bff/gateway 共 8 个进程（统一 JVM `-Xms128m -Xmx256m`），
端口 18085/18082/18081/18090/18091/18092/18080 全部监听。本地脚本对业务服务显式传
`--permission.enforcement.enabled=false`（仅本地；生产必须保持开启，`PermissionReadinessValidator` 会 fail-fast）。

### 10.2 网关登录 + BFF 链路

| 步骤 | 请求 | 结果 |
|---|---|---|
| 登录 | `POST /internet/auth/login`，channel code `01` | ✅ `satoken-internet` token，accountId=U1001 |
| BFF 批次详情 | `POST /internet/bff/batch/detail` + satoken header | ✅ `COMMON.0000`，批次 `20261115000111` COMPLETED/1 |
| BFF 聚合 | `POST /internet/bff/dashboard/batch-overview` | ✅ batchDetail + progress + applications 三路聚合成功 |
| 安全 | 无 token | ✅ `COMMON.0002` |
| 安全 | 伪造 `X-Session-Context`/`X-Account-Id` + 合法 token | ✅ 网关剥离伪造头并注入真实签名头，业务成功 |

最终 jar 复验（21:15）：`mvn -DskipTests package` 后 7 服务全部启动，重复登录/BFF 批次详情/三路聚合均返回
`COMMON.0000`；无 token 返回 `COMMON.0002`。本地演示账号 U1001 的密码仅在验证期间临时重置，验证后已恢复原哈希。

### 10.3 本轮修复的网关/BFF 缺陷

1. **SessionContextInjector 未写入 `X-Session-Context`**：`applySignedHeaders` 只签名未落头，下游全部
   「会话上下文缺失」。已补 `headers.add(SESSION_CONTEXT_HEADER, encodedContext)`。
2. **Reactive `switchIfEmpty` 误触发/重复转发**：`chain.filter()` 成功时返回空 `Mono`，原实现会再次转发
   原请求并误报 token 无效。改为 `thenReturn(true)` + 末尾 `.then()`，只在真正无请求上下文时回退。
3. **BFF→业务服务会话头透传**：新增 `BffExchangeHeaderPropagationConfiguration`（httpexchange
   `RestClientCustomizer` 拦截器），复制网关签发的会话头。
4. **BFF 并发聚合丢失请求上下文**：`AnnuityScenarioHandler` 的 `CompletableFuture` 任务包装
   `RequestContextHolder`，异步线程同样能读到会话头。
5. **BFF 外部路径**：网关 `/internet/**` StripPrefix+PrefixPath 后，BFF 业务接口实际路径为
   `/internet/bff/...`（`BffBusinessApi` 的 `@HttpExchange("/bff")`），已实测确认。

### 10.4 整改后真实服务链复验（2026-11-15 21:17）

在 F-01/F-02/F-03、P-04、AU-02、AU-03/AU-04 与网关/BFF 修复全部落地后，再次用真实 file/approval/annuity 执行服务层全链路：

| 项目 | 结果 |
|---|---|
| 批次 | `20261115000121`（PLAN-POST-REFACTOR-2117） |
| 表单 / 文件 | `2026111501M7FNKA056G0TJ78H3N9WNDJE` / `2026111501M7FNKA6Y2G61YPP6VSQQZABN` |
| 申请单 | `2026111501M7FNKAX063APFY9K7ED854AE` → `COMPLETED/COMPLETED`，version 6 |
| 外部协作 | `SUCCEEDED`，`ApprovalInstanceId[value=61859950594359296]` |
| 审批实例 | `APPROVED`，requestNo=`SE-94` |
| 批次聚合 | 由恢复任务对账收敛为 `COMPLETED/1/0`（默认 60s） |

## 11. RocketMQ 5.x 生产事件通道实测（2026-11-15 22:08）

### 11.1 部署与接线

- RocketMQ 5.3.2（Docker）：namesrv `127.0.0.1:9876`、proxy `127.0.0.1:8080/8081`。
- Proxy 启动命令需显式指定 nameserver：
  `sh mqproxy -n rmqnamesrv:9876 -pm cluster`（原 compose 仅 `sh mqproxy` 会因容器内 localhost 解析栈溢出崩溃）。
- topic `event` 已通过 `mqadmin updateTopic` 创建（8 读写队列）。
- 项目切换为官方 `rocketmq-client-java:5.0.7` gRPC 客户端：`shared.event.rocketmq.endpoints=http://127.0.0.1:8081`。
- 本地 file/approval/annuity 配置：
  `shared.event.redis.enabled=false`、`shared.event.rocketmq.enabled=true`、`topic=event`。

### 11.2 实测证据

| 项目 | 结果 |
|---|---|
| 批次 | `20261115000131`（PLAN-ROCKETMQ-2210） |
| 申请单 | `2026111501M7FRKYVE9PZHEJ7ANXMKHGVM` → `COMPLETED/COMPLETED`，version 6 |
| 审批实例 | `APPROVED`，requestNo=`SE-104` |
| 批次聚合 | `COMPLETED/1/0` |
| Topic 位点 | `event` 各队列 maxOffset 2~4，证明消息真实进入 RocketMQ |
| 事件消费线程 | annuity `FileParsedEventListener`/`ApprovalResultEventListener` 在 `rocketmq-event-subscriber` 虚拟线程执行 |
| Redis 事件通道 | file/approval/annuity 日志均无 `RedisEventSubscriber`，确认未走 Redis pub/sub |

### 11.3 设计要点

- 统一 topic + tag（集成事件简单类型名）；key 为领域事件 eventId。
- 消息体为 `RocketMQEventEnvelope(eventType, payloadJson)`，避免 Jackson default typing 的任意类型风险。
- 消费端仅加载 `com.example.` 前缀类型；每个服务独立 consumer group，实现广播式消费。
- SimpleConsumer 手动 ack：处理失败不 ack，由 RocketMQ 不可见时长重投；业务侧 EventReceiptGateway 仍按 eventId 幂等。
- Producer 发送失败由 outbox 恢复任务按事件日志重试；`RocketMQEventDispatcher` 保持 `EventDispatcher.isRemote=true`。

### 11.4 RocketMQ 切换后的全量回归

- 全仓 `mvn test`：**293 reports / 1527 tests / 0 failures / 0 errors / 2 skipped**。
- `shared-pdf-core` 测试显式 `java.awt.headless=true`，避免容器缺少 `libXi.so.6` 时加载 X11 库失败。
- 7 服务当前均以 `-Xms128m -Xmx256m` 运行。

## 12. P-02 / I-02 收口与 integration 事件订阅（2026-11-15 22:25）

### 12.1 P-02：approval-domain 去除 business-core-types

- `approval-service/approval-domain/pom.xml` 仅保留 `approval-types`、`shared-domain`、Lombok/测试依赖。
- 领域聚合/实体/仓储端口中的 `ApplicationId`/`PlanNo`/`UserNo` 全部改为 `String`；应用层与适配层同步，
  MapStruct 生成代码随 clean build 重新生成。
- 基础设施的 `RoleUserGatewayImpl`、`ApprovalInstanceRepositoryImpl` 与相关测试完成 String 化。
- approval 模块（api/domain/application/infrastructure/adapter/starter）`clean install` 通过。
- `scripts/check_architecture_config.py` 新增规则：`auth-api`、`file-api`、`approval-domain` 禁止再声明
  `business-core-types` 依赖（防止后续回退），实测 `[OK]`。

### 12.2 I-02：integration 出站事件幂等

- 新增领域端口 `OutboundSubmissionReceiptGateway`
  （`tryAcquire` / `markSuccess` / `release`）。
- 新增 JDBC 实现 `JdbcOutboundSubmissionReceiptGateway`：
  - 启动时 `CREATE TABLE IF NOT EXISTS t_integration_submission_receipt`；
  - 主键 `(event_id, file_id)` + `ON CONFLICT DO NOTHING` 提供跨实例并发幂等；
  - 状态 `PROCESSING → SUCCESS`，提交异常时仅删除 PROCESSING 行以便重试。
- `TradeSubmissionAppService.submitOutboundFileOnce` 包装提交：重复事件直接返回
  `FileSubmissionResult.duplicate(...)`，不重复调用外部系统。
- `OutboundFileGeneratedEventListener` 改走 `submitOutboundFileOnce(eventId, fileId, ...)`。
- `TradeSubmissionAppServiceImplTest` 3 项（首次提交/重复跳过/失败释放）通过。

### 12.3 integration 事件通道补齐与运行验证

- 发现 integration 启动模块未引入事件通道，`OutboundFileGeneratedEventListener` 实际不会收到
  file-service 事件。已补 `shared-event-starter` 依赖，并在 `application-local.yml` 启用
  `shared.event.rocketmq`（endpoints `http://127.0.0.1:8081`、topic `event`、Redis 关闭）。
- 以 `-Xms128m -Xmx256m` 启动 integration-service（端口 18083）：
  - 日志：`Started IntegrationApplication`、`RocketMQ 事件订阅器已启动:
    group=cjannuity-channel-integration-event`；
  - PostgreSQL `schema_demo.t_integration_submission_receipt` 已创建，字段为
    `event_id/file_id/status/created_at/updated_at`。
- 说明：外部交易系统未在本地部署，未做“真实提交成功”联调；幂等语义由单元测试
  （重复返回 DUPLICATE、失败释放）覆盖，运行时已验证订阅器与回执表装配。

## 13. V-05 可观测性与 V-06 回滚演练（2026-11-15 22:52）

### 13.1 V-05：MDC 标准化与 traceId 全链透传

| 能力 | 实现 | 实测 |
|---|---|---|
| traceId 生成/复用 | gateway `TraceIdGatewayFilter`（order -310）；servlet `BizContextPropagationFilter` 读取 `X-Trace-Id`，缺失/非法时生成 32 位 hex | 网关传入 `v05-trace-e2e-001`，auth 日志与响应头一致；无头请求生成 `5b5edf58...` |
| traceId 跨服务 | 网关写下游请求头；`SpringWebTraceInterceptor`/Retrofit/OkHttp 从 MDC 透传 | gateway→auth、gateway→internet-bff→annuity 均出现同一 `v05-bff-trace-002` |
| 业务 MDC | kernel `FlowOrchestrationService` 写入 applicationId/stepExecutionId/requestNo/businessType | `annuity-service.log` 历史真实链路：`[applicationId:2026111501M7FED...][stepExecutionId:1][requestNo:SE-1][businessType:ACC_PLAN_CREATE]` |
| 日志格式 | auth/file/approval/annuity/integration/intranet-bff/internet-bff/gateway 统一 `[traceId:][applicationId:][stepExecutionId:][requestNo:][businessType:]` | 8 服务启动日志均已验证；integration 原 pattern 的 leading `$` 缺陷一并修复 |
| 保留映射 | `trace.context.mapping` 宿主覆盖时仍保留 `traceId → X-Trace-Id`；HTTP header 大小写归一 | `BizContextPropagationFilterTest` 4 项、`TraceIdGatewayFilterTest` 3 项通过 |

联调顺带发现：登录 `channel` 仅按 CodeEnum 编码查找，传 `NETAPP` 等非法值时返回 null，在领域层
`Set.contains(null)` 触发 NPE/500。`SessionController` 现同时接受编码（`01`）与渠道名（`NETAPP`），
未知值抛 `BusinessException(COMMON.0001)`；新增 `SessionControllerChannelTest` 3 项。

### 13.2 V-06：事件通道回滚演练（RocketMQ → Redis）

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | annuity 重启：`--shared.event.rocketmq.enabled=false --shared.event.redis.enabled=true` | 启动成功，无 `RocketMQEventSubscriber` |
| 2 | 向 Redis `event.FileParsedEventDTO` 发布 `EventEnvelope`（sourceInstanceId=`rollback-drill`） | `RedisEventSubscriber`：跨服务事件到达，type=FileParsedEventDTO |
| 3 | kernel 监听器处理 | `FileParsedEventListener` 收到 rollback-file-001，classification=UNMATCHED（探针数据预期） |
| 4 | annuity 重启回默认 local（RocketMQ enabled） | `RocketMQEventSubscriber` 恢复，group=`annuity-service-event` |
| 5 | 切回后发送 RocketMQ 探针（`mqadmin sendMessage`，tag=`FileParsedEventDTO`） | `SEND_OK`，annuity `FileParsedEventListener` 收到 rmq-file-001；通道恢复闭环 |

结论：X-04 的通道切换是纯配置项，关闭 RocketMQ 后可恢复旧 Redis pub/sub 路径；
持久化 outbox（`sys_event_store`/`sys_event_dispatch_log`）不随通道切换丢失。

### 13.3 最终回归

- 全仓 `mvn test`：**306 reports / 1596 tests / 0 failures / 0 errors / 2 skipped**（V-05/V-06 收口后最终回归）。
- 当前运行：auth/file/approval/annuity/intranet-bff/internet-bff/gateway + integration，共 8 进程，
  均 `-Xms128m -Xmx256m`。

### 13.4 可复制的全链路验证指南

登录→选计划→建批次→文件解析→推进→审批的逐步命令、请求体、种子 SQL 与数据库核对
见 `docs/superpowers/specs/2026-11-16-e2e-login-to-approval-verification.md`。
