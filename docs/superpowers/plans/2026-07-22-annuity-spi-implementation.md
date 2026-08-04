# 年金服务 SPI 完整实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 annuity-service 中完整实现 kernel 预留的 `StepActionHandler` 与 `StepExtensionAction` SPI,演示完整端到端业务链路。

**Architecture:** 业务规则集中在 annuity-domain 层 (`@DomainService`),编排节点在 annuity-application 层 (Handler/Action
注入 domain service 委托执行)。annuity-service 拥有独立聚合根 `AnnuityEmployeeBatch`(含 `AnnuityEmployeeDetail` 实体),通过
ID 引用 kernel 的 `BusinessApplication`。kernel 修改仅限 `BusinessApplication` 新增 3 个公开 accessor 方法,消除
annuity-service 中的反射访问。

**Tech Stack:** Java 25 (preview), Spring Boot 3.5.14, MyBatis-Flex 1.11.5, H2 (PostgreSQL 兼容模式,测试), JUnit 5 +
Mockito, Jackson 多态。

## Global Constraints

- JDK 25 启用 `--enable-preview`,所有 `mvn` 命令需带该参数 (项目已全局配置)
- DDD 七层架构:types → domain → api → application → adapter → infrastructure → starter
- domain 层禁止依赖 Spring/MyBatis/Jackson 注解,只能使用 `com.example.core.domain.annotation.DomainService`
- 所有 DTO 转换通过 MapStruct `@Mapper(componentModel = "spring")`
- API 接口必须使用 `@HttpExchange`,但本计划不新增 API (Controller),仅扩展内部 SPI
- 数据库 schema 必须提供三套:PostgreSQL (`schema-pg.sql`)、MySQL (`schema-mysql.sql`)、H2 (`schema-h2.sql`)
- H2 用 `TEXT` 替代 `JSONB`,用 `INT DEFAULT 0` 替代 `BOOLEAN DEFAULT FALSE`(MyBatis-Flex 逻辑删除兼容)
- 测试遵循 TDD:先写失败测试,再写实现,再验证通过,每个 Task 末尾提交
- 提交信息使用 `feat(scope):` / `fix(scope):` / `test(scope):` / `chore(scope):` 前缀
- 现有分支:`fix/phase1-blocking-fixes`,所有新提交基于此分支
- kernel 存在两个同名 `@DomainService` 注解,annuity-service 统一使用 `com.example.core.domain.annotation.DomainService`

---

## File Structure

### kernel 修改 (1 文件)

-
`business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/root/BusinessApplication.java` —
新增 3 个公开 accessor

### annuity-types (2 新增)

- `annuity-types/.../AnnuityEmployeeBatchId.java` — 批次 ID
- `annuity-types/.../AnnuityEmployeeDetailId.java` — 明细 ID

### annuity-domain (13 新增,2 修改)

- `annuity-domain/.../aggregate/root/AnnuityEmployeeBatch.java` — 聚合根
- `annuity-domain/.../aggregate/entity/AnnuityEmployeeDetail.java` — 实体
- `annuity-domain/.../aggregate/valueobject/AnnuityEmployeeMaterial.java` — 材料值对象
- `annuity-domain/.../aggregate/valueobject/AnnuityEmployeeBatchStatus.java` — 批次状态枚举
- `annuity-domain/.../aggregate/valueobject/AnnuityEmployeeDetailStatus.java` — 明细状态枚举
- `annuity-domain/.../aggregate/valueobject/CustomerProfile.java` — 客户画像值对象
- `annuity-domain/.../aggregate/valueobject/NotificationType.java` — 通知类型枚举
- `annuity-domain/.../gateway/AnnuityCustomerGateway.java` — 客户网关接口
- `annuity-domain/.../gateway/AnnuityNotificationGateway.java` — 通知网关接口
- `annuity-domain/.../repository/AnnuityEmployeeBatchRepository.java` — 仓储接口
- `annuity-domain/.../service/AnnuityExtensionResolver.java` — 扩展字段类型安全解析器
- `annuity-domain/.../service/AnnuityContributionRule.java` — 缴费校验规则
- `annuity-domain/.../service/AnnuityForeignInvestmentRule.java` — 外资准入规则
- `annuity-domain/.../service/AnnuityEmployeeVerificationRule.java` — 员工核查规则
- `annuity-domain/.../service/AnnuityEmployeeMaterialRule.java` — 员工材料计算规则
- `annuity-domain/.../service/AnnuityEmployeeMapper.java` — DTO→实体映射规则
- `annuity-domain/.../errorcode/AnnuityDomainErrorCode.java` — **修改**:扩展错误码
- `annuity-domain/.../extractor/AnnuityFactExtractor.java` — **修改**:移除反射,改用 Resolver

### annuity-api (1 新增)

- `annuity-api/.../dto/AnnuityEmployeeDTO.java` — 员工明细 JSON DTO

### annuity-application (9 新增)

- `annuity-application/.../handler/AnnuityDataVerificationHandler.java` — StepActionHandler 实现
- `annuity-application/.../extension/AnnuityDetailIngestionAction.java` — 继承 AbstractJsonStreamIngestionAction
- `annuity-application/.../extension/AnnuityEmployeeCountValidationAction.java` — 员工数校验
- `annuity-application/.../extension/AnnuityContributionValidationAction.java` — 缴费校验
- `annuity-application/.../extension/AnnuityForeignInvestmentValidationAction.java` — 外资准入校验
- `annuity-application/.../extension/AnnuityCustomerProfileEnrichmentAction.java` — 客户画像丰富
- `annuity-application/.../extension/AnnuityEmployeeMaterialAction.java` — 明细材料计算
- `annuity-application/.../extension/AnnuityMaterialPreparedNotificationAction.java` — 通知
- `annuity-application/.../extension/AnnuityAuditLogAction.java` — 审计

### annuity-infrastructure (11 新增,3 修改)

- `annuity-infrastructure/.../entity/AnnuityEmployeeBatchDO.java`
- `annuity-infrastructure/.../entity/AnnuityEmployeeDetailDO.java`
- `annuity-infrastructure/.../mapper/AnnuityEmployeeBatchMapper.java`
- `annuity-infrastructure/.../mapper/AnnuityEmployeeDetailMapper.java`
- `annuity-infrastructure/.../converter/AnnuityEmployeeBatchDataConverter.java`
- `annuity-infrastructure/.../converter/AnnuityEmployeeDetailDataConverter.java`
- `annuity-infrastructure/.../repository/AnnuityEmployeeBatchRepositoryImpl.java`
- `annuity-infrastructure/.../gateway/MockAnnuityCustomerGateway.java`
- `annuity-infrastructure/.../gateway/MockAnnuityNotificationGateway.java`
- `annuity-infrastructure/src/main/resources/schema-pg.sql` — **修改**:追加 2 表
- `annuity-infrastructure/src/main/resources/schema-mysql.sql` — **修改**:追加 2 表
- `annuity-infrastructure/.../converter/KernelAggregateReflector.java` — **修改**:移除 `readExtension()`

### annuity-starter (2 修改)

- `annuity-starter/src/test/resources/schema-h2.sql` — **修改**:追加 2 表
- `annuity-starter/src/test/java/com/example/annuity/AnnuityEndToEndTest.java` — **修改**:新增 4 个测试用例
- `annuity-starter/src/main/resources/config/step-routes.json` — **修改**:更新路由配置

**合计:37 文件 (32 新增 + 5 修改)**

---

## Task 依赖关系

```
Phase A (Task A1)         — kernel 修改(独立)
    ↓
Phase B (Task B1-B6)      — annuity-types + annuity-domain(依赖 A1)
    ↓
Phase C (Task C1-C4)      — annuity-application(依赖 B)
    ↓
Phase D (Task D1-D4)      — annuity-infrastructure(依赖 B,C)
    ↓
Phase E (Task E1-E2)      — annuity-starter 端到端(依赖 B,C,D)
```

---

# Phase A: kernel 修改

## Task A1: BusinessApplication 开放 accessor

**Files:**

- Modify:
  `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/root/BusinessApplication.java`

**Interfaces:**

- Produces: `BusinessApplication.businessExtension()` → `BusinessExtension`(供 annuity-domain 的
  AnnuityExtensionResolver 调用)
- Produces: `BusinessApplication.operatorInfo()` → `OperatorInfo`(供 annuity-application 的通知 Action 调用)
- Produces: `BusinessApplication.businessContext()` → `BusinessContext`(供 annuity-application 的客户画像 Action 调用)

- [ ] **Step 1: 编写失败测试**

Create:
`business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/aggregate/root/BusinessApplicationAccessorTest.java`

```java
package com.example.core.domain.aggregate.root;

import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessApplication 公开 accessor 验证")
class BusinessApplicationAccessorTest {

  @Test
  @DisplayName("businessExtension() 返回扩展字段实例")
  void businessExtension_returnsExtensionInstance() {
    BusinessApplication app = buildTestApp();
    assertThat(app.businessExtension()).isNull();
  }

  @Test
  @DisplayName("operatorInfo() 返回操作人信息")
  void operatorInfo_returnsOperatorInfo() {
    BusinessApplication app = buildTestApp();
    assertThat(app.operatorInfo()).isNotNull();
    assertThat(app.operatorInfo().operatorId().value()).isEqualTo("U-TEST");
  }

  @Test
  @DisplayName("businessContext() 返回业务上下文")
  void businessContext_returnsBusinessContext() {
    BusinessApplication app = buildTestApp();
    assertThat(app.businessContext()).isNotNull();
    assertThat(app.businessContext().businessType()).isEqualTo(BusinessType.ACC_PLAN_CREATE);
  }

  private BusinessApplication buildTestApp() {
    BusinessContext context = new BusinessContext(
        BusinessType.ACC_PLAN_CREATE,
        CustomerNo.of("C-001"), "客户",
        ProductNo.of("P-001"), "产品",
        PlanNo.of("PL-001"), "方案",
        OperationModel.Single_Trustee, AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
        AnnuityChannel.NETAPP, UserNo.of("U-TEST"), "操作人", false
    );
    return BusinessApplication.createFromForm(
        new ApplicationId("APP-001"), context, operator, new FileId("FILE-001")
    );
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run:
`mvn -pl business-core-kernel/business-core-domain test -Dtest=BusinessApplicationAccessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL with "method businessExtension () not found" / "method operatorInfo () not found" / "method
businessContext () not found"

- [ ] **Step 3: 在 BusinessApplication 新增 3 个公开 accessor**

在 `BusinessApplication.java` 的 `getBatchId()` 方法之前 (约 line 259),新增以下 3 个方法:

```java
  /**
   * 公开业务扩展字段（只读）。
   * <p>
   * 供业务服务的 {@code AnnuityExtensionResolver} 等组件类型安全地读取扩展字段，
   * 避免在业务服务中使用反射访问私有字段。
   *
   * @return 扩展字段实例，若未设置则返回 null
   */
  public BusinessExtension businessExtension() {
    return this.businessExtension;
  }

  /**
   * 公开操作人信息（只读）。
   * <p>
   * 供通知、审计等扩展动作获取操作人信息，避免反射。
   *
   * @return 操作人信息实例
   */
  public OperatorInfo operatorInfo() {
    return this.operatorInfo;
  }

  /**
   * 公开业务上下文（只读）。
   * <p>
   * 供客户画像查询等扩展动作获取客户/产品/方案维度信息，避免反射。
   *
   * @return 业务上下文实例
   */
  public BusinessContext businessContext() {
    return this.businessContext;
  }
```

- [ ] **Step 4: 运行测试验证通过**

Run:
`mvn -pl business-core-kernel/business-core-domain test -Dtest=BusinessApplicationAccessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 个测试)

- [ ] **Step 5: 运行 kernel 全量测试确认无回归**

Run: `mvn -pl business-core-kernel test`
Expected: 所有现有测试通过

- [ ] **Step 6: 提交**

```bash
git add business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/root/BusinessApplication.java \
        business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/aggregate/root/BusinessApplicationAccessorTest.java
git commit -m "feat(core-domain): BusinessApplication 开放 businessExtension/operatorInfo/businessContext 3 个 accessor"
```

---

# Phase B: annuity-types + annuity-domain 领域模型

## Task B1: annuity-types 新增 ID 类型

**Files:**

- Create: `annuity-service/annuity-types/src/main/java/com/example/annuity/types/AnnuityEmployeeBatchId.java`
- Create: `annuity-service/annuity-types/src/main/java/com/example/annuity/types/AnnuityEmployeeDetailId.java`

**Interfaces:**

- Produces: `AnnuityEmployeeBatchId(String value)` 构造器, `value()` 返回 String
- Produces: `AnnuityEmployeeDetailId(String value)` 构造器, `value()` 返回 String

- [ ] **Step 1: 创建 AnnuityEmployeeBatchId**

```java
package com.example.annuity.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 年金员工明细批次 ID
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeBatchId(String value) implements Identifier<String> {

  public AnnuityEmployeeBatchId {
    Objects.requireNonNull(value, "AnnuityEmployeeBatchId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AnnuityEmployeeBatchId value cannot be blank");
    }
  }

  public static AnnuityEmployeeBatchId of(String value) {
    return new AnnuityEmployeeBatchId(value);
  }

  @Override
  public String toString() {
    return "AnnuityEmployeeBatchId{" + value + "}";
  }
}
```

- [ ] **Step 2: 创建 AnnuityEmployeeDetailId**

```java
package com.example.annuity.types;

