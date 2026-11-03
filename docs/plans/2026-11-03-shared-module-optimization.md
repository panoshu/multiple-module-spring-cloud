# demo-shared 模块剩余优化项执行计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 消除 demo-shared 模块体系中残留的三个根本性矛盾——层次违规、反向依赖、职责膨胀，使 shared-lib 真正成为"纯 Java、无 Spring、无业务依赖"的公共基础层。

**Architecture:** 采用三层分离（lib → core → starter）+ 限界上下文独立原则。每个优化项以"先建后拆、渐进迁移、编译验证"的方式推进，确保每一步都可独立编译通过。

**Tech Stack:** Maven 多模块、JDK 25、Spring Boot 3.5.14 AutoConfiguration、MyBatis-Flex、MapStruct

**前置约束：** 修改后必须 `mvn clean install -DskipTests` 编译通过；每个 Task 独立提交。

---

## 当前进展概览

| 原始优化项 | 状态 | 说明 |
|---|---|---|
| P0-1: gateway-shared 迁出 shared-lib | 部分完成 | 位置已调整，仍驻 shared-lib，仍依赖 auth-api |
| P0-2: bff-shared 下沉到各 BFF | 部分完成 | Spring 注解已清除，仍驻 shared-lib，仍依赖 business-core-api + annuity-api |
| P0-3: shared-lock 拆出 starter | 未完成 | AutoConfiguration 仍在 shared-lib 中 |
| P1-1: shared-types 业务 ID 归还 | 部分完成 | ApplicationId/BatchId 已迁至 business-core-types；AcceptanceNo/CustomerNo/EventId/FileId/TaskId/SerialNo/FileAccessLogId 仍在 shared-types |
| P1-2: shared-types 值对象/枚举归还 | 未完成 | AnnuityChannel/OperatingMode/Address/Email/Mobile/Telephone/PostalCode/IdentityDocument/IdentityType/DocumentNumber/ValidityPeriod 仍在 shared-types |
| P1-3: 统一分页模型 | 未完成 | PageInfo/Pagination 仍在 shared-types，与 shared-api 的 PageData/PageQuery 重复 |
| P1-4: 事件类下放 | 未完成 | EventDispatcher/EventStore/IntegrationEventConverter 仍在 shared-domain |
| P2-1: AggregateRoot.markUpdated 解耦 UserNo | 未完成 | Entity/AggregateRoot 构造函数和 markUpdated 仍接受 UserNo 类型 |
| P2-2: DistributedLockFactory 魔法值 | 未完成 | 硬编码 "localDistributedLock"/"redissonDistributedLock" |
| P2-3: lockAndRun 裸 RuntimeException | 未完成 | 应使用 SystemException + LockErrorDefinition |

---

## Task 1: 创建 shared-lock-starter 模块

**目标：** 将 shared-lock 中的 AutoConfiguration 拆至独立的 shared-lock-starter，使 shared-lib/shared-lock 回归纯 Java。

**Files:**
- Create: `demo-shared/shared-starter/shared-lock-starter/pom.xml`
- Create: `demo-shared/shared-starter/shared-lock-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Move: `demo-shared/shared-lib/shared-lock/.../autoconfigure/DistributedLockAutoConfiguration.java` → `demo-shared/shared-starter/shared-lock-starter/.../autoconfigure/DistributedLockAutoConfiguration.java`
- Delete: `demo-shared/shared-lib/shared-lock/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `demo-shared/shared-starter/pom.xml`
- Modify: `demo-shared/shared-lib/shared-lock/pom.xml`
- Modify: `demo-shared/shared-starter/shared-id-starter/pom.xml`
- Modify: `demo-shared/shared-starter/shared-cache-starter/pom.xml`
- Modify: `demo-shared/shared-starter/shared-event-starter/pom.xml`

**Step 1:** 创建 shared-lock-starter 目录和 pom.xml（参照 shared-cache-starter 结构），parent 为 shared-starter，依赖 shared-lock + spring-boot-autoconfigure。

**Step 2:** 将 DistributedLockAutoConfiguration.java 移至 shared-lock-starter（包路径不变）。

