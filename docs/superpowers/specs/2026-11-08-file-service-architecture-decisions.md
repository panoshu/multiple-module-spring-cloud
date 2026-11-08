# file-service 架构设计决策（定稿）

**日期**: 2026-11-08 **状态**: 已确认（用户审定） **关联**: file-service-parse-engine.md / storage-engine / access-token 三份既有设计

## 一、职责定位（定稿）

**文件数据中枢**：所有非结构化数据的全生命周期管理者，业务流程对文件的唯一合法入口。五层能力（存储/授权/处理/分发/生成），以"业务无关"为分层边界——业务语义经配置数据（TemplateConfig）注入，引擎零业务知识。

三品类：
- **FORM**（业务表单）：存储 + 配置驱动解析 + 拆分 + 校验
- **MATERIAL**（证明材料）：存储 + 元数据 + 状态
- **EXPORT**（导出产出物）：模板填充生成 + 分发

## 二、四项关键决策

### D1 导出分工：三方各一段
- 业务服务（kernel）：只触发（triggerOutbound）+ 等事件
- file-service：拉数据 → 模板填充 → 生成入 EXPORT 区 → 发 OutboundReadyEvent
- integration：监听事件 → 流式拉取文件 → 协议适配 → 提交外部系统
- **否决"业务服务拉回本地再上传"**：数据不动原则、能力归一、审计不断链

### D2 解析配置路由：上下文路由配置化（file 域内自治）
- FORM 的"哪个模板"由路由规则决定，路由规则配置化存于 TemplateConfig 聚合（产品/计划/账管人等上下文 → 模板）
- token 申请时（上传前）完成路由预判，无配置立即拒绝（fail-fast）
- 业务服务不感知模板选择

### D3 TTL 策略：品类差异化可配
- FORM 数据：默认 7 天；MATERIAL：随业务归档（永久）；EXPORT：默认 30 天
- 按 上下文（产品/计划/账管人）+ BizType 可配

### D4 网关直传路径：白名单进现有 channel-gateway
- file 上传/下载端点走网关白名单（跳过 sa-token——file token 自鉴权；跳过 CryptoFilter——multipart 不做 SM4）
- 理由：文件 <100MB 不构成独立入口必要性；全链路 trace/日志/脱敏一致性；配置级适配成本；前端单 base URL
- 配套：网关白名单条目、CryptoFilter 路径排除、上传端点限流

## 三、交互流程契约

### 事件契约（凭证 + 概要，轻消息）
| 事件 | 载荷 | 消费方 |
|---|---|---|
| FileUploadedEvent | fileId、品类、关联键、上下文 | kernel（材料步骤推进） |
| FileParsedEvent | fileTaskId、关联键、成功/失败行数、错误摘要、subTaskIds | kernel（表单步骤推进） |
| FileParseFailedEvent | fileTaskId、错误码、摘要 | kernel（失败/人工步骤） |
| OutboundReadyEvent | fileId、关联键、目标模板 | integration、业务服务 |

通道：shared-event-starter 的 MQ dispatcher（redis/rocketmq）。

### 职责铁律
- **文件字节流只在前端 ↔ file ↔ integration 流转**——BFF/业务服务永不中转文件
- **数据不动**：canonical 数据留在 file 域，业务方凭凭证分页拉取
- BFF 只透传 token、不监听事件（事件是服务间机制）
- 前端失败重试 = 重新申请 token（幂等关联原 FileMetadata）

## 四、kernel 框架内置（业务线零实现）

1. 上传步骤处理器（FORM/MATERIAL 两版 USER_TASK handler，step 配置声明品类）
2. 文件事件统一监听（事件 → advanceByFileTaskId 泛化映射）
3. 数据摄取骨架（AbstractJsonStreamIngestionAction 扩展 subTaskId 分页游标）
4. 导出步骤处理器（SYSTEM_TASK：触发 + 等 OutboundReadyEvent）
5. 材料清单完整性校验（step manifest）

## 五、双链路语义澄清（上传的两条路径）

- **USER_TASK 上传**（用户文件）：前端 token 直传（流程 A）
- **SYSTEM 文件操作**（导出复制、系统生成文件）：kernel 网关以系统身份直传（系统 token）
- 两条链路最终都进入 file 的统一处理管线（品类路由一致）

---

## 六、差距矩阵与实施批次（2026-11-08 评审后追加）

### 差距矩阵（设计 vs 现状）

**流程A 上传摄入**：A1 品类模型缺失（BizType 散落字符串）；A2 token 申请无路由预判/约束包；A3 路由规则无结构化模型；A4 上传后无解析触发（ParseFileUseCase 孤儿）；A5/A6 三事件零发布；A7 MQ dispatcher 未启用（配置级）；A8 网关白名单未配
**流程B 数据消费**：B1 ParsedDataApi 零实现；B2 kernel 摄取骨架走文件流非分页
**流程C 导出**：C1 OutboundReadyEvent 零发布；C2 integration 无事件监听接线；C3 kernel 导出 handler 无
**流程D 下载**：✅ 基本就绪（只差 A8 白名单）
**治理**：E1 TTL 缺失；E2 kernel 内置 5 件套缺 4 件；E4 domain 子域分包未做

### 实施批次（总约 12.5 人日）

| 批次 | 内容 | 验收 |
|---|---|---|
| 1 核心闭环通电 | A4→A6→A7（触发解析+三事件+dispatcher） | 真实 Excel→SubTaskData→事件 |
| 2 数据消费闭环 | B1→B2（分页 API+摄取切换） | kernel 分页拉到真实数据 |
| 3 品类与路由 | A1→A2→A3 | 上下文路由正确模板+fail-fast |
| 4 导出闭环 | C1→C2→C3 | 触发→生成→事件→integration 提交 |
| 5 治理收尾 | E1→A8→E2→E4 | TTL/白名单/内置件/分包 |

## 七、导出数据模型与增强扩展（2026-11-08 二次评审追加）

### 字段域（Field Domain）= 标准数据模型
- 业务类型的字段全集，显式配置于 TemplateConfig；导出模板是它的物理投影
- 字段分两类：base（来源=解析原始数据 SubTaskData）、enriched（来源=业务增强）
- 入站映射以字段域为靶子（多样源→同一契约子集）；出站取全集——多入一出枢纽

### 数据流
- 解析原始数据持久化（SubTaskData）→ 业务经分页 API 读取做业务处理 → 导出默认直接读原始数据

### 导出增强扩展（三层）
- 协议：file 定义通用 enrichment 端点协议（POST，请求=关联键+baseRows，响应=按行增强字段），零业务语义，端点 URL 由导出模板配置注入（运行时路由，编译零依赖）
- 骨架：kernel 提供 AbstractOutboundEnrichmentEndpoint 基类（封装协议），业务继承实现 enrich(rows, keys)
- 逻辑：业务服务查业务库+计算返回增强字段
- 合并规则：enriched 覆盖 base 同名字段（声明式配置）
- 备用模式：全量数据推送（业务组装完整数据集）仅用于跨子任务汇总透视类极端场景
- 容错：**fail-fast（已定稿）**——增强调用失败即导出失败，发失败事件由业务决定重试；金融数据完整性优先

### 复用与可靠性备注
- 事件通道= event-starter 三 dispatcher 之一（redis 起步/rocketmq 生产），DB 持久化+认领式恢复任务兜底
- 模板配置/路由规则缓存走 cache-starter（ICacheTemplate+shared.cache.rules+broadcastChannel 失效）——批次 3 接入
