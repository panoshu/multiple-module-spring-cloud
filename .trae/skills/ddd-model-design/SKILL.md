---
name: "ddd-model-design"
description: "DDD战术设计技能：指导领域模型设计，包括领域分包、实体、值对象、聚合根、领域服务、领域事件、错误码的设计方法和代码模板。当用户需要设计或重构领域模型时调用。"
---

# DDD战术设计：领域模型设计指南

## 一、领域分包结构

### 1.1 标准分包

```
com.example.{service}.domain/
├── aggregate/
│   ├── root/          ← 聚合根
│   ├── entity/        ← 实体
│   └── valueobject/   ← 值对象
├── event/             ← 领域事件
├── repository/        ← Repository接口
├── service/           ← 领域服务
├── gateway/           ← 防腐层网关接口
└── errorcode/         ← 错误码定义
```

### 1.2 多业务分包

当domain层有多重业务时，按业务子包组织：

```
com.example.{service}.domain/
├── approval/                    ← 审批业务
│   ├── aggregate/
│   │   ├── root/ApprovalFlowConfig.java
│   │   ├── entity/ApprovalNode.java
│   │   └── valueobject/Approver.java
│   ├── event/
│   ├── repository/
│   ├── service/
│   ├── gateway/
│   └── errorcode/
├── process/                     ← 流程业务
│   ├── aggregate/
│   │   ├── root/ApprovalProcess.java
│   │   ├── entity/ApprovalTask.java
│   │   └── valueobject/
│   ├── event/
│   ├── repository/
│   ├── service/
│   └── errorcode/
```

---

## 二、领域原语（Domain Primitive）

### 2.1 ID类领域原语（定义在types层）

```java
package com.example.order.types.id;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.IdType;
import com.example.shared.primitives.identity.Identifier;

@IdDefinition(type = IdType.ULID)
public record OrderId(String value) implements Identifier<String> {
}
```

### 2.2 非ID类领域原语（定义在domain层）

```java
package com.example.order.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record PhoneNumber(String value) implements ValueObject {

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("number不能为空");
        }
        if (!value.matches("^0?[1-9]{2,3}-?\\d{8}$")) {
            throw new IllegalArgumentException("number格式错误");
        }
    }
}
```

---

## 三、值对象（Value Object）

### 3.1 设计原则
- 不可变（Immutable），无ID
- 实现 `ValueObject` 接口
- 使用 record 类型
- 校验逻辑封装在构造函数中

### 3.2 代码模板

```java
package com.example.order.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import java.math.BigDecimal;

public record Money(BigDecimal amount, String currency) implements ValueObject {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("货币类型不能为空");
        }
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币类型不匹配");
        }
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币类型不匹配");
        }
        return new Money(amount.subtract(other.amount), currency);
    }
}
```

### 3.3 枚举值对象

```java
package com.example.order.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

public enum OrderStatus implements ValueObject {
    PENDING("PENDING"),
    PLACED("PLACED"),
    PAID("PAID"),
    SHIPPED("SHIPPED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static OrderStatus of(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus: " + code);
    }
}
```

---

## 四、实体（Entity）

### 4.1 设计原则
- 继承 `Entity<ID>`，ID实现 `Identifier<?>`
- 实现 `validateInvariants()` 方法
- 业务创建用构造函数（id, createdBy），数据库重建用全参构造函数
- 更新操作调用 `markUpdated(UserNo)`
- 不变式校验使用 `DomainException` + `ErrorDefinition`

### 4.2 代码模板

