# P2 专项方案（决策建议 + 小批次拆分）

> 生成日期：2026-11-14
> 依据：`2026-11-14-remaining-work-priority.md` P2 四项、`2026-11-08-business-core-kernel-production-design.md` §21.4 / §23.46 / §23.48、当前代码逐项核实
> 性质：**方案，不是实施计划**。按「方案需与用户确认后才能进入编码阶段」，本文只产出决策与拆分，不写任何生产代码、不改规格正文、不创建实施任务
> 状态：P0/P1 已全部清空（批次 45–48）；本文件是 P2 排期决策依据。**推进状态（2026-11-14）**：用户已确认「采纳建议推进 49→50」——批次 49 规格修订、批次 50 向前 ArchUnit 约束连续推进；P2-1 降级关闭、P2-3 wontfix、P2-4 暂缓三项按 §1 决策落实，结论与触发条件保留在 §2，不再重复评估。**批次 49/50 均已完成（2026-11-14，设计文档 §23.56 / §23.57），P2 采纳后的编码工作收口**
> 约束：延续「不要太大超级计划」——每个建议批次控制在 3–8 文件、可独立 TDD 与验收

---

## 1. 结论先行：决策总览

| 项 | 核实后的真实规模 | 决策建议 | 核心理由 | 是否需规格修订 |
|---|---|---|---|---|
| **P2-1** `Map<String,Object>` 强类型化 | 16 文件 / 30 处 / **4 类不同语义** | **建议降级关闭**（备选：仅做「类型安全读取包装」） | 动态 Map 是通用编排引擎承载「业务线自定义配置/事实」的正确设计，全量强类型化会破坏扩展能力且收益为负 | 是（补「为何保留动态结构」的设计说明） |
| **P2-2** §21.4 未机器化 3 条 | ① 约 35 个公开方法仅 2 个用 CQE；② types 24 类含 13 个非 ID 值对象；③ 5 个事件 DTO **全部**在 `*.api.event` | **建议做**：以「规格修订 + 向前 ArchUnit 约束」收敛，不回溯大重构 | 3 条与现状的张力多为**规格表述过严或自相矛盾**，并非生产缺陷；向前约束能防继续劣化且零生产风险 | 是（修订 §21.4 三条表述） |
| **P2-3** annuity 自有存储持久化 | 2 个 `ConcurrentHashMap`，引用面跨 annuity 4 模块 + kernel 3 模块 + 4 个测试 | **建议关闭（wontfix）** | annuity 是冻结的早期示例服务，前置条件「仅当转真实业务服务」不会触发；重启丢失在示例服务可接受；DDL 已由批次 40 解耦 | 否（仅记触发重启条件） |
| **P2-4** 多进程 + 真 Redis 锁 + 真 DB 并发集成 | 仓库无 testcontainers / 无 Redis 测试设施；`DistributedLock` 实现为 `Lock4jDistributedLock`；批次 43 为进程内 fake | **建议保持暂缓** | 需引入容器化依赖 + CI Docker + 多 JVM 编排；核心不变量已由批次 43 确定性并发覆盖 | 否（列前置决策清单） |

**唯一建议近期开工的是 P2-2**，拆 2 个小批次（纯文档 + 1 条 ArchUnit 向前约束）。其余三项本方案给出「关闭 / 暂缓 / 备选」依据，等用户确认。

---

## 2. 逐项核实与方案

### P2-1 `Map<String,Object>` 业务扩展字段强类型化

**核实**：`grep` 出 30 处在 16 个文件，按语义分 4 类，**不能一刀切**：

| 语义类 | 代表位置 | 本质 | 强类型化是否可行 |
|---|---|---|---|
| 动态配置/规则 | `FormParsingConfig.splitRules`、`StepExtensionConfig.params`、`BusinessConfigGateway.getFormParseRule` | 来自业务线配置 JSON，schema 由业务线定义 | ❌ kernel 不应为每种业务定义 Java 类型 |
| 执行上下文/事实 | `StepExtensionAction.execute(.., params)`、`ExtensionExecutionResult.mutations`、`BusinessMetaContext.extensionFacts`、`BusinessFactExtractor`、`ConditionEvaluationGateway.evaluate(.., context)` | SpEL 求值上下文 + 动作间透传的事实袋 | ⚠️ 求值需 `Map<String,Object>`；改包装类要双向转换，净收益低 |
| 解析数据行 | `AbstractPagedIngestionAction` 的 `row`、`FileIntegrationGateway` 的 `List<Map<String,Object>> rows`、`mapToEntity(.., params, ..)` | Excel/JSON 动态列 | ❌ 列集合运行时决定 |
| 出站补充数据 | `FileIntegrationGateway.supplementalData` | 业务线自定义键值 | ⚠️ 同上 |