**Step 3:** 创建 AutoConfiguration.imports 文件，内容为 `com.example.shared.lock.autoconfigure.DistributedLockAutoConfiguration`。

**Step 4:** 删除 shared-lib/shared-lock 中的 AutoConfiguration 文件和 autoconfigure 目录。

**Step 5:** 从 shared-lock/pom.xml 移除 spring-boot-autoconfigure 和 redisson-spring-boot-starter 依赖（redisson 仅 AutoConfiguration 使用，starter 已传递）。

**Step 6:** 在 shared-starter/pom.xml 添加 `<module>shared-lock-starter</module>`。

**Step 7:** 将 shared-id-starter、shared-cache-starter、shared-event-starter 中的 shared-lock 依赖改为 shared-lock-starter。

**Step 8:** 编译验证：`mvn clean install -pl demo-shared -am -DskipTests`

**Step 9:** 提交：`build(shared-lock): 拆出 shared-lock-starter，shared-lib 回归纯 Java`

---

## Task 2: 修复 DistributedLockFactory 硬编码 bean 名称

**目标：** 消除魔法字符串，使用常量引用。

**Files:**
- Modify: `demo-shared/shared-lib/shared-lock/src/main/java/com/example/shared/lock/DistributedLock.java`
- Modify: `demo-shared/shared-lib/shared-lock/src/main/java/com/example/shared/lock/DistributedLockFactory.java`
- Modify: `demo-shared/shared-starter/shared-lock-starter/src/main/java/com/example/shared/lock/autoconfigure/DistributedLockAutoConfiguration.java`
- Modify: `demo-shared/shared-starter/shared-id-starter/src/main/java/com/example/shared/id/autoconfigure/DistributedLockConfiguration.java`

**Step 1:** 在 DistributedLock 接口中定义常量：

```java
String LOCAL_BEAN_NAME = "localDistributedLock";
String REDISSON_BEAN_NAME = "redissonDistributedLock";
```

**Step 2:** DistributedLockFactory.getLock() 中替换硬编码：

```java
String beanName = type == LockType.LOCAL
    ? DistributedLock.LOCAL_BEAN_NAME
    : DistributedLock.REDISSON_BEAN_NAME;
DistributedLock lock = lockMap.get(beanName);
if (lock == null) {
    return lockMap.get(DistributedLock.LOCAL_BEAN_NAME);
}
return lock;
```

**Step 3:** DistributedLockAutoConfiguration 中 `@Bean("redissonDistributedLock")` → `@Bean(DistributedLock.REDISSON_BEAN_NAME)`

**Step 4:** DistributedLockConfiguration 中方法名 localDistributedLock 保持（Spring 方法名即 bean 名，与常量值一致）。

**Step 5:** 编译验证 + 提交：`refactor(shared-lock): 用常量替换硬编码 bean 名称`

---

## Task 3: 修复 lockAndRun 裸 RuntimeException

**目标：** 对齐异常体系，获取锁失败时抛出 SystemException + LockErrorDefinition。

**Files:**
- Modify: `demo-shared/shared-lib/shared-lock/src/main/java/com/example/shared/lock/DistributedLock.java`
- Modify: `demo-shared/shared-lib/shared-lock/src/test/java/com/example/shared/lock/DistributedLockFactoryTest.java`

**Step 1:** 替换异常：

```java
throw new SystemException(LockErrorDefinition.GET_LOCK_FAILED)
    .withLogDetail("Failed to acquire lock: " + key);
```

**Step 2:** 添加 import：`com.example.shared.exception.SystemException` 和 `com.example.shared.lock.errorcode.LockErrorDefinition`

**Step 3:** 更新测试中断言：`assertThatThrownBy(...).isInstanceOf(SystemException.class)`

**Step 4:** 编译验证 + 提交：`fix(shared-lock): lockAndRun 获取锁失败时抛 SystemException 替代裸 RuntimeException`

---

## Task 4: 迁移 AcceptanceNo 到 annuity-types

**目标：** AcceptanceNo 仅年金服务使用，迁至 annuity-types。