```java
package com.example.order.domain.aggregate.entity;

import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import com.example.order.domain.aggregate.valueobject.Money;
import com.example.order.domain.errorcode.OrderDomainErrorCode;
import com.example.order.types.id.OrderId;
import com.example.order.types.id.ProductId;

import java.time.LocalDateTime;

public class OrderItem extends Entity<ProductId> {

    private Integer quantity;
    private Money price;
    private OrderId orderId;

    // 业务创建
    public OrderItem(ProductId id, UserNo createdBy, Integer quantity, Money price, OrderId orderId) {
        super(id, createdBy);
        this.quantity = quantity;
        this.price = price;
        this.orderId = orderId;
        this.validateInvariants();
    }

    // 从数据库重建
    public OrderItem(ProductId id, UserNo createdBy, UserNo updatedBy,
                     LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
                     Integer quantity, Money price, OrderId orderId) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.quantity = quantity;
        this.price = price;
        this.orderId = orderId;
    }

    public void updateQuantity(Integer newQuantity, UserNo operator) {
        this.quantity = newQuantity;
        this.markUpdated(operator);
        this.validateInvariants();
    }

    @Override
    protected void validateInvariants() {
        if (quantity == null || quantity <= 0) {
            throw new DomainException(OrderDomainErrorCode.INVALID_DATA)
                .withLogDetail("数量必须大于0");
        }
        if (price == null) {
            throw new DomainException(OrderDomainErrorCode.INVALID_DATA)
                .withLogDetail("价格不能为空");
        }
    }

    public Money getTotal() {
        return new Money(price.amount().multiply(BigDecimal.valueOf(quantity)), price.currency());
    }

    // getters
    public Integer quantity() { return quantity; }
    public Money price() { return price; }
    public OrderId orderId() { return orderId; }
}
```

---

## 五、聚合根（Aggregate Root）

### 5.1 设计原则
- 继承 `AggregateRoot<ID>`（`AggregateRoot` 继承 `Entity<ID>`）
- 负责维护聚合内业务规则和一致性
- 领域事件通过 `registerDomainEvent()` 注册
- 外部只能通过聚合根操作其内部实体和值对象
- 聚合之间只通过 ID 引用，不持有对象引用

### 5.2 代码模板

```java
package com.example.order.domain.aggregate.root;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import com.example.order.domain.aggregate.entity.OrderItem;
import com.example.order.domain.aggregate.valueobject.Money;
import com.example.order.domain.aggregate.valueobject.OrderStatus;
import com.example.order.domain.errorcode.OrderDomainErrorCode;
import com.example.order.domain.event.OrderPlacedEvent;
import com.example.order.types.id.OrderId;
import com.example.order.types.id.ProductId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Order extends AggregateRoot<OrderId> {

    private List<OrderItem> items;
    private OrderStatus status;
    private Money totalAmount;

    // 业务创建
    public Order(OrderId id, UserNo createdBy) {
        super(id, createdBy);
        this.items = new ArrayList<>();
        this.status = OrderStatus.PENDING;
        this.totalAmount = new Money(BigDecimal.ZERO, "CNY");
        this.validateInvariants();
    }

    // 从数据库重建
    public Order(OrderId id, UserNo createdBy, UserNo updatedBy,
                 LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
                 List<OrderItem> items, OrderStatus status, Money totalAmount) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void addItem(ProductId productId, Integer quantity, Money price, UserNo operator) {
        if (status != OrderStatus.PENDING) {
            throw new DomainException(OrderDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("非草稿状态不能添加商品");
        }
        OrderItem item = new OrderItem(productId, operator, quantity, price, this.id());
        this.items.add(item);
        this.totalAmount = this.totalAmount.add(item.getTotal());
        this.markUpdated(operator);
    }

    public void place(UserNo operator) {
        if (status != OrderStatus.PENDING) {
            throw new DomainException(OrderDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("订单状态不是待提交");
        }
        if (items.isEmpty()) {
            throw new DomainException(OrderDomainErrorCode.INVALID_DATA)
                .withLogDetail("订单不能为空");
        }
        this.status = OrderStatus.PLACED;
        this.markUpdated(operator);
        this.registerDomainEvent(OrderPlacedEvent.of(this.id()));
    }

    @Override
    protected void validateInvariants() {
        if (items == null) {
            throw new DomainException(OrderDomainErrorCode.INVALID_DATA)
                .withLogDetail("订单项不能为空");
        }
    }

    // getters
    public List<OrderItem> items() { return List.copyOf(items); }
    public OrderStatus status() { return status; }
    public Money totalAmount() { return totalAmount; }
}
```

---

## 六、领域事件（Domain Event）

### 6.1 设计原则
- 实现 `DomainEvent` 接口，使用 record 类型
- 过去时态命名（OrderPlacedEvent）
- 必须提供 static `of()` 工厂方法
- `of()` 方法内部通过 `EventId.generate()` 和 `LocalDateTime.now()` 设置事件ID和时间

### 6.2 代码模板

```java
package com.example.order.domain.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.order.types.id.OrderId;

import java.time.LocalDateTime;

public record OrderPlacedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    OrderId orderId
) implements DomainEvent {

    public static OrderPlacedEvent of(OrderId orderId) {
        return new OrderPlacedEvent(
            EventId.generate(),
            LocalDateTime.now(),
            orderId
        );
    }
}
```