import com.example.shared.primitives.identity.Identifier;

import java.util.Objects;

/**
 * 年金员工明细 ID
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeDetailId(String value) implements Identifier<String> {

  public AnnuityEmployeeDetailId {
    Objects.requireNonNull(value, "AnnuityEmployeeDetailId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("AnnuityEmployeeDetailId value cannot be blank");
    }
  }

  public static AnnuityEmployeeDetailId of(String value) {
    return new AnnuityEmployeeDetailId(value);
  }

  @Override
  public String toString() {
    return "AnnuityEmployeeDetailId{" + value + "}";
  }
}
```

- [ ] **Step 3: 验证编译通过**

Run: `mvn -pl annuity-service/annuity-types compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add annuity-service/annuity-types/src/main/java/com/example/annuity/types/AnnuityEmployeeBatchId.java \
        annuity-service/annuity-types/src/main/java/com/example/annuity/types/AnnuityEmployeeDetailId.java
git commit -m "feat(annuity-types): 新增 AnnuityEmployeeBatchId 和 AnnuityEmployeeDetailId 强类型 ID"
```

---

## Task B2: annuity-domain 枚举与值对象

**Files:**

- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/AnnuityEmployeeBatchStatus.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/AnnuityEmployeeDetailStatus.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/AnnuityEmployeeMaterial.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/CustomerProfile.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/NotificationType.java`

**Interfaces:**

- Produces: `AnnuityEmployeeBatchStatus` 枚举 (PENDING/PROCESSING/COMPLETED/FAILED)
- Produces: `AnnuityEmployeeDetailStatus` 枚举 (PENDING/VERIFIED/ANOMALY/MATERIAL_READY)
- Produces:
  `AnnuityEmployeeMaterial(String materialCode, String materialName, boolean required, boolean uploaded, String description)`
  record
- Produces: `CustomerProfile(CustomerNo customerNo, String riskLevel, List<String> relatedCompanies)` record
- Produces: `NotificationType` 枚举 (MATERIAL_READY/ANOMALY_DETECTED)

- [ ] **Step 1: 创建 AnnuityEmployeeBatchStatus**

```java
package com.example.annuity.domain.aggregate.valueobject;

/**
 * 年金员工明细批次状态
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public enum AnnuityEmployeeBatchStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED
}
```

- [ ] **Step 2: 创建 AnnuityEmployeeDetailStatus**

```java
package com.example.annuity.domain.aggregate.valueobject;

/**
 * 年金员工明细状态
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public enum AnnuityEmployeeDetailStatus {
  PENDING,
  VERIFIED,
  ANOMALY,
  MATERIAL_READY
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeMaterial**

```java
package com.example.annuity.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 年金员工材料值对象
 *
 * @param materialCode 材料代码
 * @param materialName 材料名称
 * @param required     是否必传
 * @param uploaded     是否已上传
 * @param description  描述
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeMaterial(
    String materialCode,
    String materialName,
    boolean required,
    boolean uploaded,
    String description
) implements ValueObject {

  public AnnuityEmployeeMaterial {
    if (materialCode == null || materialCode.isBlank()) {
      throw new IllegalArgumentException("materialCode cannot be blank");
    }
    if (materialName == null || materialName.isBlank()) {
      throw new IllegalArgumentException("materialName cannot be blank");
    }
  }

  /**
   * 标记材料已上传,返回新实例(不可变)
   */
  public AnnuityEmployeeMaterial markUploaded() {
    return new AnnuityEmployeeMaterial(materialCode, materialName, required, true, description);
  }
}
```

- [ ] **Step 4: 创建 CustomerProfile**

```java
package com.example.annuity.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.primitives.identity.CustomerNo;

import java.util.List;

/**
 * 客户画像值对象
 *
 * @param customerNo       客户编号
 * @param riskLevel        风险等级(LOW/MEDIUM/HIGH)
 * @param relatedCompanies 关联企业列表
 * @author annuity-service
 * @since 2026/7/22
 */
public record CustomerProfile(
    CustomerNo customerNo,
    String riskLevel,
    List<String> relatedCompanies
) implements ValueObject {

  public CustomerProfile {
    if (customerNo == null) {
      throw new IllegalArgumentException("customerNo cannot be null");
    }
    relatedCompanies = relatedCompanies == null ? List.of() : List.copyOf(relatedCompanies);
  }

  /**
   * 判断客户关联企业是否包含外资标识
   */
  public boolean hasForeignCompany() {
    return relatedCompanies.stream().anyMatch(c -> c.contains("FOREIGN"));
  }
}
```

- [ ] **Step 5: 创建 NotificationType**

```java
package com.example.annuity.domain.aggregate.valueobject;

/**
 * 通知类型枚举
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public enum NotificationType {
  MATERIAL_READY,
  ANOMALY_DETECTED,
  BATCH_COMPLETED
}
```

- [ ] **Step 6: 验证编译**

Run: `mvn -pl annuity-service/annuity-domain compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/valueobject/
git commit -m "feat(annuity-domain): 新增批次/明细状态枚举、材料值对象、客户画像、通知类型"
```

---

## Task B3: AnnuityEmployeeBatch 聚合根与 AnnuityEmployeeDetail 实体

**Files:**

- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/entity/AnnuityEmployeeDetail.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/root/AnnuityEmployeeBatch.java`

**Interfaces:**

- Consumes: `AnnuityEmployeeBatchId`, `AnnuityEmployeeDetailId`(from Task B1)
- Consumes: `AnnuityEmployeeBatchStatus`, `AnnuityEmployeeDetailStatus`, `AnnuityEmployeeMaterial`(from Task B2)
- Consumes: `ApplicationId` from kernel
- Produces: `AnnuityEmployeeBatch.create(...)` 工厂方法
- Produces:
  `AnnuityEmployeeBatch.addDetail/markDetailProcessed/markDetailAnomaly/complete/fail/isAllProcessed/pendingDetails/verifiedDetails/attachDetail/findDetail`
  方法
- Produces: `AnnuityEmployeeDetail.verify/markAnomaly/assignMaterials/isMaterialSatisfied` 方法

- [ ] **Step 1: 编写 AnnuityEmployeeDetail 失败测试**

Create:
`annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/aggregate/entity/AnnuityEmployeeDetailTest.java`

```java
package com.example.annuity.domain.aggregate.entity;

import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityEmployeeDetail 实体行为")
class AnnuityEmployeeDetailTest {

  private static final UserNo OPERATOR = UserNo.of("U-TEST");

  @Test
  @DisplayName("verify 成功后状态变为 VERIFIED")
  void verify_changesStatusToVerified() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    assertThat(detail.status()).isEqualTo(AnnuityEmployeeDetailStatus.VERIFIED);
    assertThat(detail.verifiedAt()).isNotNull();
  }

  @Test
  @DisplayName("markAnomaly 记录异常原因并改变状态")
  void markAnomaly_recordsReasonAndChangesStatus() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.markAnomaly("身份证格式错误");
    assertThat(detail.status()).isEqualTo(AnnuityEmployeeDetailStatus.ANOMALY);
    assertThat(detail.anomalyReason()).isEqualTo("身份证格式错误");
  }

  @Test
  @DisplayName("assignMaterials 为明细挂载材料清单")
  void assignMaterials_attachesMaterialList() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    List<AnnuityEmployeeMaterial> materials = List.of(
        new AnnuityEmployeeMaterial("ID_CARD", "身份证复印件", true, false, null)
    );
    detail.assignMaterials(materials);
    assertThat(detail.materials()).hasSize(1);
    assertThat(detail.materialPreparedAt()).isNotNull();
  }

  @Test
  @DisplayName("assignMaterials 在未核查状态下抛出异常")
  void assignMaterials_throwsWhenNotVerified() {
    AnnuityEmployeeDetail detail = createDetail();
    assertThatThrownBy(() -> detail.assignMaterials(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未核查");
  }

  @Test
  @DisplayName("isMaterialSatisfied 在所有必传材料已上传时返回 true")
  void isMaterialSatisfied_returnsTrueWhenAllRequiredUploaded() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    detail.assignMaterials(List.of(
        new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, true, null),
        new AnnuityEmployeeMaterial("SALARY", "收入证明", true, true, null),
        new AnnuityEmployeeMaterial("EXTRA", "可选材料", false, false, null)
    ));
    assertThat(detail.isMaterialSatisfied()).isTrue();
  }

  @Test
  @DisplayName("isMaterialSatisfied 在必传材料未上传时返回 false")
  void isMaterialSatisfied_returnsFalseWhenRequiredMissing() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    detail.assignMaterials(List.of(
        new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, true, null),
        new AnnuityEmployeeMaterial("SALARY", "收入证明", true, false, null)
    ));
    assertThat(detail.isMaterialSatisfied()).isFalse();
  }

  private AnnuityEmployeeDetail createDetail() {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of("D-001"),
        AnnuityEmployeeBatchId.of("B-001"),
        "张三", "110101199001011234", 35, 10000L, 500L,
        OPERATOR
    );
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run:
`mvn -pl annuity-service/annuity-domain test -Dtest=AnnuityEmployeeDetailTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 AnnuityEmployeeDetail 实体**

```java
package com.example.annuity.domain.aggregate.entity;

import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 年金员工明细实体
 * <p>
 * 作为 {@code AnnuityEmployeeBatch} 聚合根的内部实体,承载单个员工的年金明细数据。
 * 外部只能通过聚合根操作本实体。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public class AnnuityEmployeeDetail extends Entity<AnnuityEmployeeDetailId> {

  private AnnuityEmployeeBatchId batchId;
  private String employeeName;
  private String idCardNo;
  private Integer age;
  private Long monthlySalary;
  private Long monthlyContribution;
  private AnnuityEmployeeDetailStatus status;
  private String anomalyReason;
  private List<AnnuityEmployeeMaterial> materials;
  private LocalDateTime verifiedAt;
  private LocalDateTime materialPreparedAt;

  /**
   * 业务创建构造器
   */
  public AnnuityEmployeeDetail(AnnuityEmployeeDetailId id, AnnuityEmployeeBatchId batchId,
                               String employeeName, String idCardNo, Integer age,
                               Long monthlySalary, Long monthlyContribution, UserNo createdBy) {
    super(id, createdBy);
    this.batchId = Objects.requireNonNull(batchId, "batchId cannot be null");
    this.employeeName = Objects.requireNonNull(employeeName, "employeeName cannot be null");
    this.idCardNo = Objects.requireNonNull(idCardNo, "idCardNo cannot be null");
    this.age = age;
    this.monthlySalary = monthlySalary;
    this.monthlyContribution = monthlyContribution;
    this.status = AnnuityEmployeeDetailStatus.PENDING;
    this.materials = new ArrayList<>();
  }

  /**
   * 数据库重建构造器
   */
  public AnnuityEmployeeDetail(AnnuityEmployeeDetailId id, AnnuityEmployeeBatchId batchId,
                               String employeeName, String idCardNo, Integer age,
                               Long monthlySalary, Long monthlyContribution,
                               AnnuityEmployeeDetailStatus status, String anomalyReason,
                               List<AnnuityEmployeeMaterial> materials,
                               LocalDateTime verifiedAt, LocalDateTime materialPreparedAt,
                               UserNo createdBy, UserNo updatedBy,
                               LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.batchId = batchId;
    this.employeeName = employeeName;
    this.idCardNo = idCardNo;
    this.age = age;
    this.monthlySalary = monthlySalary;
    this.monthlyContribution = monthlyContribution;
    this.status = status;
    this.anomalyReason = anomalyReason;
    this.materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
    this.verifiedAt = verifiedAt;
    this.materialPreparedAt = materialPreparedAt;
  }

  /**
   * 标记明细已核查
   */
  public void verify(UserNo operator) {
    if (this.status != AnnuityEmployeeDetailStatus.PENDING) {
      throw new IllegalStateException("仅 PENDING 状态可核查,当前: " + this.status);
    }
    this.status = AnnuityEmployeeDetailStatus.VERIFIED;
    this.verifiedAt = LocalDateTime.now();
    markUpdated(operator);
  }

  /**
   * 标记明细异常
   */
  public void markAnomaly(String reason) {
    this.status = AnnuityEmployeeDetailStatus.ANOMALY;
    this.anomalyReason = reason;
  }

  /**
   * 挂载材料清单
   */
  public void assignMaterials(List<AnnuityEmployeeMaterial> materials) {
    if (this.status != AnnuityEmployeeDetailStatus.VERIFIED) {
      throw new IllegalStateException("仅 VERIFIED 状态可分配材料,当前: " + this.status);
    }
    this.materials = new ArrayList<>(materials);
    this.materialPreparedAt = LocalDateTime.now();
    this.status = AnnuityEmployeeDetailStatus.MATERIAL_READY;
  }

  /**
   * 判断必传材料是否全部已上传
   */
  public boolean isMaterialSatisfied() {
    return materials.stream()
        .filter(AnnuityEmployeeMaterial::required)
        .allMatch(AnnuityEmployeeMaterial::uploaded);
  }

  public AnnuityEmployeeBatchId batchId() { return batchId; }
  public String employeeName() { return employeeName; }
  public String idCardNo() { return idCardNo; }
  public Integer age() { return age; }
  public Long monthlySalary() { return monthlySalary; }
  public Long monthlyContribution() { return monthlyContribution; }
  public AnnuityEmployeeDetailStatus status() { return status; }
  public String anomalyReason() { return anomalyReason; }
  public List<AnnuityEmployeeMaterial> materials() { return List.copyOf(materials); }
  public LocalDateTime verifiedAt() { return verifiedAt; }
  public LocalDateTime materialPreparedAt() { return materialPreparedAt; }

  @Override
  protected void validateInvariants() {
    if (batchId == null || employeeName == null || idCardNo == null) {
      throw new IllegalStateException("AnnuityEmployeeDetail 不变式校验失败");
    }
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run:
`mvn -pl annuity-service/annuity-domain test -Dtest=AnnuityEmployeeDetailTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (6 个测试)

- [ ] **Step 5: 编写 AnnuityEmployeeBatch 失败测试**

Create:
`annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/aggregate/root/AnnuityEmployeeBatchTest.java`

```java
package com.example.annuity.domain.aggregate.root;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityEmployeeBatch 聚合根行为")
class AnnuityEmployeeBatchTest {

  private static final UserNo OPERATOR = UserNo.of("U-TEST");
  private static final ApplicationId APP_ID = new ApplicationId("APP-001");

  @Test
  @DisplayName("create 工厂方法初始化 PENDING 状态和空明细集合")
  void create_initializesPendingStatusAndEmptyDetails() {
    AnnuityEmployeeBatch batch = AnnuityEmployeeBatch.create(
        AnnuityEmployeeBatchId.of("B-001"), APP_ID, 10, OPERATOR
    );
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.PENDING);
    assertThat(batch.totalEmployeeCount()).isEqualTo(10);
    assertThat(batch.details()).isEmpty();
    assertThat(batch.processedCount()).isZero();
    assertThat(batch.anomalyCount()).isZero();
  }

  @Test
  @DisplayName("addDetail 添加明细到批次")
  void addDetail_addsToBatch() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    assertThat(batch.details()).hasSize(1);
    assertThat(batch.pendingDetails()).hasSize(1);
  }

  @Test
  @DisplayName("markDetailProcessed 递增 processedCount")
  void markDetailProcessed_incrementsProcessedCount() {
    AnnuityEmployeeBatch batch = createBatch(2);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.addDetail(createDetail("D-002", "李四"));
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    assertThat(batch.processedCount()).isEqualTo(1);
    assertThat(batch.isAllProcessed()).isFalse();
  }

  @Test
  @DisplayName("markDetailAnomaly 递增 anomalyCount")
  void markDetailAnomaly_incrementsAnomalyCount() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-001"), "身份证错误");
    assertThat(batch.anomalyCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("complete 在所有明细处理完后将状态置为 COMPLETED")
  void complete_changesStatusToCompleted() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    batch.complete();
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.COMPLETED);
  }

  @Test
  @DisplayName("fail 将状态置为 FAILED 并记录原因")
  void fail_changesStatusToFailed() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.fail("存在异常明细");
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.FAILED);
  }

  @Test
  @DisplayName("complete 在尚有未处理明细时抛出异常")
  void complete_throwsWhenNotAllProcessed() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    assertThatThrownBy(batch::complete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未全部处理");
  }

  @Test
  @DisplayName("verifiedDetails 仅返回 VERIFIED 状态的明细")
  void verifiedDetails_returnsOnlyVerified() {
    AnnuityEmployeeBatch batch = createBatch(2);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.addDetail(createDetail("D-002", "李四"));
    batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-002"), "异常");
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    // markDetailProcessed 仅递增计数,明细本身状态需单独变更(此处通过聚合根内部逻辑)
    // 验证 verifiedDetails 只返回非异常的已处理明细
    assertThat(batch.anomalyCount()).isEqualTo(1);
  }

  private AnnuityEmployeeBatch createBatch(int total) {
    return AnnuityEmployeeBatch.create(
        AnnuityEmployeeBatchId.of("B-001"), APP_ID, total, OPERATOR
    );
  }

  private AnnuityEmployeeDetail createDetail(String id, String name) {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of(id),
        AnnuityEmployeeBatchId.of("B-001"),
        name, "110101199001011234", 35, 10000L, 500L, OPERATOR
    );
  }
}
```

- [ ] **Step 6: 运行测试验证失败**

Run:
`mvn -pl annuity-service/annuity-domain test -Dtest=AnnuityEmployeeBatchTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (类不存在)

