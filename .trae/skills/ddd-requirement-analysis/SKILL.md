---
name: "ddd-requirement-analysis"
description: "DDD战略设计技能：指导需求分析、事件风暴、限界上下文识别和上下文映射。当用户需要分析业务需求、识别领域边界或设计微服务划分时调用。"
---

# DDD战略设计：需求分析与领域划分

## 一、通用语言（Ubiquitous Language）

### 1.1 概念
业务专家与开发团队共同使用的、无歧义的标准化词汇表。代码中的类名、方法名必须与通用语言一一对应。

### 1.2 实践方法
- 收集业务领域中的核心术语
- 建立术语词典，明确每个术语的业务含义
- 在代码中严格使用这些术语命名

### 1.3 示例
- 业务说"下单"，代码里就是 `Order.place()`，而不是 `Order.create()` 或 `Order.save()`
- 业务说"受理"，代码里就是 `Acceptance.submit()`

---

## 二、事件风暴（Event Storming）

### 2.1 概念
一种协作式的需求分析方法，通过收集领域中的关键事件，识别业务流程中的核心概念和边界。

### 2.2 事件风暴步骤

```
1. 收集领域事件（Domain Event）—— 描述已经发生的事实，用过去时态
2. 识别命令（Command）—— 触发事件的操作指令
3. 识别实体（Entity）和值对象（Value Object）—— 业务对象
4. 识别聚合根（Aggregate Root）—— 一致性边界
5. 划定限界上下文（Bounded Context）—— 语义边界
6. 建立上下文映射（Context Map）—— 上下文之间的关系
```

### 2.3 核心产出物
- **事件列表**：领域中发生的所有关键事件
- **命令列表**：触发事件的操作指令
- **实体列表**：拥有唯一标识的业务对象
- **聚合边界**：事务一致性边界
- **限界上下文**：语义边界划分

### 2.4 事件风暴模板

#### 领域事件卡片
```
[事件名称（过去时态）]
├─ 触发命令：[命令名称]
├─ 涉及实体：[实体1]、[实体2]
├─ 变更状态：[旧状态] → [新状态]
└─ 业务规则：[规则描述]
```

#### 示例
```
[ApplicationSubmitted]
├─ 触发命令：SubmitApplication
├─ 涉及实体：BusinessApplication、BusinessForm
├─ 变更状态：INITIAL → SUBMITTED
└─ 业务规则：表单必须完整，受理编号不能为空
```

---

## 三、限界上下文（Bounded Context）

### 3.1 概念
一个明确的语义和业务边界。同一个词汇在不同上下文里含义不同。

### 3.2 识别方法
- 通过事件风暴识别业务边界
- 根据业务能力划分独立的上下文
- 每个上下文有自己的通用语言

### 3.3 示例

| 上下文 | "商品"的含义 |
|--------|-------------|
| 商品上下文 | 名称、规格、详情、价格 |
| 订单上下文 | 快照价格、购买数量、商品ID |
| 库存上下文 | SKU编码、可用数量、仓库位置 |

### 3.4 本项目限界上下文

| 上下文 | 职责 | 核心聚合根 |
|--------|------|-----------|
| business-core-kernel | 业务申请、批次、表单 | BusinessApplication、BusinessBatch、BusinessForm |
| customer-service | 客户管理 | Customer |
| file-service | 文件处理、模板管理 | InboundTemplate、OutboundTemplate |
| integration-service | 外部系统集成 | IntegrationTask |

---

## 四、上下文映射（Context Mapping）

### 4.1 概念
描述不同限界上下文之间的集成关系。

### 4.2 关系类型

| 关系类型 | 描述 | 适用场景 |
|---------|------|----------|
| **Shared Kernel** | 共享核心模型 | 多个上下文共享通用领域模型 |
| **Customer-Supplier** | 上下游依赖关系 | 一个上下文依赖另一个上下文的服务 |
| **Conformist** | 顺从外部模型 | 依赖外部系统，无法影响其设计 |
| **Anti-Corruption Layer** | 防腐层隔离 | 需要隔离外部系统影响 |
| **Open Host Service** | 开放服务接口 | 提供标准化API供外部调用 |
| **Published Language** | 共享语言协议 | 使用公共协议进行通信 |
| **Separate Ways** | 完全独立 | 无任何依赖关系 |

### 4.3 本项目上下文映射

```
customer-service ───(ACL)───> business-core-kernel
file-service ───(ACL)───> business-core-kernel
integration-service ───(ACL)───> business-core-kernel
```

### 4.4 防腐层设计要点

#### ACL设计原则
- 将外部系统的模型转换为内部领域模型
- 隔离外部系统的变化对内部领域的影响
- 定义清晰的网关接口（Gateway）

#### 网关接口示例（Domain层）
```java
public interface ExternalPaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
}
```

#### 网关实现（Infrastructure层）
```java
public class ExternalPaymentGatewayImpl implements ExternalPaymentGateway {
    // 调用外部支付系统，转换模型
}
```

---

## 五、战略设计产出物

### 5.1 领域事件清单
```markdown
## 领域事件清单

### 业务申请上下文
- ApplicationSubmitted - 申请已提交
- ApplicationApproved - 申请已审批
- ApplicationRejected - 申请已拒绝
- ApplicationWithdrawn - 申请已撤回

### 批次上下文
- BatchCreated - 批次已创建
- BatchSubmitted - 批次已提交
- BatchProcessed - 批次已处理
```

### 5.2 限界上下文定义
```markdown
## 限界上下文定义

### business-core-kernel
- **边界描述**：业务申请、批次、表单的核心业务逻辑
- **核心模型**：BusinessApplication、BusinessBatch、BusinessForm
- **对外接口**：business-core-api

### customer-service
- **边界描述**：客户信息管理
- **核心模型**：Customer
- **依赖**：business-core-api（通过ACL）
```

### 5.3 上下文映射图
```markdown
## 上下文映射图

```
                    ┌──────────────────────┐
                    │  business-core-kernel │
                    │  (Shared Kernel)     │
                    └──────────┬───────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ customer-service│  │  file-service   │  │integration-service│
│   (Consumer)    │  │   (Consumer)    │  │    (Consumer)    │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```
```

---

## 六、战略设计检查清单

- [ ] 是否建立了通用语言词典？
- [ ] 是否通过事件风暴识别了所有核心事件？
- [ ] 是否明确了限界上下文的边界？
- [ ] 是否定义了上下文之间的关系？
- [ ] 是否识别了需要防腐层的集成点？
- [ ] 是否确定了共享内核的范围？
- [ ] 是否考虑了上下文之间的通信协议？