**Files:**
- Create: `annuity-service/annuity-types/src/main/java/com/example/annuity/types/AcceptanceNo.java`
- Delete: `demo-shared/shared-lib/shared-types/src/main/java/com/example/shared/identifier/id/AcceptanceNo.java`

**Step 1:** 在 annuity-types 中创建 AcceptanceNo（复制原实现，修改 package）。

**Step 2:** 全局搜索确认无外部引用后删除 shared-types 中的原文件。

**Step 3:** 编译验证 + 提交：`refactor(annuity-types): 将 AcceptanceNo 从 shared-types 迁移至 annuity-types`

---

## Task 5: 迁移 EventId 到 shared-event-core

**目标：** EventId 是事件基础设施标识，语义上属于 shared-event-core。约 60 个文件需更新 import。

**Files:**
- Create: `demo-shared/shared-core/shared-event-core/src/main/java/com/example/shared/event/EventId.java`
- Delete: `demo-shared/shared-lib/shared-types/src/main/java/com/example/shared/identifier/id/EventId.java`
- Modify: ~60 个引用文件的 import 语句

**Step 1:** 在 shared-event-core 中创建 EventId（包含 generate() 工厂方法）。

**Step 2:** 全局替换 import：`com.example.shared.identifier.id.EventId` → `com.example.shared.event.EventId`

涉及文件清单（按模块）：
- shared-domain: DomainEvent.java
- business-core-domain: 6 个事件类
- auth-domain: ~30 个事件类
- file-domain: 9 个事件类
- approval-domain: 8 个事件类

**Step 3:** 删除 shared-types 中的 EventId.java

**Step 4:** 全项目编译验证：`mvn clean install -DskipTests`

**Step 5:** 提交：`refactor(event-core): 将 EventId 从 shared-types 迁移至 shared-event-core`

---

## Task 6: 迁移 FileAccessLogId 到 file-types

**目标：** FileAccessLogId 仅 file-service 内部使用。

**Step 1:** 在 file-types 中创建 FileAccessLogId（修改 package）。

**Step 2:** 替换 file-domain/infrastructure 中的 import。

**Step 3:** 删除 shared-types 中的原文件。

**Step 4:** 编译验证 + 提交：`refactor(file-types): 将 FileAccessLogId 从 shared-types 迁移至 file-types`

---

## Task 7: 迁移 SerialNo 和 TaskId

**目标：** SerialNo（交易流水号）和 TaskId（解析任务 ID）是业务特定 ID。

**Step 1:** 分析归属——TaskId 仅 file-service 使用 → file-types；SerialNo 被 integration-service 和 business-core 使用 → 需保留 shared-types 或迁至 business-core-types。

**Step 2:** 迁移 TaskId 到 file-types，替换 import。

**Step 3:** 对 SerialNo，若仅被 integration-service 使用则迁至 integration-service-types（需确认该 types 模块存在）；否则暂留 shared-types 并标记 TODO。

**Step 4:** 编译验证 + 提交：`refactor(types): 将 TaskId 从 shared-types 迁移至 file-types`

---

## Task 8: 迁移 AnnuityChannel/OperatingMode 到 annuity-types

**目标：** 年金业务枚举，不属于公共原语。当前生产代码无外部引用。

**Step 1:** 在 annuity-types 中创建两个枚举（复制实现，修改 package）。

**Step 2:** 替换所有引用（如有）。

**Step 3:** 删除 shared-types 中的原文件。

**Step 4:** 编译验证 + 提交：`refactor(annuity-types): 将 AnnuityChannel/OperatingMode 从 shared-types 迁移至 annuity-types`

---

## Task 9: 迁移联系信息值对象到 auth-domain

**目标：** Address/Email/Mobile/Telephone/PostalCode 仅 auth-service 使用。

**Files:**
- Create: auth-domain 中 `com.pension.permission.domain.channel.valueobject` 包下的 5 个 record
- Delete: shared-types 中 `com.example.shared.contactinfo` 包下的 5 个文件
- Modify: auth-adapter/infrastructure 中约 12 个文件的 import