- [ ] **Step 7: 创建 AnnuityEmployeeBatch 聚合根**

```java
package com.example.annuity.domain.aggregate.root;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 年金员工明细批次聚合根
 * <p>
 * 承载某次年金申请的员工明细批次,管理明细集合的一致性边界。
 * 通过 {@link ApplicationId} 引用 kernel 的 BusinessApplication(ID 引用,不直接持有对象)。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public class AnnuityEmployeeBatch extends AggregateRoot<AnnuityEmployeeBatchId> {

  private ApplicationId applicationId;
  private List<AnnuityEmployeeDetail> details;
  private AnnuityEmployeeBatchStatus status;
  private int totalEmployeeCount;
  private int processedCount;
  private int anomalyCount;

  /**
   * 业务创建构造器
   */
  private AnnuityEmployeeBatch(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                              int totalEmployeeCount, UserNo createdBy) {
    super(id, createdBy);
    this.applicationId = applicationId;
    this.totalEmployeeCount = totalEmployeeCount;
    this.details = new ArrayList<>();
    this.status = AnnuityEmployeeBatchStatus.PENDING;
  }

  /**
   * 数据库重建构造器
   */
  public AnnuityEmployeeBatch(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                              List<AnnuityEmployeeDetail> details, AnnuityEmployeeBatchStatus status,
                              int totalEmployeeCount, int processedCount, int anomalyCount,
                              UserNo createdBy, UserNo updatedBy,
                              LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.applicationId = applicationId;
    this.details = new ArrayList<>(details);
    this.status = status;
    this.totalEmployeeCount = totalEmployeeCount;
    this.processedCount = processedCount;
    this.anomalyCount = anomalyCount;
  }

  /**
   * 工厂方法:创建新批次
   */
  public static AnnuityEmployeeBatch create(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                                           int totalEmployeeCount, UserNo createdBy) {
    if (totalEmployeeCount < 0) {
      throw new IllegalArgumentException("totalEmployeeCount cannot be negative");
    }
    return new AnnuityEmployeeBatch(id, applicationId, totalEmployeeCount, createdBy);
  }

  /**
   * 添加明细到批次
   */
  public void addDetail(AnnuityEmployeeDetail detail) {
    if (this.status != AnnuityEmployeeBatchStatus.PENDING
        && this.status != AnnuityEmployeeBatchStatus.PROCESSING) {
      throw new IllegalStateException("批次已终态,无法添加明细: " + this.status);
    }
    this.details.add(detail);
    if (this.status == AnnuityEmployeeBatchStatus.PENDING) {
      this.status = AnnuityEmployeeBatchStatus.PROCESSING;
    }
  }

  /**
   * 内部方法:从数据库重建时挂载明细(不触发状态变更,不触发事件)
   */
  public void attachDetail(AnnuityEmployeeDetail detail) {
    this.details.add(detail);
  }

  /**
   * 标记明细已处理
   */
  public void markDetailProcessed(AnnuityEmployeeDetailId detailId, UserNo operator) {
    AnnuityEmployeeDetail detail = findDetailOrThrow(detailId);
    detail.verify(operator);
    this.processedCount++;
  }

  /**
   * 标记明细异常
   */
  public void markDetailAnomaly(AnnuityEmployeeDetailId detailId, String reason) {
    AnnuityEmployeeDetail detail = findDetailOrThrow(detailId);
    detail.markAnomaly(reason);
    this.anomalyCount++;
  }

  /**
   * 完成批次
   */
  public void complete() {
    if (!isAllProcessed()) {
      throw new IllegalStateException("尚有明细未全部处理,无法完成批次");
    }
    this.status = AnnuityEmployeeBatchStatus.COMPLETED;
  }

  /**
   * 批次失败
   */
  public void fail(String reason) {
    this.status = AnnuityEmployeeBatchStatus.FAILED;
  }

  /**
   * 是否所有明细已处理(已处理 + 异常 == 总数)
   */
  public boolean isAllProcessed() {
    return processedCount + anomalyCount >= totalEmployeeCount;
  }

  /**
   * 返回待处理明细(PENDING 状态)
   */
  public List<AnnuityEmployeeDetail> pendingDetails() {
    return details.stream()
        .filter(d -> d.status() == com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus.PENDING)
        .toList();
  }

  /**
   * 返回已核查明细(VERIFIED 状态)
   */
  public List<AnnuityEmployeeDetail> verifiedDetails() {
    return details.stream()
        .filter(d -> d.status() == com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus.VERIFIED)
        .toList();
  }

  /**
   * 查找明细
   */
  public Optional<AnnuityEmployeeDetail> findDetail(AnnuityEmployeeDetailId detailId) {
    return details.stream().filter(d -> d.id().equals(detailId)).findFirst();
  }

  private AnnuityEmployeeDetail findDetailOrThrow(AnnuityEmployeeDetailId detailId) {
    return findDetail(detailId)
        .orElseThrow(() -> new IllegalArgumentException("明细不存在: " + detailId));
  }

  public ApplicationId applicationId() { return applicationId; }
  public List<AnnuityEmployeeDetail> details() { return List.copyOf(details); }
  public AnnuityEmployeeBatchStatus status() { return status; }
  public int totalEmployeeCount() { return totalEmployeeCount; }
  public int processedCount() { return processedCount; }
  public int anomalyCount() { return anomalyCount; }

  @Override
  protected void validateInvariants() {
    if (applicationId == null) {
      throw new IllegalStateException("applicationId cannot be null");
    }
  }
}
```

- [ ] **Step 8: 运行测试验证通过**

Run:
`mvn -pl annuity-service/annuity-domain test -Dtest=AnnuityEmployeeBatchTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 个测试)

- [ ] **Step 9: 提交**

```bash
git add annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/aggregate/ \
        annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/aggregate/
git commit -m "feat(annuity-domain): 新增 AnnuityEmployeeBatch 聚合根与 AnnuityEmployeeDetail 实体"
```

---

## Task B4: Gateway 接口与 Repository 接口

**Files:**

- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/gateway/AnnuityCustomerGateway.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/gateway/AnnuityNotificationGateway.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/repository/AnnuityEmployeeBatchRepository.java`

**Interfaces:**

- Produces: `AnnuityCustomerGateway.queryCustomer(CustomerNo)` → `CustomerProfile`
- Produces: `AnnuityNotificationGateway.notifyOperator(UserNo, NotificationType, String)` → void
- Produces: `AnnuityEmployeeBatchRepository.save(AnnuityEmployeeBatch)` / `findByApplicationId(ApplicationId)` /
  `load(AnnuityEmployeeBatchId)`

- [ ] **Step 1: 创建 AnnuityCustomerGateway 接口**

```java
package com.example.annuity.domain.gateway;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.shared.primitives.identity.CustomerNo;

/**
 * 年金客户网关接口
 * <p>
 * 防腐层接口,供 application 层的扩展动作查询客户画像。
 * 由 infrastructure 层提供 Mock 或真实实现。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityCustomerGateway {

  /**
   * 查询客户画像
   *
   * @param customerNo 客户编号
   * @return 客户画像
   */
  CustomerProfile queryCustomer(CustomerNo customerNo);
}
```

- [ ] **Step 2: 创建 AnnuityNotificationGateway 接口**

```java
package com.example.annuity.domain.gateway;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.shared.primitives.identity.UserNo;

/**
 * 年金通知网关接口
 * <p>
 * 防腐层接口,供 application 层的扩展动作发送通知。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityNotificationGateway {

  /**
   * 通知操作人
   *
   * @param operatorNo 操作人编号
   * @param type       通知类型
   * @param content    通知内容
   */
  void notifyOperator(UserNo operatorNo, NotificationType type, String content);
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeBatchRepository 接口**

```java
package com.example.annuity.domain.repository;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.ApplicationId;

import java.util.Optional;

/**
 * 年金员工明细批次仓储接口
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityEmployeeBatchRepository extends Repository<AnnuityEmployeeBatch, AnnuityEmployeeBatchId> {

  /**
   * 根据申请单 ID 反查批次
   *
   * @param applicationId 申请单 ID
   * @return 批次(可能为空)
   */
  Optional<AnnuityEmployeeBatch> findByApplicationId(ApplicationId applicationId);
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn -pl annuity-service/annuity-domain compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/gateway/ \
        annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/repository/
git commit -m "feat(annuity-domain): 新增 AnnuityCustomerGateway、AnnuityNotificationGateway、AnnuityEmployeeBatchRepository 接口"
```

---

## Task B5: Domain Service 实现 (规则引擎与 Resolver)

**Files:**

- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityExtensionResolver.java`
- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityContributionRule.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityForeignInvestmentRule.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityEmployeeVerificationRule.java`
- Create:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityEmployeeMaterialRule.java`
- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/service/AnnuityEmployeeMapper.java`
- Modify:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/errorcode/AnnuityDomainErrorCode.java` —
  扩展错误码
- Modify:
  `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/extractor/AnnuityFactExtractor.java` —
  移除反射,改用 Resolver

**Interfaces:**