**判断**：kernel 是「业务线通过 SPI + 配置扩展、kernel 不解析业务 schema」的通用编排引擎。上述 Map 绝大多数是**业务线自有、kernel 只透传/求值**的数据，强行建 Java 类型等于把业务 schema 固化到 kernel，违反 CODEBUDDY.md「业务线扩展它，永不修改 kernel 代码」的立项目标。清单 P2-1 描述「架构级弱类型设计」把它当成缺陷，但这是设计选择而非 bug。

**决策建议**：**降级关闭**，改为补一份「动态上下文设计说明」写进规格（§21 或 §23），明确：
1. 哪些 Map 属「业务线自定义透传」，kernel 不得解读其结构；
2. 新增扩展点时的约束（禁止把领域对象塞进 Map；只允许标量、配置结构、ID 字符串；跨层传递保持不可变视图）；
3. 记录为何不建 Java 类型。

**备选（仅当用户坚持要类型安全）**，拆 3 个小批次，仍不碰动态行/配置：
- P2-1a：产出分类表与规格补充（纯文档，2 文件）；
- P2-1b：引入不可变 `ExtensionParams` 包装替换「执行上下文」类 Map 的**读取侧**（`get(String, Class<T>)` 类型安全 + 不可变），约 6–8 文件，保留 `asMap()` 供 SpEL；
- P2-1c：给配置类补「schema 校验」（键白名单/类型校验），约 3–4 文件。
> 备选方案总计仍约 12–15 文件、跨两层，属本文件开头约束的上限，需单独确认后再拆。

### P2-2 §21.4 未机器化 3 条（建议做）

**核实**（对应 §23.48「刻意不机器化的 3 条」）：
- ① **CQE 化**：约 35 个 `public` 方法，仅 `advanceApplication(AdvanceStepCommand)` 与 `correlate(CallbackCommand)` 用 CQE；其余为裸参（`ApplicationId`、`String`、`LocalDateTime`、`List<...>`、`BusinessFile`），分布在 5 个 `*AppService` + 5 个 `*Service`。回溯改造 ≈ 30 方法 / 10+ 文件 / 大量测试，且**无生产正确性收益**（纯形式）。
- ② **types 只含 ID**：`business-core-types` 24 个类，11 个 `Identifier`，13 个为 `BankAccount`/`Address`/`ValidityPeriod`/`Email`/`Mobile`/`IdentityDocument`/`Channel`/`OperatingMode` 等值对象与枚举。按 03-领域模型约束「非 ID 类型定义在 domain 层」确有偏差，但移动需改全部 import，收益仅分层洁癖。
- ③ **application 零事件 DTO**：实际依赖 5 个 DTO，全部位于 `file.api.event.*` / `approval.api.event.*`。**关键**：§21.4 第 6 条「跨服务只依赖 API 模块」与第 2 条「application 不依赖 file/approval DTO」自相矛盾——事件 DTO 正是 API 模块契约，批次 41 的规则 4 已把「只经 api/types 依赖」机器化并绿灯。

**判断**：三条都不是生产缺陷。①是规格对存量代码的过严要求；②是低成本低收益的分层偏差；③是规格内部矛盾。正确动作是**修规格 + 加向前约束**，而非大重构。

**建议批次**（编号接 48，开工时按实际顺延）：

| 批次 | 内容 | 文件 | 验证 |
|---|---|---|---|
| **49（P2-2a）规格修订** | 修订 §21.4 三条表述：①「**新增或修改的写操作入口**必须用 CQE，存量方法不回溯」；②「types 层只放跨层共享的领域原语（ID 及通用值对象），**不得放聚合根/实体/领域服务**」；③「application 不得依赖 file/approval 的 **非 API 层**类型；事件 DTO 属允许的 API 契约」。同步 §23.46 核对表 §21.4 三行状态由 ⚠️ 改为「规格已修订 + 向前约束」。纯文档、零代码 | 2–3 | 无代码，`git diff --check` + 台账自洽 |
| **50（P2-2b）向前约束机器化** | `arch-tests` 新增 2 条 ArchUnit：① 新增的 `*AppService`/`*Service` public 方法参数须为 `*Command`/`*Query`/`*Event` 或零参（**只对新增生效**，存量通过白名单快照豁免）；② types 层不得出现 `AggregateRoot`/`Entity`/`@DomainService` 子类。含防假绿断言（导入数、逐层存在性）+ 反向注入违规验证规则真生效 | 1–2 | TDD 先红后绿；arch-tests 全量、kernel 全模块、annuity E2E、全仓 `test-compile`、`git diff --check` |

> **边界**：批次 50 明确「不回溯改造存量」——白名单快照只记录现有方法，新增违规即红灯。绝不把 30+ 存量方法改造成 CQE（那是超级计划）。

### P2-3 annuity 自有存储改持久化聚合（建议关闭）

**核实**：`InMemoryAnnuityApplicationExtensionStore` 与 `InMemoryParsedFileReferenceGateway` 均为 `ConcurrentHashMap` 进程内态；引用面跨 annuity-domain/application/infrastructure/adapter + starter 测试 + kernel 的 `ParsedFileReferenceGateway`/多个 Handler。

