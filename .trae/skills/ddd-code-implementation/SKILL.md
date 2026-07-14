---
name: "ddd-code-implementation"
description: "DDD代码实现技能：指导代码实现阶段的各种模式和规范，包括API层接口定义、Adapter层实现、Repository实现、Data Converter、CQE模式、MapStruct转换器。当用户需要编写实现代码时调用。"
---

# DDD代码实现：实现模式与代码模板

## 一、API层接口定义

### 1.1 设计原则
- API接口定义在 `xxx-api` 模块中
- 使用 `@HttpExchange` 注解标记接口，方法使用 `@GetExchange`/`@PostExchange`
- 只定义协议，不包含实现逻辑
- 请求体DTO命名：XXXRequest，返回体DTO命名：XXXResponse
- 返回体统一使用 `ApiResult<T>` 包装
- 请求体使用 `@RequestBody` + `@Valid` 标记
- 不得使用GET和POST之外的其他请求类型

### 1.2 API接口代码模板

```java
package com.example.order.api;

import com.example.order.api.dto.OrderCreateRequest;
import com.example.order.api.dto.OrderResponse;
import com.example.order.api.dto.OrderQueryRequest;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/orders")
public interface OrderApi {

    @PostExchange
    ApiResult<OrderResponse> create(@RequestBody @Valid OrderCreateRequest request);

    @GetExchange("/{orderId}")
    ApiResult<OrderResponse> getById(@PathVariable("orderId") String orderId);

    @PostExchange("/query")
    ApiResult<PageData<OrderResponse>> query(@RequestBody @Valid OrderQueryRequest request);
}
```

### 1.3 Request DTO模板

```java
package com.example.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OrderCreateRequest(
    @NotBlank String userId,
    @NotNull Integer quantity,
    @NotBlank String productId,
    String remark
) {}
```

### 1.4 Response DTO模板

```java
package com.example.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
    String orderId,
    String status,
    BigDecimal totalAmount,
    String currency,
    LocalDateTime createdAt
) {}
```

### 1.5 API层命名规范

| 组件 | 命名模式 | 示例 |
|------|----------|------|
| API接口 | 业务名称 + Api | OrderApi |
| 请求体DTO | 业务名称 + Request | OrderCreateRequest |
| 返回体DTO | 业务名称 + Response | OrderResponse |
| 查询请求DTO | 业务名称 + QueryRequest | OrderQueryRequest |

---

## 二、Adapter层实现

### 2.1 设计原则
- 实现 API 层定义的接口，标注 `@RestController`
- 通过构造函数注入依赖（应用服务、转换器）
- 请求体DTO → 领域Command/DTO：通过MapStruct Converter完成
- 领域DTO → 返回体DTO：通过MapStruct Converter完成
- 禁止在Adapter中编写业务逻辑、禁止直接操作Entity或DO
- 禁止在Adapter中直接编写DTO转换代码

### 2.2 Adapter代码模板

```java
package com.example.order.adapter.controller;

import com.example.order.adapter.converter.OrderConverter;
import com.example.order.api.OrderApi;
import com.example.order.api.dto.OrderCreateRequest;
import com.example.order.api.dto.OrderResponse;
import com.example.order.api.dto.OrderQueryRequest;
import com.example.order.api.command.CreateOrderCommand;
import com.example.order.api.query.OrderQuery;
import com.example.order.api.dto.OrderDTO;
import com.example.order.application.service.OrderAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderAppService orderAppService;
    private final OrderConverter orderConverter;

    @Override
    public ApiResult<OrderResponse> create(OrderCreateRequest request) {
        // 1. 请求体DTO → 领域Command（通过Converter）
        CreateOrderCommand command = orderConverter.toCommand(request);

        // 2. 调用应用层服务
        OrderDTO orderDTO = orderAppService.create(command);

        // 3. 领域DTO → 返回体DTO（通过Converter）
        OrderResponse response = orderConverter.toResponse(orderDTO);

        return ApiResult.success(response);
    }

    @Override
    public ApiResult<OrderResponse> getById(String orderId) {
        OrderDTO orderDTO = orderAppService.getById(orderId);
        OrderResponse response = orderConverter.toResponse(orderDTO);
        return ApiResult.success(response);
    }

    @Override
    public ApiResult<PageData<OrderResponse>> query(OrderQueryRequest request) {
        OrderQuery query = orderConverter.toQuery(request);
        PageData<OrderDTO> pageData = orderAppService.query(query);
        PageData<OrderResponse> responsePage = orderConverter.toResponsePage(pageData);
        return ApiResult.success(responsePage);
    }
}
```

