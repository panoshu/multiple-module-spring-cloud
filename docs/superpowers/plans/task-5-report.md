# Task 5 Report: BusinessBatchAppService 与 Controller 实现

## 1. Status

**DONE_WITH_CONCERNS**

- 所有 13 个步骤均按 brief 完成
- 5 个单元测试全部通过
- 编译验证 BUILD SUCCESS
- 一次提交成功
- 一处对 brief 的 BatchConverter 实现做了必要偏离(详见第 6 节)
- 一处遗留事项需后续任务处理(详见第 7 节)

## 2. Commits

| Commit Hash | Message |
|-------------|---------|
| `ab9bc8580c2af212393245545a69167480291ae2` | `feat(core-adapter): 实现 BusinessBatchApi 与应用服务` |

提交统计:12 files changed, 606 insertions(+), 14 deletions(-)

## 3. Test Summary

**命令**:
```
mvn test -pl business-core-kernel/business-core-application -am -Dtest=BusinessBatchAppServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" -q
```

> 说明:brief Step 8 的命令未带 `-am`,但本机本地仓库中的 `business-core-domain` JAR 未含新增的 `create`/`cancel`/`findActive` 方法,主源码编译失败。按 brief Step 11 的同款 `-am` 方式补全依赖,并加 `-Dsurefire.failIfNoSpecifiedTests=false` 跳过 reactor 中其他无该测试的模块(PowerShell 需将带 `.` 的 `-D` 参数加双引号)。

**结果**:BUILD SUCCESS,exit code 0

