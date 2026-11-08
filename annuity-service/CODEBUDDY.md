# annuity-service 模块文档

## 一句话职责

基于 business-core-kernel 的年金业务服务，通过 SPI 扩展点注入年金特有的校验规则、数据摄入、材料计算和通知逻辑，驱动"表单上传→明细摄入→数据核验→材料准备→审批→完成"全生命周期。

## 入口清单

| 入口 | 类型 | 位置 | 说明 |
|------|------|------|------|
| `AnnuityController` | HTTP | `annuity-adapter/.../AnnuityController.java:35` | 年金特有业务入口：上传表单、查申请单、查批次状态 |
| `AnnuityKernelBatchController` | HTTP(kernel) | `annuity-adapter/.../kernel/AnnuityKernelBatchController.java:60` | 内核批次 API 实现：创建批次/活跃批次/详情/取消 |
| `AnnuityKernelApplicationController` | HTTP(kernel) | `annuity-adapter/.../kernel/AnnuityKernelApplicationController.java:42` | 内核申请 API 实现：列表/详情/推进/提交 |
| `AnnuityKernelFormController` | HTTP(kernel) | `annuity-adapter/.../kernel/AnnuityKernelFormController.java:39` | 内核表单 API 实现：上传凭证/确认上传/删除/状态 |
| `AnnuityKernelMaterialController` | HTTP(kernel) | `annuity-adapter/.../kernel/AnnuityKernelMaterialController.java:43` | 内核材料 API 实现：绑定/解绑/列表/完整性检查 |
| `AnnuityKernelProgressController` | HTTP(kernel) | `annuity-adapter/.../kernel/AnnuityKernelProgressController.java:33` | 内核进度 API 实现：批次进度查询 |
| `AnnuityLinkTestApi` → `AnnuityLinkTestController` | HTTP(测试) | `annuity-adapter/.../AnnuityLinkTestController.java` | 跨服务联通性测试（approval/file/integration） |
| `ApplicationSpawnedListener` | 事件(kernel) | `core-application/.../listener/ApplicationSpawnedListener.java:21` | 监听 ApplicationSpawnedEvent → 自动推进新申请单到 FORM_DETAIL_INGESTION |
| `StepAutoAdvanceListener` | 事件(kernel) | `core-application/.../listener/StepAutoAdvanceListener.java:26` | 监听 StepEnteredEvent → SYSTEM_TASK 自动推进，USER_TASK 挂起 |
| `FileParsedEventListener` | 集成事件(kernel) | `core-application/.../listener/FileParsedEventListener.java:25` | 监听 FileParsedEventDTO → 反查申请单并推进 |
| `ApprovalResultEventListener` | 集成事件(kernel) | `core-application/.../listener/ApprovalResultEventListener.java:31` | 监听审批结果事件 → 通过推进/驳回终止/撤回终止 |

## 职责清单

1. **年金表单上传**：验证年金规则 → 创建批次 → 创建表单 → 保存并发布事件
2. **文件解析裂变**：确认上传 → 触发异步解析 → 解析完成后裂变为多个 BusinessApplication
3. **明细摄入**：JSON 流式解析员工明细 → 去重（身份证号）→ 批量写入 AnnuityEmployeeBatch 聚合
4. **数据核验**：逐条校验员工身份信息 → 标记 VERIFIED 或 ANOMALY → 全部通过则批次 COMPLETED
5. **材料计算**：根据员工数据和业务事实计算所需材料清单 → 分配到每个 VERIFIED 明细
6. **审批触发**：材料准备就绪后创建审批流 → 等待审批结果事件
7. **年金特有校验**：初始缴费金额（≥100元）、外资投资准入、员工明细非空
8. **客户画像增强**：查询客户信息 → 提取风险等级和关联公司 → 注入 BusinessMetaContext
9. **审计日志与通知**：步骤进入时写审计日志，材料就绪后异步通知操作员
10. **跨服务联动**：通过 Gateway SPI 调用 file-service（文件解析/下载）、approval-service（审批）、integration-service（客户查询）