### 6.3 领域事件发布流程
1. 聚合根内部注册事件：`registerDomainEvent(event)`
2. Repository.save() 保存聚合根后发布事件：`eventBus.publish(event)`
3. 发布后清理事件：`clearDomainEvents()`

---

## 七、领域服务（Domain Service）

### 7.1 设计原则
- 标注 `@DomainService`（不需要额外标注 `@Component`，由基础设施层配置扫描注册）
- 无状态，只包含领域业务逻辑
- 禁止依赖外部框架，禁止直接访问数据库或外部服务
- 方法返回领域对象而非DTO
- 当业务行为不属于任何实体/值对象，或需要协调多个聚合时使用

### 7.2 代码模板

```java
package com.example.order.domain.service;

import com.example.shared.annotation.DomainService;
import com.example.shared.exception.DomainException;
import com.example.order.domain.aggregate.root.Order;
import com.example.order.domain.errorcode.OrderDomainErrorCode;

@DomainService
public class PaymentService {

    public void pay(Order order, Account account) {
        if (account.getBalance().compareTo(order.getTotalAmount()) < 0) {
            throw new DomainException(OrderDomainErrorCode.INSUFFICIENT_FUNDS)
                .withLogDetail("余额不足");
        }
        account.withdraw(order.getTotalAmount());
        order.pay();
    }
}
```

### 7.3 领域服务与应用服务的区别

| 特性 | 领域服务 | 应用服务 |
|------|----------|----------|
| 层级 | Domain层 | Application层 |
| 职责 | 封装领域业务逻辑 | 编排业务流程 |
| 状态 | 无状态 | 可持有状态 |
| 依赖 | 仅依赖领域模型 | 依赖领域服务、Repository |
| 事务 | 不管理事务 | 管理事务边界 |

---

## 八、错误码定义（ErrorCode）

### 8.1 设计原则
- 每个服务的domain层定义自己的错误码枚举
- 实现 `ErrorDefinition` 接口
- 放在 `domain.errorcode` 包下

### 8.2 代码模板

```java
package com.example.order.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

public enum OrderDomainErrorCode implements ErrorDefinition {

    INVALID_OPERATION("ORDER-001", "无效操作"),
    INVALID_DATA("ORDER-002", "无效数据"),
    ITEM_NOT_FOUND("ORDER-003", "商品不存在"),
    INSUFFICIENT_FUNDS("ORDER-004", "余额不足");

    private final String code;
    private final String message;

    OrderDomainErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
```

---

## 九、Repository接口

### 9.1 设计原则
- 继承 `Repository<T, ID>`
- 方法命名体现业务语义（load、save、findByXxx）
- 入参和返回值使用领域原语和领域对象
- 定义在 `domain.repository` 包下

### 9.2 代码模板

```java
package com.example.order.domain.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.repository.Repository;
import com.example.order.domain.aggregate.root.Order;
import com.example.order.types.id.OrderId;

import java.util.Optional;

public interface OrderRepository extends Repository<Order, OrderId> {

    Optional<Order> findByOrderNo(String orderNo);
}
```

---

## 十、防腐层网关（Gateway）

### 10.1 设计原则
- 定义在 `domain.gateway` 包下
- 只定义接口，实现在Infrastructure层
- 将外部系统模型转换为内部领域模型

### 10.2 代码模板

```java
package com.example.order.domain.gateway;

public interface ExternalPaymentGateway {
    PaymentResult processPayment(PaymentRequest request);
}
```

---

## 十一、战术设计检查清单

- [ ] 是否识别了所有核心聚合根？
- [ ] 聚合根是否定义了清晰的一致性边界？
- [ ] 是否使用领域原语代替了基础类型？
- [ ] 值对象是否不可变且无ID？
- [ ] 实体是否包含业务行为而非单纯数据容器？
- [ ] 领域服务是否确实不属于任何实体/值对象？
- [ ] 领域事件是否以过去时态命名？
- [ ] 聚合之间是否只通过ID引用？
- [ ] 是否定义了完整的状态枚举？
- [ ] 是否定义了错误码枚举？
- [ ] domain层分包是否遵循 `aggregate.root/entity/valueobject` 结构？
- [ ] 是否需要防腐层网关接口？