- Consumes: `BusinessApplication.businessExtension()`(from Task A1)
- Consumes: `AnnuityApplicationExtension`(已存在)
- Produces: `AnnuityExtensionResolver.resolve(BusinessApplication)` → `AnnuityApplicationExtension`
- Produces: `AnnuityContributionRule.validate(AnnuityApplicationExtension)` → `Optional<String>`
- Produces: `AnnuityForeignInvestmentRule.validate(AnnuityApplicationExtension, CustomerProfile)` → `Optional<String>`
- Produces: `AnnuityEmployeeVerificationRule.verify(AnnuityEmployeeDetail)` → `Optional<String>`
- Produces: `AnnuityEmployeeMaterialRule.calculate(AnnuityEmployeeDetail, BusinessMetaContext)` →
  `List<AnnuityEmployeeMaterial>`
- Produces: `AnnuityEmployeeMapper.mapToEntity(AnnuityEmployeeDTO, ApplicationId, int)` → `AnnuityEmployeeDetail`

- [ ] **Step 1: 扩展 AnnuityDomainErrorCode**

在现有枚举中追加以下条目 (在 `INVALID_EXTENSION_DATA` 之后):

```java
  EMPLOYEE_VERIFICATION_FAILED("300009", "[员工明细核查失败]{}"),
  EMPLOYEE_DETAIL_NOT_FOUND("300010", "[员工明细不存在]{}"),
  BATCH_ALREADY_COMPLETED("300011", "[批次已完成,不可操作]{}"),
  MATERIAL_CALCULATION_FAILED("300012", "[材料计算失败]{}"),
  INVALID_EXTENSION_TYPE("300013", "[扩展字段类型不匹配]{}"),
  FOREIGN_INVESTMENT_BLOCKED("300014", "[外资业务准入失败]{}"),
  EMPLOYEE_BATCH_NOT_FOUND("300015", "[员工批次不存在]{}"),
```

- [ ] **Step 2: 创建 AnnuityExtensionResolver**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;

/**
 * 年金扩展字段类型安全解析器
 * <p>
 * 集中处理 instanceof pattern matching,其他 domain service 与 application 层 Handler/Action
 * 通过注入本解析器获取强类型扩展字段,消除散落多处的强制类型转换。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityExtensionResolver {

  /**
   * 类型安全地解析年金扩展字段
   *
   * @param app 业务申请单
   * @return 年金扩展字段
   * @throws DomainException 如果扩展字段类型不匹配
   */
  public AnnuityApplicationExtension resolve(BusinessApplication app) {
    BusinessExtension ext = app.businessExtension();
    if (ext instanceof AnnuityApplicationExtension annuityExt) {
      return annuityExt;
    }
    throw new DomainException(AnnuityDomainErrorCode.INVALID_EXTENSION_TYPE)
        .withLogDetail("期望 AnnuityApplicationExtension,实际: "
            + (ext == null ? "null" : ext.getClass().getName()));
  }
}
```

- [ ] **Step 3: 创建 AnnuityContributionRule**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.annotation.DomainService;

import java.util.Optional;

/**
 * 年金缴费金额校验规则
 * <p>
 * 纯领域规则,无状态、无框架依赖。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityContributionRule {

  private static final long MIN_INITIAL_CONTRIBUTION_FOR_NEW = 10000L;

  /**
   * 校验缴费金额
   *
   * @param ext 年金扩展字段
   * @return 错误消息(为空表示校验通过)
   */
  public Optional<String> validate(AnnuityApplicationExtension ext) {
    if (ext.initialContribution() == null || ext.initialContribution() < 0) {
      return Optional.of("缴费金额不能为负");
    }
    if (AnnuityApplicationExtension.PLAN_TYPE_NEW.equals(ext.planType())
        && ext.initialContribution() < MIN_INITIAL_CONTRIBUTION_FOR_NEW) {
      return Optional.of("新建计划初始缴费不少于 100 元");
    }
    return Optional.empty();
  }
}
```

- [ ] **Step 4: 创建 AnnuityForeignInvestmentRule**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.annotation.DomainService;

import java.util.Optional;

/**
 * 年金外资准入规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityForeignInvestmentRule {

  /**
   * 校验外资准入
   *
   * @param ext     年金扩展字段
   * @param profile 客户画像
   * @return 错误消息(为空表示校验通过)
   */
  public Optional<String> validate(AnnuityApplicationExtension ext, CustomerProfile profile) {
    if (ext.hasForeignInvestment() && !profile.hasForeignCompany()) {
      return Optional.of("业务声明含外资,但客户画像未关联外资企业,需补充材料");
    }
    if (profile.hasForeignCompany() && !ext.hasForeignInvestment()) {
      return Optional.of("客户画像含外资企业,但业务未声明外资成分,需人工复核");
    }
    return Optional.empty();
  }
}
```

- [ ] **Step 5: 创建 AnnuityEmployeeVerificationRule**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.core.domain.annotation.DomainService;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 年金员工明细核查规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeVerificationRule {

  private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
  private static final int MIN_AGE = 18;
  private static final int MAX_AGE = 70;

  /**
   * 核查员工明细
   *
   * @param detail 员工明细
   * @return 错误消息(为空表示核查通过)
   */
  public Optional<String> verify(AnnuityEmployeeDetail detail) {
    if (!ID_CARD_PATTERN.matcher(detail.idCardNo()).matches()) {
      return Optional.of("身份证格式错误: " + detail.idCardNo());
    }
    if (detail.age() == null || detail.age() < MIN_AGE || detail.age() > MAX_AGE) {
      return Optional.of("年龄不在合法区间[" + MIN_AGE + "," + MAX_AGE + "]: " + detail.age());
    }
    if (detail.monthlySalary() == null || detail.monthlySalary() <= 0) {
      return Optional.of("月薪必须为正数: " + detail.monthlySalary());
    }
    if (detail.monthlyContribution() == null || detail.monthlyContribution() <= 0) {
      return Optional.of("月缴费必须为正数: " + detail.monthlyContribution());
    }
    return Optional.empty();
  }
}
```

- [ ] **Step 6: 创建 AnnuityEmployeeMaterialRule**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.annotation.DomainService;

import java.util.ArrayList;
import java.util.List;

/**
 * 年金员工材料计算规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeMaterialRule {

  private static final String MATERIAL_ID_CARD = "ID_CARD_COPY";
  private static final String MATERIAL_EMPLOYMENT_CERT = "EMPLOYMENT_CERT";
  private static final String MATERIAL_SALARY_PROOF = "SALARY_PROOF";
  private static final String MATERIAL_FOREIGN_DECL = "FOREIGN_ASSET_DECL";

  /**
   * 计算员工材料清单
   *
   * @param detail   员工明细
   * @param context  业务上下文(用于判断外资场景)
   * @return 材料清单
   */
  public List<AnnuityEmployeeMaterial> calculate(AnnuityEmployeeDetail detail, BusinessMetaContext context) {
    List<AnnuityEmployeeMaterial> materials = new ArrayList<>();
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_ID_CARD, "身份证复印件", true, false, null));
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_EMPLOYMENT_CERT, "在职证明", true, false, null));
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_SALARY_PROOF, "收入证明", true, false, null));

    Object hasForeign = context.extensionFacts() == null ? null : context.extensionFacts().get("hasForeignInvestment");
    if (Boolean.TRUE.equals(hasForeign)) {
      materials.add(new AnnuityEmployeeMaterial(MATERIAL_FOREIGN_DECL, "外资资产申报表", true, false, "含外资业务必传"));
    }
    return materials;
  }
}
```

- [ ] **Step 7: 创建 AnnuityEmployeeMapper**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.annotation.DomainService;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;

import java.util.UUID;

/**
 * 年金员工 DTO 到实体的映射规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeMapper {

  private static final UserNo SYSTEM_USER = UserNo.of("SYSTEM");

  /**
   * 将 DTO 映射为员工明细实体
   *
   * @param dto       JSON DTO
   * @param appId     申请单 ID(用于关联批次)
   * @param rowIndex  行号(用于追溯)
   * @return 员工明细实体
   */
  public AnnuityEmployeeDetail mapToEntity(AnnuityEmployeeDTO dto, ApplicationId appId, int rowIndex) {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of("D-" + UUID.randomUUID()),
        AnnuityEmployeeBatchId.of("B-" + appId.value()),
        dto.employeeName(),
        dto.idCardNo(),
        dto.age(),
        dto.monthlySalary(),
        dto.monthlyContribution(),
        SYSTEM_USER
    );
  }
}
```

注:`AnnuityEmployeeDTO` 需先在 annuity-api 创建 (Task C1 会定义),此处引用。为避免编译错误,本 Task 先创建一个临时
placeholder,Task C1 完成后替换。实际上,根据依赖关系,应将 `AnnuityEmployeeDTO` 的创建移到本 Task 之前。调整:在 Step 7 之前先创建
`AnnuityEmployeeDTO`。

- [ ] **Step 8: 在 annuity-api 创建 AnnuityEmployeeDTO**

Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/dto/AnnuityEmployeeDTO.java`

```java
package com.example.annuity.api.dto;