## 主流程

### 流程 1：表单上传（AnnuityController.uploadForm → 裂变完成）

```
前端 POST /api/annuity/upload
  → AnnuityController.uploadForm()
    → @RequirePermission(business="ANNUITY", action="UPLOAD_FORM")
    → AnnuityApiConverter.toCommand(request)           // 构造 UploadFormCommand
    → AnnuityAppService.uploadForm(command)
      → validateAnnuityRules(command)                  // 年金额度/外资校验
        → AnnuityContributionRule.validate()            // 初始缴费 ≥ 10000(分)
        → AnnuityForeignInvestmentRule.validate()       // 外资标志与客户画像交叉校验
      → BusinessBatchAppService.createBatch()           // 创建 BusinessBatch
      → BusinessForm.create()                           // 创建表单聚合根
      → formRepository.save(form) + 发布事件
  --- 事件驱动后续 ---
  FormUploadedEvent → BusinessFormAppService.triggerParsing()
    → configGateway.getFormParsingConfig()              // 获取解析模板: ANNUITY_DEFAULT_TEMPLATE
    → fileIntegrationGateway.triggerAsyncParsing()      // 调用文件服务异步解析
    → form.markAsParsing() → save

  FileParsedEventDTO → FileParsedEventListener.onFileParsed()
    → FlowOrchestrationService.advanceByFileTaskId()
      → BusinessFormAppService.handleParsingResult()
        → form.markAsParsed() → save
        → 循环 parsedResults:
          → BusinessApplication.createFromForm()        // 每个计划裂变为一个申请单
          → applicationRepository.save() + 发布事件

  ApplicationSpawnedEvent → ApplicationSpawnedListener.onApplicationSpawned()
    → @Async @TransactionalEventListener(AFTER_COMMIT)
    → FlowOrchestrationService.advanceStep(appId)       // 点火启动，进入 FORM_DETAIL_INGESTION
```

### 流程 2：步骤推进（FlowOrchestrationService.advanceStep 管道执行）

```
advanceStep(applicationId)
  → applicationRepository.loadOrThrow(appId)            // 加载聚合根
  → buildFullConfigContext(app)                          // 两阶段构建上下文
    → 阶段1: app.buildConfigQueryContext()               // 基础维度（客户/产品/计划/渠道）
    → 阶段2: configGateway.getExtractorConfig() → AnnuityFactExtractor.extractBusinessFacts()
    → 阶段3: BusinessMetaContext.withExtensionFacts()    // 含业务事实的完整上下文
  → configGateway.getNextStep(context, currentStep)      // 查 step-routes.json

  → pipelineExecutor.executeChain(preValidations)        // ① 前置校验管道
      → sort by order → condition filter → sync/async dispatch
      → 例 DATA_VERIFICATION: auditLog → contributionValidation → foreignInvestmentValidation

  → handlerRegistry.get(mainProcessor).execute()         // ② 核心处理器
      → 例 DATA_VERIFICATION: AnnuityDataVerificationHandler
        → load AnnuityEmployeeBatch → 逐条 verify()
        → AnnuityEmployeeVerificationRule: 身份证格式/年龄[18,70]/金额正值
        → markDetailProcessed() 或 markDetailAnomaly()
        → 全部 VERIFIED → batch.complete() → SUCCESS
        → 有 ANOMALY → batch.fail() → FAILED

  → pipelineExecutor.executeChain(detailProcessors)      // ③ 明细处理管道
      → 例 DATA_VERIFICATION: customerProfileEnrichment（注入 riskLevel, relatedCompanies）

  → pipelineExecutor.executeChain(sideEffects)           // ④ 副作用管道
      → 例 APPROVAL: materialPreparedNotification(async)

  → app.transit(nextStep)                                // ⑤ 状态跃迁

  → transactionTemplate: save + publishEvents            // ⑥ 持久化 + 事件发布
```

### 流程 3：材料准备（MATERIAL_PREPARATION 步骤）