**Step 1:** 在 auth-domain 的 valueobject 包中创建 5 个 record（保持实现不变）。

**Step 2:** 替换 auth-service 中所有 `com.example.shared.contactinfo.*` import。

**Step 3:** 删除 shared-types 中的 5 个原文件。

**Step 4:** 编译验证：`mvn clean install -pl auth-service -am -DskipTests`

**Step 5:** 提交：`refactor(auth-domain): 将联系信息值对象从 shared-types 迁移至 auth-domain`

---

## Task 10: 迁移 IdentityDocument 值对象到 auth-domain

**目标：** IdentityDocument/IdentityType/DocumentNumber 仅 auth-service 使用。

**Step 1:** 在 auth-domain 中创建 3 个值对象。

**Step 2:** 替换 UserAggregate.java 和 UserConverter.java 的 import（仅 2 个文件）。

**Step 3:** 删除 shared-types 中的原文件。

**Step 4:** 编译验证 + 提交：`refactor(auth-domain): 将 IdentityDocument 值对象从 shared-types 迁移至 auth-domain`

---

## Task 11: 迁移 ValidityPeriod 到 auth-domain

**目标：** ValidityPeriod 主要被凭证/授权聚合使用。

**Step 1:** 在 auth-domain 的 valueobject 包中创建 ValidityPeriod（复制完整 ~80 行实现）。

**Step 2:** 替换 auth-domain/infrastructure 中约 6 个文件的 import。

**Step 3:** 删除 shared-types 中的原文件。

**Step 4:** 编译验证 + 提交：`refactor(auth-domain): 将 ValidityPeriod 从 shared-types 迁移至 auth-domain`

---

## Task 12: 删除 shared-types 中的 PageInfo/Pagination

**目标：** 消除分页模型二义性，统一使用 shared-api 的 PageData/PageQuery。

**Step 1:** 确认无外部引用（当前为 0）。

**Step 2:** 删除 `com.example.shared.page` 包下的 PageInfo.java 和 Pagination.java。

**Step 3:** 编译验证 + 提交：`refactor(shared-types): 删除废弃的 PageInfo/Pagination，统一使用 shared-api 的 PageData/PageQuery`

---

## Task 13: 迁移 EventDispatcher/EventStore/IntegrationEventConverter 到 shared-event-core

**目标：** 保持 shared-domain 元模型纯粹，将具体策略接口下放。

**Files:**
- Create: shared-event-core 中 3 个接口（新包路径）
- Modify: shared-event-core 中已有实现类的 import
- Delete: shared-domain 中 3 个原文件

**Step 1:** 在 shared-event-core 中创建：
- `com.example.shared.event.dispatcher.EventDispatcher`
- `com.example.shared.event.store.EventStore`
- `com.example.shared.event.converter.IntegrationEventConverter`

保持接口签名不变。

**Step 2:** 更新 shared-event-core 实现类的 import：
- SpringEventDispatcher、RedisEventDispatcher、RocketMQEventDispatcher
- JdbcEventStore
- EventDeliverer、EventRecoveryJob

**Step 3:** 从 shared-domain 删除原文件。

**Step 4:** 全项目编译验证。

**Step 5:** 提交：`refactor(event-core): 将 EventDispatcher/EventStore/IntegrationEventConverter 从 shared-domain 下放至 shared-event-core`

---

## Task 14: 解耦 Entity/AggregateRoot 对 UserNo 的类型依赖

**目标：** 元模型不应耦合具体业务 ID 类型。将 markUpdated 和构造函数参数从 UserNo 改为 String。

**风险评估：** 影响面最大（约 200+ 文件），必须最后执行且全项目编译验证。

**Files:**
- Modify: `demo-shared/shared-lib/shared-domain/.../entity/Entity.java`
- Modify: `demo-shared/shared-lib/shared-domain/.../aggregate/root/AggregateRoot.java`
- Modify: 全项目所有 Entity/AggregateRoot 子类、Converter、Repository 实现

**Step 1:** 修改 Entity 基类字段和构造函数：UserNo → String。

**Step 2:** 修改 markUpdated 签名：`markUpdated(UserNo)` → `markUpdated(String)`。

