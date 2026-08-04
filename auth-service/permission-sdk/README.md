# permission-sdk

业务微服务用来调用 Permission 服务做 **数据级鉴权**的客户端库。零依赖（连 Lombok 都没用）， 不会跟宿主服务自己的技术栈冲突。

## 核心组件

| 类                        | 作用                                                                     |
|---------------------------|--------------------------------------------------------------------------|
| `PermissionClient`        | 核心接口：`checkPermission(accountId, planId, businessCode, actionCode)` |
| `HttpPermissionClient`    | 基于`java.net.http.HttpClient`的默认实现                                 |
| `CachingPermissionClient` | 装饰器，加本地短TTL缓存，减少网络往返                                    |
| `PermissionGuard`         | 不依赖AOP框架的直接调用方式                                              |
| `RequirePermission`       | 框架无关的声明式注解，具体怎么解释它由各服务自己的切面决定               |

## 最简用法（不依赖任何框架）

```java
PermissionClient client = new CachingPermissionClient(
        new HttpPermissionClient(URI.create("http://permission-service"), () -> myServiceToken()),
        Duration.ofSeconds(10));

public void submitContribution(ContributionRequest req) {
    PermissionGuard.require(client, req.accountId(), req.planId(), "CONTRIBUTION", "SUBMIT");
    // ... 正常业务逻辑
}
```

## Spring AOP 集成示例（如果你们的技术栈是Spring）

SDK本身不引入spring-aop依赖，下面这段代码作为示例，需要业务服务自己在项目里引入
`spring-boot-starter-aop`后放进自己的代码库：

```java
@Aspect
@Component
public class RequirePermissionAspect {

    private final PermissionClient permissionClient;

    public RequirePermissionAspect(PermissionClient permissionClient) {
        this.permissionClient = permissionClient;
    }

    @Before("@annotation(requirePermission)")
    public void check(JoinPoint joinPoint, RequirePermission requirePermission) {
        String accountId = CurrentUserContext.accountId(); // 从当前请求上下文取，各服务自己实现
        String planId = extractPlanId(joinPoint);           // 从方法入参里解析，各服务自己实现
        String action = requirePermission.action().isEmpty() ? null : requirePermission.action();

        PermissionGuard.require(permissionClient, accountId, planId, requirePermission.business(), action);
    }

    private String extractPlanId(JoinPoint joinPoint) {
        // 建议：约定请求DTO实现一个PlanIdAware接口，这里统一按接口取，
        // 不要在切面里对每个服务的DTO类型做反射猜测。
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof PlanIdAware aware) {
                return aware.planId();
            }
        }
        throw new IllegalStateException("找不到planId，检查方法入参是否实现了PlanIdAware");
    }
}
```

业务代码这样用：

```java
@RequirePermission(business = "CONTRIBUTION", action = "SUBMIT")
@PostMapping("/contributions")
public ResponseEntity<?> submitContribution(@RequestBody ContributionRequest req) {
    // 方法体不需要出现任何鉴权代码，切面在进入方法前已经做完检查
}
```

## 紧急撤销时的缓存失效

`CachingPermissionClient` 只处理"缓存本地存什么、多久过期"，不负责订阅消息队列。 业务服务需要自己订阅 Permission 服务通过
outbox 广播出来的 `GrantRevoked` / `AccountFrozen`
等事件对应的消息队列 topic，收到后调用：

```java
cachingPermissionClient.invalidate(accountId);
```

这一步不接，紧急撤销就只能靠 TTL 兜底，不算错，但不是"立即生效"。

## 降级策略

`PermissionGuard.require(...)` 在 Permission 服务不可达时统一按拒绝处理（fail-closed），
这个默认值不应该在业务代码里被覆盖成"服务挂了就放行"。如果自己实现`PermissionClient`
或者不用`PermissionGuard`直接调用`checkPermission`，请自己保证遵守同样的降级原则。