### 2.3 Adapter层职责

| 职责 | 说明 |
|------|------|
| 实现API接口 | 实现API层定义的接口协议 |
| DTO转换 | 请求体DTO ↔ 领域DTO的转换（通过Converter） |
| 调用应用服务 | 委托应用层处理业务逻辑 |
| 响应包装 | 返回统一的ApiResult格式 |

---

## 三、MapStruct转换器

### 3.1 Adapter层DTO转换（请求体DTO ↔ 领域DTO）

```java
package com.example.order.adapter.converter;

import com.example.order.api.command.CreateOrderCommand;
import com.example.order.api.dto.OrderCreateRequest;
import com.example.order.api.dto.OrderResponse;
import com.example.order.api.dto.OrderQueryRequest;
import com.example.order.api.dto.OrderDTO;
import com.example.order.api.query.OrderQuery;
import com.example.shared.web.core.dto.PageData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderConverter {

    CreateOrderCommand toCommand(OrderCreateRequest request);

    OrderQuery toQuery(OrderQueryRequest request);

    OrderResponse toResponse(OrderDTO orderDTO);

    default PageData<OrderResponse> toResponsePage(PageData<OrderDTO> pageData) {
        return new PageData<>(
            pageData.records().stream().map(this::toResponse).toList(),
            pageData.total(),
            pageData.page(),
            pageData.size()
        );
    }
}
```

### 3.2 基础设施层Entity转换（Entity ↔ DO）

```java
package com.example.order.infrastructure.converter;

import com.example.order.domain.aggregate.root.Order;
import com.example.order.infrastructure.entity.OrderDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OrderEntityConverter {

    @Mapping(source = "id.value", target = "id")
    @Mapping(source = "totalAmount.amount", target = "totalAmount")
    @Mapping(source = "totalAmount.currency", target = "currency")
    OrderDO toDO(Order entity);

    @Mapping(target = "totalAmount", expression = "java(new Money(doObj.getTotalAmount(), doObj.getCurrency()))")
    Order toEntity(OrderDO doObj);
}
```

### 3.3 MapStruct高级用法

**自定义转换方法：**

```java
@Mapper(componentModel = "spring")
public interface OrderConverter {

    @Mapping(target = "amount", source = "amount", qualifiedByName = "toMoney")
    OrderDTO toDomain(OrderCreateRequest request);

    @Named("toMoney")
    default Money toMoney(BigDecimal amount) {
        return new Money(amount, "CNY");
    }
}
```

**更新场景（@MappingTarget）：**

```java
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OrderUpdateConverter {

    void updateOrder(@MappingTarget OrderDTO target, OrderUpdateRequest source);
}
```

**引入通用转换器（uses）：**

```java
@Mapper(componentModel = "spring", uses = {MoneyConverter.class, DateConverter.class})
public interface OrderConverter {
    OrderResponse toResponse(OrderDTO domainDTO);
}
```

### 3.4 MapStruct配置要点

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| componentModel | 生成代码的组件模型 | spring |
| uses | 引入通用转换器 | CommonConverter等 |
| unmappedTargetPolicy | 未映射目标字段策略 | IGNORE |
| nullValuePropertyMappingStrategy | null值映射策略 | IGNORE（更新场景） |

---

## 四、异常体系实现

### 4.1 异常层级与使用场景

| 异常类 | 使用层 | 使用场景 | 示例 |
|--------|--------|----------|------|
| `DomainException` | Domain层 | 领域规则校验失败 | 金额不能为负数、状态不允许此操作 |
| `BusinessException` | Application层 | 业务流程异常 | 审批流未找到、流程已被他人处理 |
| `SystemException` | Infrastructure层 | 系统级错误 | 数据库连接失败、外部服务超时 |

