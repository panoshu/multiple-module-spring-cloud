# 渠道架构重构方案：单库网关 · 双部署 BFF · 框架归位

> 日期：2026-11-03
> 状态：已确认（三关键分叉经用户拍板），Phase 1 实施中
> 前置文档：`2026-08-08-bff-design.md`、`2026-08-09-gateway-split.md`、`2026-08-30-bff-kernel-api-refactoring.md`、`2026-07-26-business-core-kernel-public-api-design.md`

---

## 一、背景与问题

三渠道（INTERNET 互联网 / HQ 内网总部 / BRANCH 专线网点）两通路的整体设计已在既有文档中确立并部分实现。本轮重构解决审计实测发现的差距：

| 编号 | 差距 | 实证 |
|---|---|---|
| G1 | 技术底座反向依赖业务契约 | `bff-shared → business-core-api`、`gateway-starter → auth-api`，二者均物理位于 demo-shared |
| G3/G9 | 结构先于内容 | internet-bff 3 个源文件切 5 层（api/infrastructure 空壳）；两 BFF 的 infrastructure 全空 |
| G7 | 网关拆分遗留 | demo-gateway 在引入 gateway-starter 的同时重复声明其已传递的 10+ 依赖 |
| 模块冗余 | 三个网关壳各只有一个启动类 | internet/intranet/demo-gateway 本质是"同一代码 + 不同配置" |

同时澄清两个架构定性（修正此前审计口径）：

1. **kernel 白盒联编是既定设计**（进程内框架模式，见 kernel public API 设计 §1.2 与 2026-08-30 重构方案），不是意外妥协。优化方向是"规范化白盒用法"而非黑盒化。
2. **渠道业务差异属于领域层**：kernel 已建模 `SessionContext.channelType`（INTERNET/HQ/BRANCH），`DefaultBusinessAccessGuard` 按渠道分叉（INTERNET 代办校验 / BRANCH 二次授权），步骤路由可按渠道维度分叉。BFF 只做渠道化 ViewModel、聚合与协议适配，不承载业务规则。

## 二、已确认决策

| 分叉 | 决策 |
|---|---|
| 网关形态 | **合并为单模块 `channel-gateway`**：一个 artifact，`internet` / `intranet` / `dev` 三 profile；demo-gateway 删除 |
| BFF 形态 | **两个部署 + 共享顶层 `bff-framework`**：不采用单库双 profile（避免配置错误把内网接口暴露到互联网通路） |
| 实施方式 | 设计文档定稿后逐阶段实施（Phase 1 → 4） |

## 三、目标架构

```
互联网用户(INTERNET) ──┐                                  ┌── 总部员工(HQ)
                      ├─→ channel-gateway ×2 部署 ───────┤
专线网点(BRANCH) ─────┘   internet profile / intranet     └─→ (经 intranet 实例)
        │                每渠道独立 StpLogic → 会话命名空间隔离
        ▼
internet-bff / intranet-bff（共享 bff-framework：ScenarioHandler SPI + 调度 + 审计）
        ▼
业务服务（annuity 联编 kernel / approval / file / auth / integration）
        ▼
框架层（全部为顶层独立模块）：
  gateway-framework（渠道侧网关框架）
  bff-framework（场景聚合框架，依赖 business-core-api —— 方向合法）
  business-core-kernel（业务编排引擎）
  demo-shared（纯技术底座，零业务 artifact 依赖）
```

**渠道差异归属准则**：
- 影响领域状态/规则/数据 → 领域层（Channel 值对象 + AccessGuard 分叉 + 步骤路由按渠道分叉）
- 只影响调哪些接口/怎么组装展示 → BFF 层（ViewModel 裁剪、脱敏、聚合）
- 传输安全差异 → 网关 profile（SM4 开关、白名单、CORS）

## 四、模块重组清单