**决策**：**wontfix**。理由：① annuity 是用户明确冻结的早期示例服务，清单原始前置条件「仅当 annuity 转为真实业务服务时再做」不会触发；② 进程内态重启丢失在「只验证编排链路」的示例服务里可接受；③ 批次 37b/37c-1 引入这两个存储是为了把遗留字段迁出核心聚合，已达目的；④ 批次 40 已消除 DDL 复制根因，无需再为 kernel 改动反复解冻 annuity。

**触发重启条件**（写入台账）：仅当 annuity 或某个新业务服务成为真实生产服务、且需要跨重启保留扩展/解析文件引用时，按「新增 DO + Mapper + DDL + 改 SPI 实现」立项，规模约 8–12 文件，须单独出方案。

### P2-4 多进程 + 真 Redis 锁 + 真 DB 并发集成（建议暂缓）

**核实**：仓库无 `testcontainers` 依赖、无 Redis 集成测试设施；`DistributedLock` 唯一实现是 `Lock4jDistributedLock`（shared-lock-starter，依赖 Redis）；批次 43 的 `ConcurrentAdvanceAcceptanceTest` / `OptimisticLockConcurrencyTest` 是进程内状态化 fake + `CountDownLatch` 的确定性并发，§23.50 已如实标注「多进程 + 真实 Redis + 真实数据库集成测试仍未做」。

**决策**：保持暂缓。**若要做**的最小方案（需先出专项设计，不在本文件展开）：
1. 引入 `testcontainers` + Redis 容器（父 pom 加版本属性，1 个测试模块）；
2. 用 JDK `ProcessBuilder` fork 2 个 JVM 共享同一 Redis 与 H2/PG，编排真并发；
3. 断言与批次 43 相同的两条不变量（只一次有效跃迁、CAS 不覆盖）；
4. 稳定化处理（容器启动等待、固定 seed、超时容忍），并评估 CI 是否具备 Docker。

**前置决策清单**（需用户/基础设施确认）：CI 是否有 Docker？是否允许新增容器化测试依赖（构建时间显著增加）？是否需要「多 JVM」而非「单 JVM 多线程 + 真 Redis」（后者成本低得多且已能验证锁实现）？若接受后者，可降级为一个小批次（真 Redis + 多线程），规模约 4–6 文件。

---

## 3. 依赖关系与推荐顺序

```
49（规格修订 §21.4） ──> 50（向前 ArchUnit 约束）    ← 唯一建议近期开工，串行
        │
        └── 不依赖 P2-1 / P2-3 / P2-4

P2-1（设计说明，若采纳「降级关闭」） ── 可与 49 合并为纯文档批次
P2-3（关闭） / P2-4（暂缓） ── 仅台账记录，不排批次
```

**推荐起步**：批次 49（纯文档规格修订）→ 批次 50（向前约束）。理由：唯一「小步、零生产风险、有真实收益（防止继续劣化）」的方向；其余三项的关闭/暂缓结论先请用户确认，确认后只更新台账、不写代码。

---

## 4. 开放点（需用户确认，确认前不开工）

1. **P2-1 是否采纳「降级关闭 + 补设计说明」**？还是坚持做「类型安全读取包装」（备选 P2-1a/b/c，约 12–15 文件）？
2. **P2-2 是否同意「修规格 + 向前约束、不回溯存量」**？特别是 §21.4 第 2/5 条从「字面禁令」改为「分层边界 + 向前约束」，属规格决策。
3. **P2-3 是否确认 wontfix**？（与「冻结 annuity」一致，但仍请明示）
4. **P2-4 是否同意继续暂缓**？若要做，先定「CI 是否有 Docker」与「真多 JVM 还是单 JVM+真 Redis」。
5. 批次编号 49/50 为建议值，开工时按实际顺延；每批完成后按既有约定自动提交推送。

---

## 5. 验收口径（每个实施批次通用，沿用批次 1–48）

1. TDD：先红（契约/断言失败）后绿，禁止空转；涉及架构规则须做防假绿与反向违规验证。
2. 全仓验证：kernel 全模块测试 + `arch-tests` + annuity E2E + 全仓 `mvn test-compile` + `git diff --check`。
3. 台账：设计文档补 `§23.x` 边界说明、修正上一批提交号；优先级清单与计划文档标注状态；收口批次回填自身提交号，无「本次（未提交）」残留。
4. 提交：Conventional Commits，多文件写 Body，批次完成自动 commit + push。
5. 如实标注：不成立的约束、未覆盖的边界一律写明，不冒充已覆盖（延续 §23.46/§23.48 做法）。

---

## 6. 维护约定

- 本文只做决策与拆分；用户确认后，按 §3 顺序逐批开工，每批独立验收、独立提交。
- 若核实发现某项已完成或描述过时，直接移入主清单第 2 节纠偏表，不保留在待办里。
- 「关闭 / 暂缓」项不是遗忘：在台账保留结论与触发重启条件，避免后续会话重复评估。