### 4.2 DomainException 使用模板

```java
// Domain层：业务规则校验
if (status != OrderStatus.PENDING) {
    throw new DomainException(OrderDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("非草稿状态不能添加商品");
}
```

### 4.3 BusinessException 使用模板

```java
// Application层：业务流程异常
public ApprovalProcessDTO start(StartApprovalProcessCommand command) {
    ApprovalFlowConfig config = configRepository.findByMatchDimension(...)
        .orElseThrow(() -> new BusinessException(WorkflowErrorCode.CONFIG_NOT_FOUND)
            .withLogDetail("未找到匹配的审批流配置"));
}
```

### 4.4 错误码定义模板

```java
// Domain层错误码
package com.example.order.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

public enum OrderDomainErrorCode implements ErrorDefinition {
    INVALID_OPERATION("ORDER-001", "无效操作"),
    INVALID_DATA("ORDER-002", "无效数据");

    private final String code;
    private final String message;

    OrderDomainErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
```

```java
// Application层错误码
package com.example.order.application.errorcode;

import com.example.shared.exception.ErrorDefinition;

public enum OrderAppErrorCode implements ErrorDefinition {
    CONFIG_NOT_FOUND("ORDER-APP-001", "配置不存在"),
    CONCURRENT_MODIFICATION("ORDER-APP-002", "并发修改冲突");

    private final String code;
    private final String message;

    OrderAppErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
```

---

## 五、CQE模式规范

### 5.1 Command（指令 - 写操作）

```java
package com.example.order.api.command;

import com.example.order.types.id.OrderId;
import com.example.shared.primitives.identity.UserNo;

public record CreateOrderCommand(
    OrderId orderId,
    UserNo userId,
    List<OrderItemInput> items
) {}
```

### 5.2 Query（查询 - 读操作）

```java
package com.example.order.api.query;

import com.example.shared.web.core.dto.PageQuery;

public record OrderQuery(
    String orderNo,
    String userId,
    String status,
    PageQuery pageQuery
) {}
```

### 5.3 Event（应用层事件 - 事件驱动编排）

```java
package com.example.order.api.event;

import com.example.order.types.id.OrderId;

public record OrderCreatedEvent(
    OrderId orderId,
    String userId
) {}
```

---

## 六、应用层服务实现

### 6.1 设计原则
- 标注 `@Service`，管理事务边界（@Transactional）
- 编排业务流程，不包含业务规则
- 入参只能是CQE对象（Command/Query/Event）
- 通过构造函数注入依赖
- 返回DTO对象

### 6.2 代码模板

```java
package com.example.order.application.service;

import com.example.order.api.command.CreateOrderCommand;
import com.example.order.api.dto.OrderDTO;
import com.example.order.api.query.OrderQuery;
import com.example.order.domain.aggregate.root.Order;
import com.example.order.domain.repository.OrderRepository;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderAppService {

    private final OrderRepository orderRepository;
    private final IdService idService;

    @Transactional
    public OrderDTO create(CreateOrderCommand command) {
        OrderId orderId = new OrderId(idService.generate());
        Order order = new Order(orderId, command.userId());
        // ... 业务编排
        orderRepository.save(order);
        return OrderDTOAssembler.toDTO(order);
    }

    @Transactional(readOnly = true)
    public OrderDTO getById(String orderId) {
        Order order = orderRepository.loadOrThrow(new OrderId(orderId));
        return OrderDTOAssembler.toDTO(order);
    }
}
```

### 6.3 DTO组装器（DTOAssembler）

DTOAssembler负责将领域对象（Entity/AggregateRoot）转换为DTO，供Adapter层使用。

```java
package com.example.order.application.assembler;

import com.example.order.api.dto.OrderDTO;
import com.example.order.api.dto.OrderItemDTO;
import com.example.order.domain.aggregate.root.Order;
import com.example.order.domain.aggregate.entity.OrderItem;

public class OrderDTOAssembler {

    public static OrderDTO toDTO(Order order) {
        return new OrderDTO(
            order.id().value(),
            order.status().code(),
            order.totalAmount().amount(),
            order.totalAmount().currency(),
            order.createdAt(),
            order.items().stream().map(OrderDTOAssembler::toItemDTO).toList()
        );
    }

    private static OrderItemDTO toItemDTO(OrderItem item) {
        return new OrderItemDTO(
            item.id().value(),
            item.quantity(),
            item.price().amount(),
            item.price().currency()
        );
    }
}
```

