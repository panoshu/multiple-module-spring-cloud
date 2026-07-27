# Task 4 报告: BusinessBatchApi 接口与 DTO

**Status:** DONE

## 任务概述

在 `business-core-kernel/business-core-api` 模块的 `com.example.core.api.batch` 包下新增批次管理 API 接口及配套 Command/Query/Response DTO,所有代码逐字照搬自 `task-4-brief.md`。

## 创建的文件(8 个)

| # | 绝对路径 | 类型 |
|---|---------|------|
| 1 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\BusinessBatchApi.java` | API 接口 |
| 2 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\command\CreateBatchCommand.java` | Command |
| 3 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\command\CancelBatchCommand.java` | Command |
| 4 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\query\FindActiveBatchQuery.java` | Query |
| 5 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\query\GetBatchDetailQuery.java` | Query |
| 6 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\response\BatchSummaryResponse.java` | Response |
| 7 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\response\BatchCreatedResponse.java` | Response |
| 8 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\batch\response\BatchDetailResponse.java` | Response(含嵌套 `FormSummary`) |

## 提交信息

- **Commit Hash:** `57f72f9`
- **分支:** `feature/iam-service-plan1-authentication`
- **Message(逐字保留 brief 指定文案):**

```
feat(core-api): 新增 BusinessBatchApi 接口与配套 DTO

1. BusinessBatchApi 定义批次查询/创建/详情/取消 4 个公共接口
2. CreateBatchCommand 仅含 businessType+planNo+operatorRemark,敏感字段由后端组装
3. 配套 FindActiveBatchQuery/GetBatchDetailQuery/CancelBatchCommand
4. 配套 BatchSummaryResponse/BatchCreatedResponse/BatchDetailResponse
```

- **文件统计:** 8 files changed, 198 insertions(+)
- **暂存范围:** 仅 `business-core-api/src/main/java/com/example/core/api/batch/` 下新增文件,未触动其他模块的工作区改动

## 编译验证

命令:`mvn compile -pl business-core-kernel/business-core-api -am`

结果:
```
[INFO] BUILD SUCCESS
```
退出码 0。JDK 25 + `--enable-preview` 编译通过,仅有 Lombok 触及 `sun.misc.Unsafe` 的常规警告(与业务无关)。

## pom.xml 依赖核查

**无需新增依赖。** `business-core-api/pom.xml` 未修改。

依赖来源确认:
- `com.example.shared.web.core.api.ApiResult` ← `shared-api` 模块(已是 business-core-api 直接依赖)
- `org.springframework.web.service.annotation.HttpExchange` / `@PostExchange` ← `org.springframework:spring-web`(由 `shared-api/pom.xml` 直接引入,传递可用)
- `org.springframework.web.bind.annotation.RequestBody` ← 同上(`spring-web`)
- `jakarta.validation.Valid` / `@NotBlank` / `@NotNull` ← `jakarta.validation:jakarta.validation-api`(由 `shared-api/pom.xml` 直接引入,传递可用)

`shared-api/pom.xml` 的 dependencies 节明确包含:
```xml
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-web</artifactId>
</dependency>
<dependency>
  <groupId>jakarta.validation</groupId>
  <artifactId>jakarta.validation-api</artifactId>
</dependency>
```
所有 brief 列举的"consumes"类均通过 `shared-api` 传递,无需在 business-core-api 显式新增依赖。

## 自检清单

| # | 项 | 结果 |
|---|----|------|
| 1 | `mvn compile -pl business-core-kernel/business-core-api` BUILD SUCCESS | ✅ 通过 |
| 2 | 8 个文件按 brief 包路径创建 | ✅ 通过 |
| 3 | `BusinessBatchApi` 含 `@HttpExchange("/core/batch")` 与 4 个 `@PostExchange` 方法 | ✅ 通过(`findActive`/`create`/`detail`/`cancel`) |
| 4 | 所有 DTO 为 record,带恰当的 `@NotBlank`/`@NotNull` 校验注解 | ✅ 通过 |
| 5 | `BatchDetailResponse` 包含嵌套 `FormSummary` record | ✅ 通过 |
| 6 | 所有 public 类含 Javadoc 与 `@author panoshu` | ✅ 通过 |
| 7 | `BusinessBatchApi` Javadoc 含"后续新增接口流程"指南 | ✅ 通过(4 步 `<ol>` 列表) |
| 8 | Commit 遵循 Conventional Commits,scope=`core-api` | ✅ 通过 |

## 关键设计落地点

- **敏感字段隔离**:`CreateBatchCommand` 仅暴露 `businessType + planNo + operatorRemark`,客户/产品/账管人等敏感字段不进入前端契约,与 Task 1 的 `BusinessMetaContext` + `BusinessMetaContextAssembler` 设计闭环。
- **协议优先**:`BusinessBatchApi` 为纯接口,只声明 `@HttpExchange`/`@PostExchange`/`@Valid`/`@RequestBody`,不含任何实现逻辑,完全符合规则 04 二节"API 层约束"。
- **不可变 DTO**:全部为 record 类型,符合规则 03 五节"值对象约束"。
- **路径前缀统一**:`/core/batch` 与 starter 路由规则(`demo-gateway` → `/core/**`)对齐。

## 关注事项(无)

无阻塞性问题,无残留 TODO,无未完成项。下游 Task 5(application 层 AppService)可直接基于本接口契约实现。