| 现状 | 目标 | 阶段 |
|---|---|---|
| internet-gateway + intranet-gateway + demo-gateway | **channel-gateway**（单模块，3 profile + test profile） | Phase 1 |
| demo-shared/shared-starter/gateway-starter（依赖 auth-api） | 顶层 **gateway-framework**；SessionContext/签名契约下沉 demo-shared | Phase 1b/2 |
| demo-shared/shared-starter/bff-shared | 顶层 **bff-framework**（依赖 kernel 契约合法，归属层修正） | Phase 2 |
| internet-bff / intranet-bff 各 5 层 | 各收敛为 **3 层**（api + web + starter），场景 handler 归 application | Phase 2 |
| annuity-service 白盒复用 + Mock + 渠道映射 | 六项优化（见 §六） | Phase 3 |
| 规则靠自觉 | ArchUnit + maven-enforcer 机器强制 | Phase 4 |

## 五、Phase 1 变更清单（本轮实施）

1. 新建 `channel-gateway` 顶层模块：
   - `pom.xml`：依赖 `gateway-starter` + `httpexchange-spring-boot-autoconfigure` + `spring-cloud-starter-sofa-registry`（与旧 internet-gateway 一致，无冗余声明）
   - `ChannelGatewayApplication`（包 `com.example.gateway`，扫描 gateway-starter 组件 + auth-api 客户端）
   - `application.yml`：公共配置（Redis 环境变量、httpclient 超时、sa-token 基线、日志滚动、crypto 默认关闭），默认 profile 缺省需部署时显式指定
   - `application-internet.yml`：name=internet-gateway、port=18081、channels=[INTERNET]、CORS（satoken-internet）、白名单、internet-bff/auth 路由
   - `application-intranet.yml`：name=intranet-gateway、port=18082、channels=[HQ,BRANCH]、CORS（satoken-hq/branch）、白名单（含二次授权）、intranet-bff/auth/integration 路由
   - `application-dev.yml`：name=gateway、port=18080、channels=[INTERNET,HQ,BRANCH]、全量白名单、全量路由（承接 demo-gateway 开发入口职责）
   - `application-local.yml`：本机覆盖（注册中心地址、开发签名密钥、日志级别），沿用 `spring.profiles.include: local` 惯例
   - `application-test.yml` + `ChannelGatewayApplicationTest`：上下文装配测试（禁 Redis/注册中心，channels=[INTERNET]）
2. 根 pom `<modules>`：移除 internet-gateway / intranet-gateway / demo-gateway，加入 channel-gateway
3. 删除三个旧网关模块目录（git 可恢复；网关相关文件在 git 中干净，未提交改动全部位于 annuity-service，不受影响）
4. 验证：`mvn clean install -pl channel-gateway -am -DskipTests` 全链构建通过 + 测试 profile 上下文加载

> 路由迁移注意：intranet 的 `/integration/**` 路由目标 `lb://cjannuity-channel-integration` 与 integration-service 的注册名待 Phase 2 核对统一；dev profile 沿用 demo-gateway 的 `lb://integration`。

## 六、Phase 3：annuity-service 优化清单（示例业务服务标杆化）

| 问题 | 优化 |
|---|---|
| `mapChannel` INTERNET→NETAPP 硬映射 | 统一渠道枚举到 kernel 契约 ChannelType，删除私有映射 |
| `Mock*@Primary` 四个网关 | `@Profile("dev")` / `@ConditionalOnProperty` 开关，真实实现为默认 |
| `JsonBusinessConfigGateway` classpath JSON | 抽 `ConfigSource` SPI：dev=classpath，prod=DB/配置中心 |
| Repository version 重置 hack | DB 版本回写语义封装进仓储基类 |
| kernel DO/Mapper 直接复用 | 收敛为 kernel 提供的仓储/基类，annuity 不 import kernel infrastructure 内部类 |
| 白盒复用无边界 | 显式"公共门面清单"（FlowOrchestrationService、BusinessXxxAppService、SessionContextResolver、AccessGuard），ArchUnit 固化禁用其余内部件 |

## 七、验证方式（每阶段通用）

- `mvn clean install -DskipTests` 全量构建通过
- 各 profile 启动：`FilterOrderChecker` 打印过滤器顺序无冲突；渠道注册与 `gateway.channels.enabled` 一致
- 旧端口号不变（18081/18082/18080），前端与运维无感知
- Phase 4 后：ArchUnit 规则红转绿（domain 零 Spring、application 不见 infrastructure、demo-shared 零业务 artifact 依赖）