---

## 七、Repository实现

### 7.1 设计原则
- 必须在save方法中发布领域事件
- 使用Data Converter（MapStruct）完成Entity与DO转换
- 使用MyBatis-Flex进行数据库操作
- 禁止在Repository实现中编写业务逻辑

### 7.2 代码模板

```java
package com.example.order.infrastructure.repository;

import com.example.shared.domain.event.EventBus;
import com.example.order.domain.aggregate.root.Order;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.infrastructure.converter.OrderEntityConverter;
import com.example.order.infrastructure.entity.OrderDO;
import com.example.order.infrastructure.mapper.OrderMapper;
import com.example.order.types.id.OrderId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderEntityConverter converter;
    private final EventBus eventBus;

    @Override
    public Optional<Order> load(OrderId id) {
        OrderDO doObj = orderMapper.selectOne(
            QueryWrapper.create()
                .where(OrderDO::getId).eq(id.value())
                .and(OrderDO::getDeleted).eq(0)
        );
        return Optional.ofNullable(converter.toEntity(doObj));
    }

    @Override
    public void save(Order order) {
        OrderDO doObj = converter.toDO(order);

        OrderDO existing = orderMapper.selectById(doObj.getId());
        if (existing == null) {
            orderMapper.insert(doObj);
        } else {
            doObj.setVersion(existing.getVersion());
            orderMapper.update(doObj);
        }

        // 发布领域事件
        order.getDomainEvents().forEach(eventBus::publish);
        order.clearDomainEvents();
    }

    @Override
    public void delete(Order aggregateRoot) {
        OrderDO doObj = converter.toDO(aggregateRoot);
        doObj.setDeleted(true);
        orderMapper.update(doObj);
    }

    @Override
    public void deleteById(OrderId id) {
        load(id).ifPresent(this::delete);
    }
}
```

---

## 八、DO实体模板

DO实体是基础设施层与数据库的映射对象，使用MyBatis-Flex注解。

```java
package com.example.order.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table(value = "t_order")
public class OrderDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String status;
    private BigDecimal totalAmount;
    private String currency;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    private String createdBy;
    private String updatedBy;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
```

---

## 九、Gateway实现模板

Gateway实现位于Infrastructure层，负责调用外部服务并将外部模型转换为内部领域模型。

```java
package com.example.order.infrastructure.gateway;

import com.example.order.domain.gateway.ExternalPaymentGateway;
import com.example.order.domain.aggregate.valueobject.PaymentResult;
import com.example.order.domain.aggregate.valueobject.PaymentRequest;
import com.example.order.infrastructure.client.PaymentClient;
import com.example.order.infrastructure.converter.PaymentConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalPaymentGatewayImpl implements ExternalPaymentGateway {

    private final PaymentClient paymentClient;
    private final PaymentConverter paymentConverter;

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // 1. 内部模型 → 外部请求模型
        PaymentApiRequest apiRequest = paymentConverter.toApiRequest(request);

        // 2. 调用外部服务
        PaymentApiResponse apiResponse = paymentClient.processPayment(apiRequest);

        // 3. 外部响应模型 → 内部模型
        return paymentConverter.toPaymentResult(apiResponse);
    }
}
```

---