```
advanceStep 进入 MATERIAL_PREPARATION:
  preValidations: [auditLog]
  mainProcessor: planMaterialPreparationHandler (kernel 提供)
  detailProcessor: [annuityEmployeeMaterialAction]

  annuityEmployeeMaterialAction.execute():
    → load AnnuityEmployeeBatch
    → 遍历 batch.verifiedDetails()（仅 VERIFIED 状态的明细）
    → AnnuityEmployeeMaterialRule.calculate(detail, context):
        基础材料: ID_CARD_COPY, EMPLOYMENT_CERT, SALARY_PROOF (全部 required)
        条件材料: FOREIGN_ASSET_DECL (当 hasForeignInvestment=true 时 required)
    → detail.assignMaterials(materials)                  // VERIFIED → MATERIAL_READY
    → batchRepository.save(batch)

  前端调用 /annuity/core/material/check-completeness:
    → AnnuityKernelMaterialController.checkCompleteness()
    → MaterialAppService.checkCompleteness()
    → 过滤未满足的材料项返回
```

## 数据与依赖

| 数据存储 | 表 | 说明 |
|----------|------|------|
| PostgreSQL | `t_business_application` | 内核通用申请单表，business_extension 列为 JSONB（多态 VO） |
| PostgreSQL | `t_business_batch` | 内核通用批次表 |
| PostgreSQL | `t_business_form` | 内核通用表单表 |
| PostgreSQL | `t_annuity_employee_batch` | 年金员工批次表（聚合根二） |
| PostgreSQL | `t_annuity_employee_detail` | 年金员工明细表，materials 列为 JSONB 数组 |

| 外部依赖 | 方向 | 协议 | 说明 |
|----------|------|------|------|
| file-service | 出站 | HTTP (Retrofit) + MQ 事件 | 文件上传凭证、异步解析、流式下载 |
| approval-service | 出站 | HTTP (Retrofit) + MQ 事件 | 创建审批流、接收审批结果 |
| integration-service | 出站 | HTTP (Retrofit) | 5564 交易码客户查询 |
| auth-service | 入站 | @RequirePermission AOP | 功能权限校验 |
| business-core-kernel | 内部依赖 | SPI + Spring Bean | 提供通用聚合根、应用服务、管道执行器 |

## 历史痕迹

| 痕迹 | 位置 | 说明 |
|------|------|------|
| 内核表名无 `t_annuity_` 前缀 | `schema-pg.sql:1-380` | 重构时 annuity-service 复用了内核的 DO/Mapper/Converter，内核表保持原名 |
| `JsonBusinessConfigGateway` 内存缓存 | `annuity-infrastructure/.../gateway/JsonBusinessConfigGateway.java:42` | 演示环境从 classpath JSON 加载配置，注释标明生产应切 Nacos/Apollo |
| `MockAnnuityCustomerGateway` @Primary | `annuity-infrastructure/.../gateway/MockAnnuityCustomerGateway.java:23` | 固定返回 LOW 风险客户，演示用 |
| `MockAnnuityNotificationGateway` @Primary | `annuity-infrastructure/.../gateway/MockAnnuityNotificationGateway.java:24` | 内存 List 存通知记录，仅用于测试断言 |
| `MockFileIntegrationGateway` @Primary | `annuity-infrastructure/.../gateway/MockFileIntegrationGateway.java:39` | 跳过真实文件服务，直接发布 FileParsedEventDTO |
| channel 映射 INTERNET→NETAPP | `AnnuityKernelBatchController.java` mapChannel() | 前端传 internet 渠道，映射到内核 AnnuityChannel.NETAPP 枚举 |
| version 重置 | `AnnuityEmployeeBatchRepositoryImpl.java:91` | 领域层 markUpdated() 可多次递增 version，save 时需重置为 DB 版本确保乐观锁匹配 |
| `isUserActionCompletedForCurrentStep()` 已删除 | `FlowOrchestrationService.java:59` | 注释表明曾经有显式的前置完成检查，现改为由 preValidation 中的业务校验器拦截 |