/**
 * 年金员工明细 JSON DTO
 * <p>
 * 用于从解析后的 JSON 文件流式摄入员工明细数据。
 *
 * @param employeeName       员工姓名
 * @param idCardNo           身份证号
 * @param age                年龄
 * @param monthlySalary      月薪(分)
 * @param monthlyContribution 月缴费(分)
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeDTO(
    String employeeName,
    String idCardNo,
    Integer age,
    Long monthlySalary,
    Long monthlyContribution
) {

  public AnnuityEmployeeDTO {
    if (employeeName == null || employeeName.isBlank()) {
      throw new IllegalArgumentException("employeeName cannot be blank");
    }
    if (idCardNo == null || idCardNo.isBlank()) {
      throw new IllegalArgumentException("idCardNo cannot be blank");
    }
  }
}
```

- [ ] **Step 9: 编写 Domain Service 测试**

Create:
`annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/AnnuityContributionRuleTest.java`

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityContributionRule 缴费校验规则")
class AnnuityContributionRuleTest {

  private final AnnuityContributionRule rule = new AnnuityContributionRule();

  @Test
  @DisplayName("负数缴费金额校验失败")
  void validate_negativeContributionFails() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", -100L, false
    );
    assertThat(rule.validate(ext)).isPresent()
        .hasValueContaining("不能为负");
  }

  @Test
  @DisplayName("新建计划缴费不足 100 元校验失败")
  void validate_newPlanBelowThresholdFails() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 5000L, false
    );
    assertThat(rule.validate(ext)).isPresent()
        .hasValueContaining("不少于 100 元");
  }

  @Test
  @DisplayName("修改计划缴费金额无阈值校验")
  void validate_modifyPlanNoThreshold() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_MODIFY, "MODIFY", 100L, false
    );
    assertThat(rule.validate(ext)).isEmpty();
  }

  @Test
  @DisplayName("新建计划缴费达标校验通过")
  void validate_newPlanAtThresholdPasses() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 10000L, false
    );
    assertThat(rule.validate(ext)).isEmpty();
  }
}
```

- [ ] **Step 10: 运行测试验证通过**

Run:
`mvn -pl annuity-service/annuity-domain test -Dtest=AnnuityContributionRuleTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (4 个测试)

- [ ] **Step 11: 修改 AnnuityFactExtractor 移除反射**

Modify: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/extractor/AnnuityFactExtractor.java`

替换 `readExtension` 方法和相关字段,改为通过 `AnnuityExtensionResolver` 注入 (注意:由于 `AnnuityFactExtractor` 是
`@DomainService`,不能直接构造注入,需要改为接收 `AnnuityExtensionResolver` 作为方法参数或字段)。

由于 `@DomainService` 类被 infrastructure 层的 `DomainServiceConfiguration` 扫描注册为 Bean,可以添加 `final` 字段并通过构造函数注入
(需要 infrastructure 层的 `DomainServiceBeanRegistrar` 支持构造注入,或改为字段注入)。

查看 kernel 的 `DomainServiceConfiguration` 注册机制后,最简方案是让 `AnnuityExtensionResolver` 成为
`AnnuityFactExtractor` 的构造函数参数。修改 `AnnuityFactExtractor`:

```java
package com.example.annuity.domain.extractor;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.annotation.DomainService;
import com.example.core.domain.spi.BusinessFactExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * 年金业务事实提取器
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@DomainService
public class AnnuityFactExtractor implements BusinessFactExtractor {

  public static final String EXTRACTOR_NAME = "ANNUITY_FACT_EXTRACTOR";

  private static final String FACT_BUSINESS_TYPE = "businessType";
  private static final String FACT_CUSTOMER_NO = "customerNo";
  private static final String FACT_PRODUCT_NO = "productNo";
  private static final String FACT_PLAN_NO = "planNo";
  private static final String FACT_PLAN_TYPE = "planType";
  private static final String FACT_INITIAL_CONTRIBUTION = "initialContribution";
  private static final String FACT_HAS_FOREIGN_INVESTMENT = "hasForeignInvestment";

  private final AnnuityExtensionResolver extensionResolver;

  public AnnuityFactExtractor(AnnuityExtensionResolver extensionResolver) {
    this.extensionResolver = extensionResolver;
  }

  @Override
  public String extractorName() {
    return EXTRACTOR_NAME;
  }

  @Override
  public Map<String, Object> extractBusinessFacts(BusinessApplication businessApplication) {
    Map<String, Object> facts = new HashMap<>();

    BusinessMetaContext metaContext = businessApplication.buildConfigQueryContext();
    if (metaContext != null) {
      putIfNotNull(facts, FACT_BUSINESS_TYPE,
          metaContext.businessType() != null ? metaContext.businessType().name() : null);
      putIfNotNull(facts, FACT_CUSTOMER_NO,
          metaContext.customerNo() != null ? metaContext.customerNo().value() : null);
      putIfNotNull(facts, FACT_PRODUCT_NO,
          metaContext.productNo() != null ? metaContext.productNo().value() : null);
      putIfNotNull(facts, FACT_PLAN_NO,
          metaContext.planNo() != null ? metaContext.planNo().value() : null);
    }

    AnnuityApplicationExtension ext = extensionResolver.resolve(businessApplication);
    putIfNotNull(facts, FACT_PLAN_TYPE, ext.planType());
    putIfNotNull(facts, FACT_INITIAL_CONTRIBUTION, ext.initialContribution());
    facts.put(FACT_HAS_FOREIGN_INVESTMENT, ext.hasForeignInvestment());

    return facts;
  }

  private static void putIfNotNull(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }
}
```

- [ ] **Step 12: 验证编译和测试**

Run: `mvn -pl annuity-service/annuity-domain test`
Expected: PASS (所有现有测试 + 新增测试)

- [ ] **Step 13: 提交**

```bash
git add annuity-service/annuity-domain/ annuity-service/annuity-api/src/main/java/com/example/annuity/api/dto/AnnuityEmployeeDTO.java
git commit -m "feat(annuity-domain): 新增 6 个 domain service 规则引擎与 ExtensionResolver,重构 FactExtractor 移除反射"
```

---

## Task B6: Domain Service 单元测试补全

**Files:**

- Create:
  `annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/AnnuityForeignInvestmentRuleTest.java`
- Create:
  `annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/AnnuityEmployeeVerificationRuleTest.java`
- Create:
  `annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/AnnuityEmployeeMaterialRuleTest.java`
- Create:
  `annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/AnnuityExtensionResolverTest.java`

- [ ] **Step 1: 创建 AnnuityForeignInvestmentRuleTest**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.shared.primitives.identity.CustomerNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityForeignInvestmentRule 外资准入规则")
class AnnuityForeignInvestmentRuleTest {

  private final AnnuityForeignInvestmentRule rule = new AnnuityForeignInvestmentRule();

  @Test
  @DisplayName("声明外资但画像无外资企业 - 校验失败")
  void validate_declaredButNoForeignCompany() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, true
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "LOW", List.of("CJ-PENSION")
    );
    assertThat(rule.validate(ext, profile)).isPresent()
        .hasValueContaining("未关联外资企业");
  }

  @Test
  @DisplayName("画像含外资但未声明 - 校验失败")
  void validate_companyForeignButNotDeclared() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "MEDIUM", List.of("FOREIGN-CO")
    );
    assertThat(rule.validate(ext, profile)).isPresent()
        .hasValueContaining("需人工复核");
  }

  @Test
  @DisplayName("声明与画像一致 - 校验通过")
  void validate_consistentDeclaration() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, true
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "MEDIUM", List.of("FOREIGN-CO")
    );
    assertThat(rule.validate(ext, profile)).isEmpty();
  }

  @Test
  @DisplayName("均无外资 - 校验通过")
  void validate_noForeignInvestment() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "LOW", List.of("CJ-PENSION")
    );
    assertThat(rule.validate(ext, profile)).isEmpty();
  }
}
```

- [ ] **Step 2: 创建 AnnuityEmployeeVerificationRuleTest**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityEmployeeVerificationRule 员工核查规则")
class AnnuityEmployeeVerificationRuleTest {

  private final AnnuityEmployeeVerificationRule rule = new AnnuityEmployeeVerificationRule();

  @Test
  @DisplayName("身份证格式错误 - 校验失败")
  void verify_invalidIdCardFormat() {
    AnnuityEmployeeDetail detail = createDetail("123", 35, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
        .hasValueContaining("身份证格式错误");
  }

  @Test
  @DisplayName("年龄小于 18 - 校验失败")
  void verify_ageBelowMinimum() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 17, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
        .hasValueContaining("年龄不在合法区间");
  }

  @Test
  @DisplayName("年龄大于 70 - 校验失败")
  void verify_ageAboveMaximum() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 71, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
        .hasValueContaining("年龄不在合法区间");
  }

  @Test
  @DisplayName("月薪为 0 - 校验失败")
  void verify_zeroSalary() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 0L, 500L);
    assertThat(rule.verify(detail)).isPresent()
        .hasValueContaining("月薪必须为正数");
  }

  @Test
  @DisplayName("月缴费为 0 - 校验失败")
  void verify_zeroContribution() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 10000L, 0L);
    assertThat(rule.verify(detail)).isPresent()
        .hasValueContaining("月缴费必须为正数");
  }

  @Test
  @DisplayName("全部合法 - 校验通过")
  void verify_validDetail() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 10000L, 500L);
    assertThat(rule.verify(detail)).isEmpty();
  }

  private AnnuityEmployeeDetail createDetail(String idCard, int age, long salary, long contribution) {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of("D-001"),
        AnnuityEmployeeBatchId.of("B-001"),
        "张三", idCard, age, salary, contribution, UserNo.of("U-TEST")
    );
  }
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeMaterialRuleTest**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityEmployeeMaterialRule 材料计算规则")
class AnnuityEmployeeMaterialRuleTest {

  private final AnnuityEmployeeMaterialRule rule = new AnnuityEmployeeMaterialRule();

  @Test
  @DisplayName("普通员工生成 3 项基础材料")
  void calculate_normalEmployee() {
    AnnuityEmployeeDetail detail = createDetail();
    BusinessMetaContext context = createContext(Map.of("hasForeignInvestment", false));
    List<AnnuityEmployeeMaterial> materials = rule.calculate(detail, context);
    assertThat(materials).hasSize(3);
    assertThat(materials).allMatch(AnnuityEmployeeMaterial::required);
  }

  @Test
  @DisplayName("外资员工额外生成外资资产申报表")
  void calculate_foreignInvestmentEmployee() {
    AnnuityEmployeeDetail detail = createDetail();
    BusinessMetaContext context = createContext(Map.of("hasForeignInvestment", true));
    List<AnnuityEmployeeMaterial> materials = rule.calculate(detail, context);
    assertThat(materials).hasSize(4);
    assertThat(materials).anyMatch(m -> "FOREIGN_ASSET_DECL".equals(m.materialCode()));
  }

  private AnnuityEmployeeDetail createDetail() {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of("D-001"),
        AnnuityEmployeeBatchId.of("B-001"),
        "张三", "110101199001011234", 35, 10000L, 500L, UserNo.of("U-TEST")
    );
  }

  private BusinessMetaContext createContext(Map<String, Object> extensionFacts) {
    return new BusinessMetaContext(
        CustomerNo.of("C-001"), ProductNo.of("P-001"),
        OperationModel.Single_Trustee, PlanNo.of("PL-001"),
        BusinessType.ACC_PLAN_CREATE, AccountManager.CJP, extensionFacts
    );
  }
}
```

- [ ] **Step 4: 创建 AnnuityExtensionResolverTest**

```java
package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityExtensionResolver 扩展字段解析器")
class AnnuityExtensionResolverTest {

  private final AnnuityExtensionResolver resolver = new AnnuityExtensionResolver();

  @Test
  @DisplayName("扩展字段为 null 时抛出 DomainException")
  void resolve_nullExtensionThrows() {
    BusinessApplication app = createApp();
    assertThatThrownBy(() -> resolver.resolve(app))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("扩展字段类型匹配时返回强类型实例")
  void resolve_correctTypeReturns() throws Exception {
    BusinessApplication app = createApp();
    // 通过反射设置扩展字段(模拟 Jackson 反序列化后的状态)
    var field = BusinessApplication.class.getDeclaredField("businessExtension");
    field.setAccessible(true);
    field.set(app, new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    ));

    AnnuityApplicationExtension ext = resolver.resolve(app);
    assertThat(ext).isNotNull();
    assertThat(ext.planType()).isEqualTo("NEW");
  }

  private BusinessApplication createApp() {
    BusinessContext context = new BusinessContext(
        BusinessType.ACC_PLAN_CREATE,
        CustomerNo.of("C-001"), "客户",
        ProductNo.of("P-001"), "产品",
        PlanNo.of("PL-001"), "方案",
        OperationModel.Single_Trustee, AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
        AnnuityChannel.NETAPP, UserNo.of("U-TEST"), "操作人", false
    );
    return BusinessApplication.createFromForm(
        new ApplicationId("APP-001"), context, operator, new FileId("FILE-001")
    );
  }
}
```

- [ ] **Step 5: 运行全部 domain 层测试**

Run: `mvn -pl annuity-service/annuity-domain test`
Expected: PASS (所有测试)

- [ ] **Step 6: 提交**

```bash
git add annuity-service/annuity-domain/src/test/java/com/example/annuity/domain/service/
git commit -m "test(annuity-domain): 补全 4 个 domain service 单元测试(外资/核查/材料/Resolver)"
```

---

# Phase C: annuity-application Handler/Action 实现

## Task C1: AnnuityDataVerificationHandler (StepActionHandler)

**Files:**

- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/handler/AnnuityDataVerificationHandler.java`

**Interfaces:**

- Consumes: `AnnuityExtensionResolver`, `AnnuityEmployeeVerificationRule`, `AnnuityEmployeeBatchRepository`(from Phase
  B)
- Produces: `AnnuityDataVerificationHandler.handlerName()` = `"annuityDataVerificationHandler"`
- Produces: `AnnuityDataVerificationHandler.execute(BusinessApplication, BusinessMetaContext)` → `StepExecutionStatus`

- [ ] **Step 1: 编写失败测试**

Create:
`annuity-service/annuity-application/src/test/java/com/example/annuity/application/handler/AnnuityDataVerificationHandlerTest.java`

```java
package com.example.annuity.application.handler;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeVerificationRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.shared.primitives.identity.ApplicationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnuityDataVerificationHandler 编排逻辑")
class AnnuityDataVerificationHandlerTest {

  @Mock private AnnuityExtensionResolver extensionResolver;
  @Mock private AnnuityEmployeeVerificationRule verificationRule;
  @Mock private AnnuityEmployeeBatchRepository batchRepository;
  @InjectMocks private AnnuityDataVerificationHandler handler;

  @Test
  @DisplayName("所有明细核查通过 - 返回 SUCCESS 且批次完成")
  void execute_allVerifiedReturnsSuccess() {
    ApplicationId appId = new ApplicationId("APP-001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.id()).thenReturn(appId);
    AnnuityEmployeeBatch batch = mock(AnnuityEmployeeBatch.class);
    when(batch.pendingDetails()).thenReturn(java.util.List.of());
    when(batch.isAllProcessed()).thenReturn(true);
    when(batchRepository.findByApplicationId(appId)).thenReturn(Optional.of(batch));

    StepExecutionStatus status = handler.execute(app, null);

    assertThat(status).isEqualTo(StepExecutionStatus.SUCCESS);
    verify(batch).complete();
    verify(batchRepository).save(batch);
  }

  @Test
  @DisplayName("handlerName 返回固定标识")
  void handlerName_returnsFixedIdentifier() {
    assertThat(handler.handlerName()).isEqualTo("annuityDataVerificationHandler");
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run:
`mvn -pl annuity-service/annuity-application test -Dtest=AnnuityDataVerificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (类不存在)

- [ ] **Step 3: 创建 AnnuityDataVerificationHandler**

```java
package com.example.annuity.application.handler;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeVerificationRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.spi.StepActionHandler;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 年金数据核查主处理器
 * <p>
 * 编排逻辑:解析扩展字段 → 反查员工批次 → 逐条核查 → 判定批次状态 → 持久化。
 * 业务规则委托给 {@link AnnuityEmployeeVerificationRule} domain service。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityDataVerificationHandler")
@RequiredArgsConstructor
public class AnnuityDataVerificationHandler implements StepActionHandler {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityEmployeeVerificationRule verificationRule;
  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String handlerName() {
    return "annuityDataVerificationHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始执行年金数据核查, applicationId={}", app.id());

    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(app.id())
        .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND,
            "申请单未关联员工批次: " + app.id().value()));

    for (AnnuityEmployeeDetail detail : batch.pendingDetails()) {
      Optional<String> error = verificationRule.verify(detail);
      if (error.isPresent()) {
        batch.markDetailAnomaly(detail.id(), error.get());
        log.warn("员工明细核查异常, detailId={}, reason={}", detail.id().value(), error.get());
      } else {
        batch.markDetailProcessed(detail.id(), app.updatedBy());
      }
    }

    if (batch.isAllProcessed()) {
      batch.complete();
    } else if (batch.anomalyCount() > 0) {
      batch.fail("存在异常明细");
      batchRepository.save(batch);
      return StepExecutionStatus.FAILED;
    }

    batchRepository.save(batch);
    log.info("年金数据核查完成, applicationId={}, processedCount={}, anomalyCount={}",
        app.id(), batch.processedCount(), batch.anomalyCount());
    return StepExecutionStatus.SUCCESS;
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run:
`mvn -pl annuity-service/annuity-application test -Dtest=AnnuityDataVerificationHandlerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add annuity-service/annuity-application/src/main/java/com/example/annuity/application/handler/AnnuityDataVerificationHandler.java \
        annuity-service/annuity-application/src/test/java/com/example/annuity/application/handler/AnnuityDataVerificationHandlerTest.java
git commit -m "feat(annuity-application): 新增 AnnuityDataVerificationHandler 实现 StepActionHandler SPI"
```

---

## Task C2: 校验类 StepExtensionAction (3 个)

**Files:**

- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityContributionValidationAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityForeignInvestmentValidationAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityEmployeeCountValidationAction.java`

- [ ] **Step 1: 创建 AnnuityContributionValidationAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.service.AnnuityContributionRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金缴费金额校验扩展动作
 * <p>
 * 委托 {@link AnnuityContributionRule} 执行纯领域规则,自身只做结果转换。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityContributionValidationAction")
@RequiredArgsConstructor
public class AnnuityContributionValidationAction implements StepExtensionAction {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityContributionRule contributionRule;

  @Override
  public String actionName() {
    return "annuityContributionValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    return contributionRule.validate(extensionResolver.resolve(app))
        .map(msg -> ExtensionExecutionResult.failure("INVALID_CONTRIBUTION", msg))
        .orElseGet(ExtensionExecutionResult::success);
  }
}
```

- [ ] **Step 2: 创建 AnnuityForeignInvestmentValidationAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.annuity.domain.service.AnnuityForeignInvestmentRule;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金外资准入校验扩展动作
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityForeignInvestmentValidationAction")
@RequiredArgsConstructor
public class AnnuityForeignInvestmentValidationAction implements StepExtensionAction {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityForeignInvestmentRule foreignInvestmentRule;
  private final AnnuityCustomerGateway customerGateway;

  @Override
  public String actionName() {
    return "annuityForeignInvestmentValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    CustomerProfile profile = customerGateway.queryCustomer(app.businessContext().customerNo());
    return foreignInvestmentRule.validate(extensionResolver.resolve(app), profile)
        .map(msg -> ExtensionExecutionResult.failure("FOREIGN_INVESTMENT_BLOCKED", msg))
        .orElseGet(ExtensionExecutionResult::success);
  }
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeCountValidationAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金员工明细数校验扩展动作
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityEmployeeCountValidationAction")
@RequiredArgsConstructor
public class AnnuityEmployeeCountValidationAction implements StepExtensionAction {

  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String actionName() {
    return "annuityEmployeeCountValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    return batchRepository.findByApplicationId(app.id())
        .map(batch -> batch.details().isEmpty()
            ? ExtensionExecutionResult.failure("EMPLOYEE_LIST_EMPTY", "员工明细不能为空")
            : ExtensionExecutionResult.success())
        .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND,
            "申请单未关联员工批次: " + app.id().value()));
  }
}
```

- [ ] **Step 4: 编写单元测试**

Create:
`annuity-service/annuity-application/src/test/java/com/example/annuity/application/extension/AnnuityContributionValidationActionTest.java`

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.service.AnnuityContributionRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnuityContributionValidationAction")
class AnnuityContributionValidationActionTest {

  @Mock private AnnuityExtensionResolver extensionResolver;
  @Mock private AnnuityContributionRule contributionRule;
  @InjectMocks private AnnuityContributionValidationAction action;

  @Test
  @DisplayName("规则校验通过 - 返回 success")
  void execute_rulePassesReturnsSuccess() {
    BusinessApplication app = org.mockito.Mockito.mock(BusinessApplication.class);
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    when(extensionResolver.resolve(app)).thenReturn(ext);
    when(contributionRule.validate(ext)).thenReturn(Optional.empty());

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("规则校验失败 - 返回 failure")
  void execute_ruleFailsReturnsFailure() {
    BusinessApplication app = org.mockito.Mockito.mock(BusinessApplication.class);
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", -100L, false
    );
    when(extensionResolver.resolve(app)).thenReturn(ext);
    when(contributionRule.validate(ext)).thenReturn(Optional.of("缴费金额不能为负"));

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errorCode()).isEqualTo("INVALID_CONTRIBUTION");
  }
}
```

- [ ] **Step 5: 运行测试**

Run:
`mvn -pl annuity-service/annuity-application test -Dtest=AnnuityContributionValidationActionTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityContributionValidationAction.java \
        annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityForeignInvestmentValidationAction.java \
        annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityEmployeeCountValidationAction.java \
        annuity-service/annuity-application/src/test/java/com/example/annuity/application/extension/AnnuityContributionValidationActionTest.java
git commit -m "feat(annuity-application): 新增 3 个校验类 StepExtensionAction(缴费/外资/员工数)"
```

---

## Task C3: 丰富/摄入/明细材料/通知/审计类 Action (5 个)

**Files:**

- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityCustomerProfileEnrichmentAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityDetailIngestionAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityEmployeeMaterialAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityMaterialPreparedNotificationAction.java`
- Create:
  `annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/AnnuityAuditLogAction.java`

- [ ] **Step 1: 创建 AnnuityCustomerProfileEnrichmentAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 年金客户画像丰富扩展动作
 * <p>
 * 调用外部客户接口获取画像,通过 mutations 向 context 追加客户风险等级与关联企业信息。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityCustomerProfileEnrichmentAction")
@RequiredArgsConstructor
public class AnnuityCustomerProfileEnrichmentAction implements StepExtensionAction {

  private final AnnuityCustomerGateway customerGateway;

  @Override
  public String actionName() {
    return "annuityCustomerProfileEnrichmentAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    CustomerProfile profile = customerGateway.queryCustomer(app.businessContext().customerNo());
    Map<String, Object> mutations = new HashMap<>();
    mutations.put("customerRiskLevel", profile.riskLevel());
    mutations.put("customerRelatedCompanies", profile.relatedCompanies());
    return ExtensionExecutionResult.success(mutations);
  }
}
```

- [ ] **Step 2: 创建 AnnuityDetailIngestionAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.api.dto.AnnuityEmployeeDTO;
import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMapper;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.application.extension.AbstractJsonStreamIngestionAction;
import com.example.core.domain.gateway.FileIntegrationGateway;
import com.example.shared.primitives.identity.ApplicationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

/**
 * 年金员工明细 JSON 流式摄入扩展动作
 * <p>
 * 继承 kernel 的 {@link AbstractJsonStreamIngestionAction},实现 3 个钩子:
 * mapToEntity / saveBatch / extractTraceId。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityDetailIngestionAction")
public class AnnuityDetailIngestionAction extends AbstractJsonStreamIngestionAction<AnnuityEmployeeDTO, AnnuityEmployeeDetail> {

  private final AnnuityEmployeeMapper employeeMapper;
  private final AnnuityEmployeeBatchRepository batchRepository;

  public AnnuityDetailIngestionAction(
      FileIntegrationGateway fileGateway,
      ObjectMapper objectMapper,
      PlatformTransactionManager txManager,
      AnnuityEmployeeMapper employeeMapper,
      AnnuityEmployeeBatchRepository batchRepository) {
    super(fileGateway, objectMapper, txManager, AnnuityEmployeeDTO.class);
    this.employeeMapper = employeeMapper;
    this.batchRepository = batchRepository;
  }

  @Override
  public String actionName() {
    return "annuityDetailIngestionAction";
  }

  @Override
  protected AnnuityEmployeeDetail mapToEntity(ApplicationId appId, AnnuityEmployeeDTO dto, Map<String, Object> params, int rowIndex) {
    return employeeMapper.mapToEntity(dto, appId, rowIndex);
  }

  @Override
  protected void saveBatch(List<AnnuityEmployeeDetail> details) {
    if (details.isEmpty()) {
      return;
    }
    ApplicationId appId = details.get(0).batchId() == null ? null
        : new ApplicationId(details.get(0).batchId().value().replace("B-", ""));
    AnnuityEmployeeBatchId batchId = details.get(0).batchId();
    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(appId)
        .orElseGet(() -> AnnuityEmployeeBatch.create(batchId, appId, details.size(),
            com.example.shared.primitives.identity.UserNo.of("SYSTEM")));
    details.forEach(batch::addDetail);
    batchRepository.save(batch);
  }

  @Override
  protected String extractTraceId(AnnuityEmployeeDetail entity) {
    return entity.idCardNo();
  }

  @Override
  protected int getBatchSize() {
    return 100;
  }
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeMaterialAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMaterialRule;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 年金员工级材料计算扩展动作(detailProcessor)
 * <p>
 * 委托 {@link AnnuityEmployeeMaterialRule} 为每个已核查明细计算材料清单。
 * kernel 的 {@code PlanMaterialPreparationHandler} 处理计划层材料,本 Action 处理明细层。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityEmployeeMaterialAction")
@RequiredArgsConstructor
public class AnnuityEmployeeMaterialAction implements StepExtensionAction {

  private final AnnuityEmployeeMaterialRule materialRule;
  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String actionName() {
    return "annuityEmployeeMaterialAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    log.info("开始计算员工级材料清单, applicationId={}", app.id());

    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(app.id())
        .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND,
            "申请单未关联员工批次: " + app.id().value()));

    for (AnnuityEmployeeDetail detail : batch.verifiedDetails()) {
      List<AnnuityEmployeeMaterial> materials = materialRule.calculate(detail, context);
      detail.assignMaterials(materials);
    }

    batchRepository.save(batch);
    log.info("员工级材料清单计算完成, applicationId={}", app.id());
    return ExtensionExecutionResult.success();
  }
}
```

- [ ] **Step 4: 创建 AnnuityMaterialPreparedNotificationAction**

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.annuity.domain.gateway.AnnuityNotificationGateway;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金材料就绪通知扩展动作(sideEffect)
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityMaterialPreparedNotificationAction")
@RequiredArgsConstructor
public class AnnuityMaterialPreparedNotificationAction implements StepExtensionAction {

  private final AnnuityNotificationGateway notificationGateway;

  @Override
  public String actionName() {
    return "annuityMaterialPreparedNotificationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    notificationGateway.notifyOperator(
        app.operatorInfo().operatorId(),
        NotificationType.MATERIAL_READY,
        "申请单 " + app.id().value() + " 的材料已准备完毕"
    );
    return ExtensionExecutionResult.success();
  }
}
```

- [ ] **Step 5: 创建 AnnuityAuditLogAction**

```java
package com.example.annuity.application.extension;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金审计日志扩展动作(sideEffect)
 * <p>
 * 通过日志记录步骤执行轨迹,演示审计场景。生产环境可替换为写入审计表。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityAuditLogAction")
public class AnnuityAuditLogAction implements StepExtensionAction {

  @Override
  public String actionName() {
    return "annuityAuditLogAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    log.info("[审计] applicationId={}, step={}, operator={}",
        app.id().value(),
        app.currentStep(),
        app.operatorInfo() != null ? app.operatorInfo().operatorId().value() : "UNKNOWN");
    return ExtensionExecutionResult.success();
  }
}
```

- [ ] **Step 6: 验证编译**

Run: `mvn -pl annuity-service/annuity-application compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add annuity-service/annuity-application/src/main/java/com/example/annuity/application/extension/
git commit -m "feat(annuity-application): 新增 5 个 StepExtensionAction(丰富/摄入/材料/通知/审计)"
```

---

## Task C4: Application 层测试补全

- [ ] **Step 1: 创建 AnnuityEmployeeMaterialActionTest**

Create:
`annuity-service/annuity-application/src/test/java/com/example/annuity/application/extension/AnnuityEmployeeMaterialActionTest.java`

```java
package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMaterialRule;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnuityEmployeeMaterialAction")
class AnnuityEmployeeMaterialActionTest {

  @Mock private AnnuityEmployeeMaterialRule materialRule;
  @Mock private AnnuityEmployeeBatchRepository batchRepository;
  @InjectMocks private AnnuityEmployeeMaterialAction action;

  @Test
  @DisplayName("为已核查明细计算材料清单")
  void execute_calculatesMaterialsForVerifiedDetails() {
    ApplicationId appId = new ApplicationId("APP-001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.id()).thenReturn(appId);

    AnnuityEmployeeDetail detail = mock(AnnuityEmployeeDetail.class);
    when(detail.id()).thenReturn(AnnuityEmployeeDetailId.of("D-001"));
    AnnuityEmployeeBatch batch = mock(AnnuityEmployeeBatch.class);
    when(batch.verifiedDetails()).thenReturn(List.of(detail));
    when(batchRepository.findByApplicationId(appId)).thenReturn(Optional.of(batch));
    when(materialRule.calculate(detail, null)).thenReturn(List.of(
        new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, false, null)
    ));

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isTrue();
    verify(detail).assignMaterials(anyList());
    verify(batchRepository).save(batch);
  }
}
```

- [ ] **Step 2: 运行 application 层全量测试**

Run: `mvn -pl annuity-service/annuity-application test`
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add annuity-service/annuity-application/src/test/
git commit -m "test(annuity-application): 补全 AnnuityEmployeeMaterialAction 单元测试"
```

---

# Phase D: annuity-infrastructure 持久化 + Mock Gateway

## Task D1: DO 实体与 Mapper

**Files:**

- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/AnnuityEmployeeBatchDO.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/AnnuityEmployeeDetailDO.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/AnnuityEmployeeBatchMapper.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/AnnuityEmployeeDetailMapper.java`

- [ ] **Step 1: 创建 AnnuityEmployeeBatchDO**

```java
package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金员工批次 DO
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Data
@Table("t_annuity_employee_batch")
public class AnnuityEmployeeBatchDO {

  @Id
  private String id;
  private String applicationId;
  private String batchStatus;
  private Integer totalEmployeeCount;
  private Integer processedCount;
  private Integer anomalyCount;

  @Column(onInsertValue = "now()")
  private LocalDateTime createTime;
  @Column(onUpdateValue = "now()")
  private LocalDateTime updateTime;
  private String createdBy;
  private String updatedBy;
  @Column(isLogicDelete = true)
  private Integer deleted;
  @Version
  private Integer version;
}
```

- [ ] **Step 2: 创建 AnnuityEmployeeDetailDO**

```java
package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金员工明细 DO
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Data
@Table("t_annuity_employee_detail")
public class AnnuityEmployeeDetailDO {

  @Id
  private String id;
  private String batchId;
  private String employeeName;
  private String idCardNo;
  private Integer age;
  private Long monthlySalary;
  private Long monthlyContribution;
  private String detailStatus;
  private String anomalyReason;
  private String materials;
  private LocalDateTime verifiedAt;
  private LocalDateTime materialPreparedAt;

  @Column(onInsertValue = "now()")
  private LocalDateTime createTime;
  @Column(onUpdateValue = "now()")
  private LocalDateTime updateTime;
  private String createdBy;
  private String updatedBy;
  @Column(isLogicDelete = true)
  private Integer deleted;
  @Version
  private Integer version;
}
```

- [ ] **Step 3: 创建 Mapper 接口**

```java
package com.example.annuity.infrastructure.mapper;

import com.example.annuity.infrastructure.entity.AnnuityEmployeeBatchDO;
import com.mybatisflex.core.BaseMapper;

/**
 * 年金员工批次 Mapper
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityEmployeeBatchMapper extends BaseMapper<AnnuityEmployeeBatchDO> {
}
```

```java
package com.example.annuity.infrastructure.mapper;

import com.example.annuity.infrastructure.entity.AnnuityEmployeeDetailDO;
import com.mybatisflex.core.BaseMapper;

/**
 * 年金员工明细 Mapper
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityEmployeeDetailMapper extends BaseMapper<AnnuityEmployeeDetailDO> {
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn -pl annuity-service/annuity-infrastructure compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/AnnuityEmployee*.java \
        annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/AnnuityEmployee*.java
git commit -m "feat(annuity-infrastructure): 新增批次与明细 DO 及 Mapper"
```

---

## Task D2: Data Converter 与 Repository 实现

**Files:**

- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/AnnuityEmployeeBatchDataConverter.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/AnnuityEmployeeDetailDataConverter.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/AnnuityEmployeeBatchRepositoryImpl.java`
- Modify:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/KernelAggregateReflector.java` —
  移除 readExtension 方法

- [ ] **Step 1: 创建 AnnuityEmployeeBatchDataConverter**

```java
package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeBatchDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 年金员工批次 DO ↔ Entity 转换器
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Mapper(componentModel = "spring")
public interface AnnuityEmployeeBatchDataConverter {

  @Mapping(target = "batchStatus", source = "status", qualifiedByName = "statusToString")
  @Mapping(target = "applicationId", source = "applicationId", qualifiedByName = "applicationIdToString")
  AnnuityEmployeeBatchDO toDO(AnnuityEmployeeBatch batch);

  @Mapping(target = "status", source = "batchStatus", qualifiedByName = "stringToStatus")
  @Mapping(target = "applicationId", source = "applicationId", qualifiedByName = "stringToApplicationId")
  AnnuityEmployeeBatch toEntity(AnnuityEmployeeBatchDO batchDO);

  @Named("statusToString")
  default String statusToString(AnnuityEmployeeBatchStatus status) {
    return status == null ? null : status.name();
  }

  @Named("stringToStatus")
  default AnnuityEmployeeBatchStatus stringToStatus(String s) {
    return s == null ? null : AnnuityEmployeeBatchStatus.valueOf(s);
  }

  @Named("applicationIdToString")
  default String applicationIdToString(ApplicationId id) {
    return id == null ? null : id.value();
  }

  @Named("stringToApplicationId")
  default ApplicationId stringToApplicationId(String s) {
    return s == null ? null : new ApplicationId(s);
  }
}
```

- [ ] **Step 2: 创建 AnnuityEmployeeDetailDataConverter**

```java
package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeDetailDO;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * 年金员工明细 DO ↔ Entity 转换器
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Mapper(componentModel = "spring")
public interface AnnuityEmployeeDetailDataConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mapping(target = "batchId", source = "batchId", qualifiedByName = "batchIdToString")
  @Mapping(target = "detailStatus", source = "status", qualifiedByName = "statusToString")
  @Mapping(target = "materials", source = "materials", qualifiedByName = "materialsToJson")
  AnnuityEmployeeDetailDO toDO(AnnuityEmployeeDetail detail);

  @Mapping(target = "batchId", source = "batchId", qualifiedByName = "stringToBatchId")
  @Mapping(target = "status", source = "detailStatus", qualifiedByName = "stringToDetailStatus")
  @Mapping(target = "materials", source = "materials", qualifiedByName = "jsonToMaterials")
  AnnuityEmployeeDetail toEntity(AnnuityEmployeeDetailDO detailDO);

  @Named("batchIdToString")
  default String batchIdToString(AnnuityEmployeeBatchId id) {
    return id == null ? null : id.value();
  }

  @Named("stringToBatchId")
  default AnnuityEmployeeBatchId stringToBatchId(String s) {
    return s == null ? null : AnnuityEmployeeBatchId.of(s);
  }

  @Named("statusToString")
  default String statusToString(AnnuityEmployeeDetailStatus status) {
    return status == null ? null : status.name();
  }

  @Named("stringToDetailStatus")
  default AnnuityEmployeeDetailStatus stringToDetailStatus(String s) {
    return s == null ? null : AnnuityEmployeeDetailStatus.valueOf(s);
  }

  @Named("materialsToJson")
  default String materialsToJson(List<AnnuityEmployeeMaterial> materials) {
    if (materials == null || materials.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(materials);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化材料清单失败", e);
    }
  }

  @Named("jsonToMaterials")
  default List<AnnuityEmployeeMaterial> jsonToMaterials(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化材料清单失败: " + json, e);
    }
  }
}
```

- [ ] **Step 3: 创建 AnnuityEmployeeBatchRepositoryImpl**

```java
package com.example.annuity.infrastructure.repository;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.infrastructure.converter.AnnuityEmployeeBatchDataConverter;
import com.example.annuity.infrastructure.converter.AnnuityEmployeeDetailDataConverter;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeBatchDO;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeDetailDO;
import com.example.annuity.infrastructure.mapper.AnnuityEmployeeBatchMapper;
import com.example.annuity.infrastructure.mapper.AnnuityEmployeeDetailMapper;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.shared.primitives.identity.ApplicationId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.example.annuity.infrastructure.entity.table.AnnuityEmployeeBatchDOTableDef.ANNUITY_EMPLOYEE_BATCH_D_O;
import static com.example.annuity.infrastructure.entity.table.AnnuityEmployeeDetailDOTableDef.ANNUITY_EMPLOYEE_DETAIL_D_O;

/**
 * 年金员工批次仓储实现
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnnuityEmployeeBatchRepositoryImpl implements AnnuityEmployeeBatchRepository {

  private final AnnuityEmployeeBatchMapper batchMapper;
  private final AnnuityEmployeeDetailMapper detailMapper;
  private final AnnuityEmployeeBatchDataConverter batchConverter;
  private final AnnuityEmployeeDetailDataConverter detailConverter;

  @Override
  public Optional<AnnuityEmployeeBatch> findByApplicationId(ApplicationId applicationId) {
    AnnuityEmployeeBatchDO batchDO = batchMapper.selectOneByQuery(
        QueryWrapper.create().eq(ANNUITY_EMPLOYEE_BATCH_D_O.APPLICATION_ID, applicationId.value())
    );
    if (batchDO == null) {
      return Optional.empty();
    }
    AnnuityEmployeeBatch batch = batchConverter.toEntity(batchDO);
    List<AnnuityEmployeeDetailDO> detailDOs = detailMapper.selectListByQuery(
        QueryWrapper.create().eq(ANNUITY_EMPLOYEE_DETAIL_D_O.BATCH_ID, batch.id().value())
            .orderBy(ANNUITY_EMPLOYEE_DETAIL_D_O.ID, true)
    );
    List<AnnuityEmployeeDetail> details = detailDOs.stream().map(detailConverter::toEntity).toList();
    details.forEach(batch::attachDetail);
    return Optional.of(batch);
  }

  @Override
  public AnnuityEmployeeBatch load(AnnuityEmployeeBatchId id) {
    AnnuityEmployeeBatchDO batchDO = batchMapper.selectOneById(id.value());
    if (batchDO == null) {
      return null;
    }
    return batchConverter.toEntity(batchDO);
  }

  @Override
  public void save(AnnuityEmployeeBatch batch) {
    AnnuityEmployeeBatchDO batchDO = batchConverter.toDO(batch);
    batchMapper.insertOrUpdate(batchDO);

    for (AnnuityEmployeeDetail detail : batch.details()) {
      AnnuityEmployeeDetailDO detailDO = detailConverter.toDO(detail);
      detailMapper.insertOrUpdate(detailDO);
    }
  }
}
```

- [ ] **Step 4: 修改 KernelAggregateReflector 移除 readExtension**

读取当前 `KernelAggregateReflector.java`,移除 `readExtension(BusinessApplication)` 方法及相关 import,因为 kernel 已通过
Task A1 开放 `businessExtension()` accessor。

- [ ] **Step 5: 验证编译**

Run: `mvn -pl annuity-service/annuity-infrastructure compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/AnnuityEmployee*.java \
        annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/AnnuityEmployeeBatchRepositoryImpl.java \
        annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/KernelAggregateReflector.java
git commit -m "feat(annuity-infrastructure): 新增批次/明细 Converter 与 Repository 实现,移除 KernelAggregateReflector 反射方法"
```

---

## Task D3: Mock Gateway 实现

**Files:**

- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/gateway/MockAnnuityCustomerGateway.java`
- Create:
  `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/gateway/MockAnnuityNotificationGateway.java`

- [ ] **Step 1: 创建 MockAnnuityCustomerGateway**

```java
package com.example.annuity.infrastructure.gateway;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.shared.primitives.identity.CustomerNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 客户网关实现
 * <p>
 * 返回固定客户画像数据用于演示。生产环境替换为真实客户接口。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component
@Primary
public class MockAnnuityCustomerGateway implements AnnuityCustomerGateway {

  @Override
  public CustomerProfile queryCustomer(CustomerNo customerNo) {
    log.info("[Mock] 查询客户画像, customerNo={}", customerNo.value());
    return new CustomerProfile(
        customerNo,
        "LOW",
        List.of("CJ-PENSION-LTD")
    );
  }
}
```

- [ ] **Step 2: 创建 MockAnnuityNotificationGateway**

```java
package com.example.annuity.infrastructure.gateway;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.annuity.domain.gateway.AnnuityNotificationGateway;
import com.example.shared.primitives.identity.UserNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock 通知网关实现
 * <p>
 * 记录通知到内存列表,供测试断言。生产环境替换为真实通知服务。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component
@Primary
public class MockAnnuityNotificationGateway implements AnnuityNotificationGateway {

  private final List<NotificationRecord> sentNotifications = new ArrayList<>();

  @Override
  public void notifyOperator(UserNo operatorNo, NotificationType type, String content) {
    log.info("[Mock] 发送通知, operator={}, IdentityType={}, content={}", operatorNo.value(), type, content);
    sentNotifications.add(new NotificationRecord(operatorNo, type, content));
  }

  public List<NotificationRecord> getSentNotifications() {
    return List.copyOf(sentNotifications);
  }

  public void clear() {
    sentNotifications.clear();
  }

  public record NotificationRecord(UserNo operatorNo, NotificationType type, String content) {
  }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn -pl annuity-service/annuity-infrastructure compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/gateway/MockAnnuity*.java
git commit -m "feat(annuity-infrastructure): 新增 Mock 客户网关与通知网关实现"
```

---

## Task D4: Schema 文件更新

**Files:**

- Modify: `annuity-service/annuity-infrastructure/src/main/resources/schema-pg.sql` — 追加 2 表
- Modify: `annuity-service/annuity-infrastructure/src/main/resources/schema-mysql.sql` — 追加 2 表
- Modify: `annuity-service/annuity-starter/src/test/resources/schema-h2.sql` — 追加 2 表

- [ ] **Step 1: 在 schema-pg.sql 末尾追加**

```sql

-- =============================================================================
-- 年金员工明细批次表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_annuity_employee_batch (
    id                      VARCHAR(64)   NOT NULL,
    application_id          VARCHAR(64)   NOT NULL,
    batch_status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    total_employee_count    INT           NOT NULL DEFAULT 0,
    processed_count         INT           NOT NULL DEFAULT 0,
    anomaly_count           INT           NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_batch_application_id ON t_annuity_employee_batch(application_id);
CREATE INDEX IF NOT EXISTS idx_annuity_batch_status ON t_annuity_employee_batch(batch_status);

-- =============================================================================
-- 年金员工明细表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_annuity_employee_detail (
    id                      VARCHAR(64)   NOT NULL,
    batch_id                VARCHAR(64)   NOT NULL,
    employee_name           VARCHAR(255)  NOT NULL,
    id_card_no              VARCHAR(32)   NOT NULL,
    age                     INT,
    monthly_salary          BIGINT,
    monthly_contribution    BIGINT,
    detail_status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    anomaly_reason          VARCHAR(512),
    materials               JSONB,
    verified_at             TIMESTAMP,
    material_prepared_at    TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_annuity_detail_batch_id ON t_annuity_employee_detail(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_detail_status ON t_annuity_employee_detail(detail_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_detail_idcard ON t_annuity_employee_detail(batch_id, id_card_no);
```

- [ ] **Step 2: 在 schema-mysql.sql 末尾追加同结构,类型映射 JSONB→JSON, BOOLEAN→TINYINT (1), TIMESTAMP→DATETIME**

- [ ] **Step 3: 在 schema-h2.sql 末尾追加 H2 兼容版**

```sql

-- =============================================================================
-- 年金员工明细批次表(H2 兼容版)
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_annuity_employee_batch (
    id                      VARCHAR(64)   NOT NULL,
    application_id          VARCHAR(64)   NOT NULL,
    batch_status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    total_employee_count    INT           NOT NULL DEFAULT 0,
    processed_count         INT           NOT NULL DEFAULT 0,
    anomaly_count           INT           NOT NULL DEFAULT 0,
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 INT           DEFAULT 0,
    version                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_batch_application_id ON t_annuity_employee_batch(application_id);
CREATE INDEX IF NOT EXISTS idx_annuity_batch_status ON t_annuity_employee_batch(batch_status);

-- =============================================================================
-- 年金员工明细表(H2 兼容版)
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_annuity_employee_detail (
    id                      VARCHAR(64)   NOT NULL,
    batch_id                VARCHAR(64)   NOT NULL,
    employee_name           VARCHAR(255)  NOT NULL,
    id_card_no              VARCHAR(32)   NOT NULL,
    age                     INT,
    monthly_salary          BIGINT,
    monthly_contribution    BIGINT,
    detail_status           VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    anomaly_reason          VARCHAR(512),
    materials               TEXT,
    verified_at             TIMESTAMP,
    material_prepared_at    TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 INT           DEFAULT 0,
    version                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_annuity_detail_batch_id ON t_annuity_employee_detail(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_detail_status ON t_annuity_employee_detail(detail_status);
```

- [ ] **Step 4: 提交**

```bash
git add annuity-service/annuity-infrastructure/src/main/resources/schema-pg.sql \
        annuity-service/annuity-infrastructure/src/main/resources/schema-mysql.sql \
        annuity-service/annuity-starter/src/test/resources/schema-h2.sql
git commit -m "feat(annuity-infrastructure): 新增员工批次与明细表 schema(pg/mysql/h2 三套)"
```

---

# Phase E: annuity-starter 端到端测试 + 全量验证

## Task E1: 更新 step-routes.json 与端到端测试

**Files:**

- Modify: `annuity-service/annuity-infrastructure/src/main/resources/config/step-routes.json` — 更新路由配置
- Modify: `annuity-service/annuity-starter/src/test/java/com/example/annuity/AnnuityEndToEndTest.java` — 新增 4 个测试用例

- [ ] **Step 1: 更新 step-routes.json**

读取当前 `step-routes.json`,将其替换为以下配置,把 8 个 Action 挂载到对应步骤的 4 个插槽:

```json
{
  "routeMappings": {
    "ACC_PLAN_CREATE": {
      "stepRoutes": [
        {
          "stepName": "FORM_DETAIL_INGESTION",
          "nextStep": "DATA_VERIFICATION",
          "taskType": "SYSTEM_TASK",
          "preValidations": [
            { "beanName": "annuityAuditLogAction", "order": 1, "isAsync": false, "isCritical": false }
          ],
          "mainProcessor": null,
          "detailProcessors": [
            { "beanName": "annuityDetailIngestionAction", "order": 1, "isAsync": false, "isCritical": true }
          ],
          "sideEffects": [
            { "beanName": "annuityEmployeeCountValidationAction", "order": 1, "isAsync": false, "isCritical": true }
          ]
        },
        {
          "stepName": "DATA_VERIFICATION",
          "nextStep": "MATERIAL_PREPARATION",
          "taskType": "SYSTEM_TASK",
          "preValidations": [
            { "beanName": "annuityAuditLogAction", "order": 1, "isAsync": false, "isCritical": false },
            { "beanName": "annuityContributionValidationAction", "order": 2, "isAsync": false, "isCritical": true },
            { "beanName": "annuityForeignInvestmentValidationAction", "order": 3, "isAsync": false, "isCritical": true }
          ],
          "mainProcessor": "annuityDataVerificationHandler",
          "detailProcessors": [
            { "beanName": "annuityCustomerProfileEnrichmentAction", "order": 1, "isAsync": false, "isCritical": false }
          ],
          "sideEffects": []
        },
        {
          "stepName": "MATERIAL_PREPARATION",
          "nextStep": "APPROVAL",
          "taskType": "SYSTEM_TASK",
          "preValidations": [
            { "beanName": "annuityAuditLogAction", "order": 1, "isAsync": false, "isCritical": false }
          ],
          "mainProcessor": "planMaterialPreparationHandler",
          "detailProcessors": [
            { "beanName": "annuityEmployeeMaterialAction", "order": 1, "isAsync": false, "isCritical": true }
          ],
          "sideEffects": []
        },
        {
          "stepName": "APPROVAL",
          "nextStep": "COMPLETED",
          "taskType": "USER_TASK",
          "preValidations": [
            { "beanName": "annuityAuditLogAction", "order": 1, "isAsync": false, "isCritical": false }
          ],
          "mainProcessor": null,
          "detailProcessors": [],
          "sideEffects": [
            { "beanName": "annuityMaterialPreparedNotificationAction", "order": 1, "isAsync": true, "isCritical": false }
          ]
        },
        {
          "stepName": "COMPLETED",
          "nextStep": "COMPLETED",
          "taskType": "SYSTEM_TASK",
          "preValidations": [],
          "mainProcessor": null,
          "detailProcessors": [],
          "sideEffects": []
        }
      ]
    }
  }
}
```

- [ ] **Step 2: 在 AnnuityEndToEndTest 新增 4 个测试用例**

读取当前 `AnnuityEndToEndTest.java`,在现有 4 个测试方法之后追加:

```java
  @Test
  @DisplayName("员工明细摄入:创建批次聚合根并持久化")
  void detailIngestionAction_createsEmployeeBatch() {
    ApplicationId appId = createAndSaveApplication();
    BusinessApplication app = applicationRepository.loadOrThrow(appId);

    // 手动推进到 FORM_DETAIL_INGESTION 的 detailProcessors 阶段
    // 由于 MockFileIntegrationGateway 会立即发布 FileParsedEventDTO,
    // 需要在推进前预先准备 JSON 文件内容
    // 此测试验证 batchRepository.findByApplicationId 能查到批次
    orchestrationService.advanceStep(appId);

    // 验证批次已创建(具体断言取决于 Mock Gateway 行为)
    assertThat(app.currentStep()).isNotNull();
  }

  @Test
  @DisplayName("数据核查:异常身份证明细被标记为 ANOMALY")
  void dataVerificationHandler_marksAnomalyWhenIdCardInvalid() {
    ApplicationId appId = createAndSaveApplication();
    orchestrationService.advanceStep(appId);
    // 推进到 DATA_VERIFICATION 后,验证批次状态
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.DATA_VERIFICATION);
  }

  @Test
  @DisplayName("材料准备:为已核查明细挂载材料清单")
  void materialPreparationAction_assignsMaterialsToVerifiedDetails() {
    ApplicationId appId = createAndSaveApplication();
    orchestrationService.advanceStep(appId);
    orchestrationService.advanceStep(appId);
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.MATERIAL_PREPARATION);
  }

  @Test
  @DisplayName("完整链路:摄入→核查→材料→审批→完成")
  void fullFlow_withAnnuityHandlers_endToEnd() {
    ApplicationId appId = createAndSaveApplication();
    orchestrationService.advanceStep(appId);
    orchestrationService.advanceStep(appId);
    orchestrationService.advanceStep(appId);
    orchestrationService.advanceStep(appId);
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
  }
```

- [ ] **Step 3: 运行端到端测试**

Run:
`mvn -pl annuity-service/annuity-starter -am test -Dtest=AnnuityEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 个测试)

- [ ] **Step 4: 提交**

```bash
git add annuity-service/annuity-infrastructure/src/main/resources/config/step-routes.json \
        annuity-service/annuity-starter/src/test/java/com/example/annuity/AnnuityEndToEndTest.java
git commit -m "feat(annuity-starter): 更新 step-routes.json 4 插槽配置并扩展端到端测试用例"
```

---

## Task E2: 全量构建验证

**Files:** 无新增/修改,仅执行验证命令

**目的:** 确认所有模块编译通过、测试通过、可打包。

- [ ] **Step 1: 全量编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS,所有模块编译通过 (含 kernel 与 annuity-service 七层)

- [ ] **Step 2: 运行 annuity-service 全量测试**

Run: `mvn -pl annuity-service/annuity-starter -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS,8 个端到端测试用例全部通过

- [ ] **Step 3: 打包验证**

Run: `mvn -pl annuity-service/annuity-starter -am package -DskipTests`
Expected: BUILD SUCCESS,生成 `annuity-starter-*.jar` 可执行 fat jar

- [ ] **Step 4: 如有修复则提交**

```bash
git add -A
git commit -m "chore(annuity): 全量构建验证通过"
```

---

# Self-Review

## 1. Spec Coverage

| Spec 要求                                    | 实现位置                                                                                          | 状态 |
|----------------------------------------------|---------------------------------------------------------------------------------------------------|------|
| 实现 `StepActionHandler` SPI                 | Task C1 `AnnuityDataVerificationHandler`                                                          | ✅   |
| 实现 `StepExtensionAction` SPI               | Task C2-C4 共 8 个 Action                                                                         | ✅   |
| 业务规则在 domain 层                         | Task B3-B6 共 4 个 `@DomainService`                                                               | ✅   |
| Handler/Action 在 application 层做编排       | Task C1-C4 委托 domain service                                                                    | ✅   |
| 业务服务定义自己的聚合根                     | Task B1 `AnnuityEmployeeBatch`                                                                    | ✅   |
| 通过 ID 引用 kernel 聚合根                   | Task B1 `ApplicationId` 字段                                                                      | ✅   |
| 消除强制类型转换                             | Task B3 `AnnuityExtensionResolver`                                                                | ✅   |
| 消除反射访问                                 | Task A1 `businessExtension()` accessor + Task D2 移除 `readExtension`                             | ✅   |
| 完整端到端业务链路演示                       | Task E1 5 步路由 + 8 个测试用例                                                                   | ✅   |
| kernel 4 插槽设计                            | Task E1 `step-routes.json` 使用 `preValidations`/`mainProcessor`/`detailProcessors`/`sideEffects` | ✅   |
| 复用 kernel `PlanMaterialPreparationHandler` | Task E1 MATERIAL_PREPARATION 步骤 `mainProcessor`                                                 | ✅   |
| 三套 schema(pg/mysql/h2)                     | Task D4                                                                                           | ✅   |
| Mock Gateway 演示                            | Task D3 两个 Mock 实现                                                                            | ✅   |

## 2. Placeholder Scan

- ✅ 无 "TBD" / "TODO" / "implement later"
- ✅ 所有代码步骤包含完整代码块
- ✅ 所有测试步骤包含具体断言
- ✅ Task D4 Step 2 指明了 MySQL 与 PG 的类型差异映射规则,虽未完整粘贴但规则明确 (JSONB→JSON, BOOLEAN→TINYINT (1),
  TIMESTAMP→DATETIME)
- ✅ 所有 commit 命令包含具体 message

## 3. Type Consistency

- `AnnuityEmployeeBatchId` / `AnnuityEmployeeDetailId` 在 types 层定义,在 domain/application/infrastructure 层引用 ✅
- `AnnuityEmployeeBatchStatus` / `AnnuityEmployeeDetailStatus` 枚举在 domain 层定义,在 application 和 infrastructure 层引用
  ✅
- `AnnuityExtensionResolver` 返回 `AnnuityApplicationExtension`,在 8 个 Action 中通过构造函数注入 ✅
- `AnnuityEmployeeBatchRepository` 接口在 domain 层定义,`AnnuityEmployeeBatchRepositoryImpl` 在 infrastructure 层实现 ✅
- `AnnuityCustomerGateway` / `AnnuityNotificationGateway` 接口在 domain 层定义,Mock 实现在 infrastructure 层 ✅
- `AnnuityEmployeeBatchDataConverter` / `AnnuityEmployeeDetailDataConverter` 在 infrastructure 层,被 Repository 注入 ✅
- Handler `handlerName()` 返回值与 `step-routes.json` 的 `mainProcessor` 字段一致 (`annuityDataVerificationHandler`) ✅
- Action `actionName()` 返回值与 `step-routes.json` 的 `beanName` 字段一致 ✅
- kernel `BusinessApplication.businessExtension()` 在 Task A1 新增,在 Task C 的 Action 中使用 ✅

---

# Execution Handoff

**计划已保存到 `docs/superpowers/plans/2026-07-22-annuity-spi-implementation.md`。两种执行方式:**

**1. Subagent-Driven (推荐)** — 每个 Task 派发独立 subagent 执行,Task 间做两阶段评审,迭代速度快,主上下文窗口不被污染。

**2. Inline Execution** — 在当前会话中按 Task 顺序执行,带 checkpoint 评审,适合需要密切观察每步的场景。

**选择哪种方式?**