**Step 3:** 修改 accessors：`createdBy()`/ `updatedBy()` 返回 String。

**Step 4:** 全局批量替换：
- 构造函数调用：`super(id, userNo)` → `super(id, userNo.value())`
- markUpdated 调用：`markUpdated(userNo)` → `markUpdated(userNo.value())`
- Converter toDomain：`UserNo.of(do.getCreatedBy())` → `do.getCreatedBy()`
- Converter toDO：`entity.createdBy().value()` → `entity.createdBy()`

**Step 5:** 删除 Entity/AggregateRoot 中的 UserNo import。

**Step 6:** 全项目编译验证：`mvn clean install -DskipTests`

**Step 7:** 提交：`refactor(shared-domain): 解耦 Entity/AggregateRoot 对 UserNo 类型依赖，改用 String`

---

## Task 15: gateway-shared 迁出 shared-lib

**目标：** 消除 shared-lib → auth-api 反向依赖。

**Files:**
- Move: `demo-shared/shared-lib/gateway-shared/` → `demo-shared/shared-starter/gateway-starter/`
- Modify: shared-lib/pom.xml, shared-starter/pom.xml
- Modify: 3 个网关应用 pom.xml
- Modify: 根 pom.xml dependencyManagement

**Step 1:** 移动目录，更新 pom（parent 改为 shared-starter，artifactId 改为 gateway-starter）。

**Step 2:** 更新 shared-lib/pom.xml 移除 gateway-shared module。

**Step 3:** 更新 shared-starter/pom.xml 添加 gateway-starter module。

**Step 4:** 更新 internet-gateway、intranet-gateway、demo-gateway 的 pom 依赖。

**Step 5:** 更新根 pom dependencyManagement。

**Step 6:** 全项目编译验证。

**Step 7:** 提交：`refactor(gateway-starter): 将 gateway-shared 从 shared-lib 迁至 shared-starter`

---

## Task 16: bff-shared 解耦反向依赖

**目标：** 消除 bff-shared → business-core-api + annuity-api 反向依赖。

**方案：** 将具体业务 DTO、AnnuityScenarioHandler、BffBusinessService/Api 下沉到各 BFF；bff-shared 仅保留 ScenarioHandlerRegistry、BusinessScenarioHandler 接口、BffAggregationException、BffErrorCode。

**Step 1:** 将 BffCreateBatchRequest 等 8 个 DTO 移至各 BFF 的 api 模块。

**Step 2:** 将 AnnuityScenarioHandler 移至各 BFF 的 application 模块。

**Step 3:** 将 BffBusinessService/BffAggregationService/BffBusinessApi/BffResponseAssembler 移至各 BFF 的 application/api 模块。

**Step 4:** bff-shared pom.xml 移除 business-core-api 和 annuity-api 依赖。

**Step 5:** 各 BFF 的 application/api 模块添加 business-core-api 和 annuity-api 依赖。

**Step 6:** 全项目编译验证。

**Step 7:** 提交：`refactor(bff-shared): 解耦反向依赖，业务编排逻辑下沉至各 BFF`

---

## 执行顺序与分组

```
第 1 轮 (低风险)    Task 1, 2, 3 — shared-lock 模块整改
第 2 轮 (低风险)    Task 4, 6, 7, 8 — 无/窄引用 ID 和枚举迁移
第 3 轮 (中风险)    Task 5 — EventId 迁移 (60+ 文件)
第 4 轮 (低-中)     Task 9, 10, 11 — auth-domain 值对象迁移
第 5 轮 (低风险)    Task 12 — 纯删除
第 6 轮 (中风险)    Task 13 — 事件接口下放
第 7 轮 (中风险)    Task 15, 16 — 模块迁移 (gateway/bff)
第 8 轮 (高风险)    Task 14 — UserNo 解耦 (200+ 文件，最后执行)
```

**并行性说明：** 同一轮次内的 Task 可并行执行，但建议串行以便每步提交独立。第 8 轮必须在所有其他 Task 完成后执行，因为其影响面覆盖全项目。