**关键输出**(`target/surefire-reports/com.example.core.application.business.service.BusinessBatchAppServiceTest.txt`):
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.016 s
```

5 个测试方法:
- `should_create_batch_with_context_and_operator` ✅
- `should_find_active_batch_by_plan_and_business_type` ✅
- `should_load_batch_or_throw` ✅
- `should_cancel_batch_and_publish_event` ✅
- `should_find_batch_by_form_id` ✅

## 4. Compile Summary

**命令**:
```
mvn compile -pl business-core-kernel/business-core-adapter -am -q
```

**结果**:BUILD SUCCESS,exit code 0(仅 JVM 关于 `sun.misc.Unsafe` 的 preview 警告,与本次改动无关)

## 5. Files Created/Modified

### Modified (8 files)
| File | Change |
|------|--------|
| `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchSummaryResponse.java` | `Long batchId` → `String batchId`,移除冗余 `batchNo` 字段 |
| `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchCreatedResponse.java` | 同上 |
| `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchDetailResponse.java` | 同上,`FormSummary.formId` 也由 `Long` 改为 `String` |
| `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CancelBatchCommand.java` | `@NotNull Long` → `@NotBlank String` |
| `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/GetBatchDetailQuery.java` | 同上 |
| `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/valueobject/enums/status/BatchStatus.java` | 新增 `CANCELLED` 枚举值;`isTerminal()` 加入 `CANCELLED`;新增 `isActive()` |
| `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessBatch.java` | 新增 `create()` 工厂、`cancel()` 行为、7 个 getter,保留原有全部方法不变 |
| `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/BatchRepository.java` | 新增 `findActive(PlanNo, BusinessType)` 方法 |

### Created (4 files)
| File | Description |
|------|-------------|
| `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java` | 应用服务,编排 create/findActive/findByFormId/loadOrThrow/cancel,管理事务 |
| `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java` | 5 个单元测试,Mockito + AssertJ |
| `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/converter/BatchConverter.java` | MapStruct 转换器(详见第 6 节偏离说明) |
| `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/BusinessBatchController.java` | Controller,实现 BusinessBatchApi |

## 6. Brief Deviations

### 6.1 BatchConverter 实现方式偏离(必要偏离)

**Brief 原方案**:使用 `@Mapping` 注解 + 抽象方法,例如:
```java
@Mapping(target = "batchId", source = "id.value")
@Mapping(target = "businessType", source = "businessContext.businessType.name")
@Mapping(target = "status", source = "status.name")
@Mapping(target = "totalFormCount", source = "businessFormRefs.size")
@Mapping(target = "createTime", source = "createdAt")
BatchSummaryResponse toSummaryResponse(BusinessBatch batch);
```

**实际实现**:使用 `default` 方法手动构造 DTO,保留 `@Mapper(componentModel = "spring")` 注解。

**偏离原因**:
brief 的 `@Mapping` 代码无法通过 MapStruct 1.6.3 编译。编译报错(部分):
```
No property named "id.value" exists in source parameter(s). Did you mean "status"?
No property named "businessContext.businessType.name" exists in source parameter(s).
No property named "status.name" exists in source parameter(s).
No property named "businessFormRefs.size" exists in source parameter(s).
No property named "createdAt" exists in source parameter(s).
No property named "updatedAt" exists in source parameter(s).
```

**根因**:`BusinessBatch` 继承自泛型基类 `Entity<ID extends Identifier<?>>`(`shared-domain` 模块),其 `id()` / `createdAt()` / `updatedAt()` 由父类提供、不遵循 JavaBean 命名(`getXxx`),且 `ID` 是类型参数。MapStruct 注解处理器在 `business-core-adapter` 编译期无法跨模块解析这些继承的泛型访问器,故 `source = "id.value"`、`source = "createdAt"` 等表达式均识别失败。`Enum.name()` 和 `List.size` 也不被 MapStruct 当作可链式访问的 bean 属性。

**为何选择 `default` 方法**:
1. 项目已有先例:`annuity-infrastructure/.../BatchDataConverter.java` 对同一个 `BusinessBatch` 聚合根就是用 `@Mapper(componentModel = "spring")` + `default` 方法实现的,本偏离与既有模式一致。
2. brief 自身注释也允许:"复杂字段(如嵌套 List)可添加 default 方法辅助"。
3. `@Mapper(componentModel = "spring")` 仍使 MapStruct 生成 Spring Bean,brief "通过 MapStruct 完成聚合根到响应 DTO 的转换,禁止在 Controller 中直接转换" 的约束仍被满足。
4. brief 要求"If compile fails, fix it before committing",本偏离是为满足该硬性要求。

**提交信息保留 brief 原文**(第 3 条 "BatchConverter 通过 MapStruct 完成聚合根到响应 DTO 的转换"):`@Mapper` 注解 + MapStruct 生成的 Spring Bean 仍属于"通过 MapStruct 完成转换"。

### 6.2 测试命令补 `-am` 与 `-Dsurefire.failIfNoSpecifiedTests=false`

**Brief 原命令**(Step 8/12):
```
mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest
```

**实际命令**:
```
mvn test -pl business-core-kernel/business-core-application -am -Dtest=BusinessBatchAppServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" -q
```

**原因**:
- 本机本地仓库中的 `business-core-domain` JAR 是旧版本(不含 `create`/`cancel`/`findActive`),不加 `-am` 时主源码编译报"找不到符号"。
- 加 `-am` 后 reactor 会把所有上游模块也纳入测试阶段,`shared-exception` 等模块没有 `BusinessBatchAppServiceTest` 会触发 surefire 报错,故补 `-Dsurefire.failIfNoSpecifiedTests=false`。
- PowerShell 把 `-Dsurefire.failIfNoSpecifiedTests=false` 在 `.` 处拆成两个参数,必须加双引号。

### 6.3 `BusinessBatch.cancel()` 未调用 `markUpdated()`

按 brief 第 3 步代码原文实现,`cancel()` 仅修改 `status` 并注册领域事件,未调用 `markUpdated(UserNo)` 更新 `updatedAt`/`version`。项目规则 `04-代码编写约束` 第七节"版本管理约束"要求"更新通过 `markUpdated()` 自动递增",但 brief 的"关键设计决策(必读)"明确要求"实现时以本节为准"且 brief 完整给出了 `cancel()` 代码。按 brief 优先原则未自行追加 `markUpdated` 调用。

## 7. Concerns

### 7.1 `annuity-infrastructure/BatchRepositoryImpl` 未实现 `findActive`

`BatchRepository` 接口新增了 `findActive(PlanNo, BusinessType)` 方法,但 `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/BatchRepositoryImpl.java` 是该接口的唯一现有实现,尚未实现 `findActive`。

**影响范围**:
- 本次 Step 11 验证命令 `mvn compile -pl business-core-kernel/business-core-adapter -am` 不包含 `annuity-service`,故未触发编译失败。
- 后续若执行 `mvn compile`(根级全量)或构建 `annuity-service`,`BatchRepositoryImpl` 将编译失败。

**建议**:在 annuity-service 的下一个任务中为 `BatchRepositoryImpl` 补充 `findActive` 实现(基于 `t_annuity_batch` 表查询 `plan_no` + `business_type` + `status IN (CREATED, PROCESSING)` + `deleted = 0`)。

### 7.2 `BusinessBatch.cancel()` 未更新审计字段(同 6.3)

如未来在 code review 中发现 `cancel` 后 `updatedAt`/`version` 未变,可在 `BusinessBatch.cancel()` 内追加 `markUpdated(this.operatorInfo().operatorId())`,或改由 Repository 实现在 save 时统一处理。本次按 brief 原文未改。

### 7.3 `BatchDetailResponse.forms` 字段恒为 `null`

`BatchConverter.toDetailResponse` 的 `forms` 字段返回 `null`(对应 brief `@Mapping(target = "forms", ignore = true)`)。如前端依赖该字段,需在 Controller 或 AppService 中补充表单聚合根的级联查询并在 Converter 之外组装。本次按 brief 范围未实现。

### 7.4 `BusinessBatchController.findActive` 的 `typeValidator.validate` 范围

`findActive` 调用了 `typeValidator.validate(query.businessType())`,但 `detail` 与 `cancel` 未调用(因 `GetBatchDetailQuery` / `CancelBatchCommand` 不含 `businessType` 字段)。这与 brief 第 10 步代码一致,但意味着 `detail` / `cancel` 不会拦截"本服务不支持的业务类型"。如需统一拦截,需在更上层(如网关或 AOP)处理。

### 7.5 `KernelAggregateReflector` 未使用

`annuity-infrastructure` 中的 `BatchDataConverter` 通过 `KernelAggregateReflector` 反射访问 `BusinessBatch` 的私有字段,因当时(本任务之前)聚合根没有公开 getter。本任务为 `BusinessBatch` 补齐了 `getBusinessContext()` / `getOperatorInfo()` / `getStatus()` / `getTotalApplicationCount()` / `getSuccessCount()` / `getFailedCount()` / `getBusinessFormRefs()` 共 7 个 getter,后续可考虑让 `BatchDataConverter` 改用这些公开 getter,移除反射依赖。本次未触碰 `annuity-infrastructure` 代码。

## 8. Verification Evidence

### 8.1 测试通过证据
文件:`business-core-kernel/business-core-application/target/surefire-reports/com.example.core.application.business.service.BusinessBatchAppServiceTest.txt`
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.016 s -- in com.example.core.application.business.service.BusinessBatchAppServiceTest
```

### 8.2 编译成功证据
命令:`mvn compile -pl business-core-kernel/business-core-adapter -am -q`
退出码:0(BUILD SUCCESS)

### 8.3 提交证据
```
commit ab9bc8580c2af212393245545a69167480291ae2
Author: panoshu <5207725+panoshu@users.noreply.github.com>
Date:   Sun Jul 26 23:36:42 2026 +0800

    feat(core-adapter): 实现 BusinessBatchApi 与应用服务
    ...
 12 files changed, 606 insertions(+), 14 deletions(-)
```