## 十、DTO转换流程全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                      HTTP请求                                      │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  请求体DTO (OrderCreateRequest)                              │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  Adapter层 (OrderController implements OrderApi)             │  │
│   │  OrderConverter.toCommand()                                  │  │
│   │  请求体DTO → 领域Command                                     │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  Application层 (OrderAppService)                             │  │
│   │  OrderAppService.create(command)                             │  │
│   │  Command → Entity → Domain Event                             │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  Infrastructure层 (OrderRepositoryImpl)                      │  │
│   │  OrderEntityConverter.toDO()                                 │  │
│   │  Entity → DO → 数据库                                        │  │
│   │  发布领域事件                                                 │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  Application层                                               │  │
│   │  返回领域DTO (OrderDTO)                                      │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  Adapter层                                                   │  │
│   │  OrderConverter.toResponse()                                 │  │
│   │  领域DTO → 返回体DTO                                         │  │
│   └───────────────────┬─────────────────────────────────────────┘  │
│                       ↓                                            │
│   ┌─────────────────────────────────────────────────────────────┐  │
│   │  返回体DTO (OrderResponse)                                   │  │
│   │  ApiResult.success(response)                                 │  │
│   └─────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

| 转换步骤 | 层级 | 转换内容 | 使用工具 |
|----------|------|----------|----------|
| 1 | Adapter | 请求体DTO → 领域Command/DTO | MapStruct Converter |
| 2 | Infrastructure | Entity → DO | MapStruct EntityConverter |
| 3 | Infrastructure | DO → Entity | MapStruct EntityConverter |
| 4 | Adapter | 领域DTO → 返回体DTO | MapStruct Converter |

---

## 十一、反模式与避坑指南

### ❌ 反模式 1：贫血模型
**现象**：实体类只有 Getter/Setter，没有任何业务行为。
**正解**：充血模型，把属于该实体的行为放回实体内部。

### ❌ 反模式 2：大泥球聚合
**现象**：设计了一个极大的聚合根，每次加载都要连表查出海量数据。
**正解**：聚合之间只引用 ID。

### ❌ 反模式 3：基本类型偏执
**现象**：方法参数全是 `String`, `Long`。
**正解**：使用领域原语 `void pay(OrderId orderId, UserId userId, Money amount)`。

### ❌ 反模式 4：基础设施驱动设计
**现象**：领域层充满了 `@Entity`, `@Table` 等注解。
**正解**：领域层纯 POJO/Record，在基础设施层做转换。

### ❌ 反模式 5：跨聚合引用
**现象**：聚合根内部直接持有另一个聚合根的对象引用。
**正解**：聚合之间只通过 ID 引用。

### ❌ 反模式 6：忽略领域事件
**现象**：所有操作都是同步调用，没有使用事件解耦。
**正解**：使用领域事件异步处理非核心流程。

### ❌ 反模式 7：流水账代码
**现象**：Adapter中包含所有逻辑。
**正解**：分层处理，每层只做一件事。

### ❌ 反模式 8：手动编写转换代码
**现象**：在Adapter中手动编写大量DTO转换代码。
**正解**：使用MapStruct自动生成转换代码。

### ❌ 反模式 9：Controller不实现API接口
**现象**：Controller自定义路由，不实现API层定义的接口。
**正解**：Controller必须实现API层的 `@HttpExchange` 接口。

### ❌ 反模式 10：Adapter中直接转换DTO
**现象**：在Controller中直接new DTO或手写转换逻辑。
**正解**：所有DTO转换通过MapStruct Converter完成。

---

## 十二、实现检查清单

- [ ] API接口是否使用 `@HttpExchange` 注解？
- [ ] API接口是否定义了 Request/Response DTO？
- [ ] Adapter是否实现API接口并标注 `@RestController`？
- [ ] DTO转换是否通过MapStruct Converter完成？
- [ ] MapStruct Mapper是否标注 `@Mapper(componentModel = "spring")`？
- [ ] Repository实现是否在save中发布领域事件？
- [ ] ApplicationService是否使用CQE模式？
- [ ] Controller是否返回ApiResult？
- [ ] 是否使用领域原语代替基础类型？
- [ ] 聚合之间是否只通过ID引用？
- [ ] 是否避免了贫血模型？
- [ ] 领域分包是否遵循 `aggregate.root/entity/valueobject` 结构？
- [ ] 异常是否使用了正确的层级（DomainException/BusinessException/SystemException）？
- [ ] 错误码是否使用 `ErrorDefinition` 定义？
- [ ] DTOAssembler是否将领域对象正确转换为DTO？
- [ ] Gateway实现是否将外部模型转换为内部领域模型？
- [ ] DO实体是否包含通用字段（createTime/updateTime/deleted/version）？