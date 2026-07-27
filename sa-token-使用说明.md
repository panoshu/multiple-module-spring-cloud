# Sa-Token 使用说明文档

> 基于 Sa-Token v1.45.0 官方文档（https://sa-token.cc/doc.html）整理
> 适配项目技术栈：Spring Boot 3.5.x + Spring Cloud 2025.0.x + Spring Cloud Gateway（WebFlux）

---

## 目录

- [一、框架介绍](#一框架介绍)
- [二、快速集成](#二快速集成)
- [三、登录认证](#三登录认证)
- [四、权限认证](#四权限认证)
- [五、踢人下线](#五踢人下线)
- [六、注解式鉴权](#六注解式鉴权)
- [七、路由拦截式鉴权](#七路由拦截式鉴权)
- [八、Session 会话](#八session-会话)
- [九、前后台分离](#九前后台分离)
- [十、Token 风格定制](#十token-风格定制)
- [十一、记住我模式](#十一记住我模式)
- [十二、二级认证](#十二二级认证)
- [十三、模拟他人账号与临时身份切换](#十三模拟他人账号与临时身份切换)
- [十四、同端互斥登录](#十四同端互斥登录)
- [十五、账号封禁](#十五账号封禁)
- [十六、密码加密](#十六密码加密)
- [十七、会话查询](#十七会话查询)
- [十八、Http Basic 认证](#十八http-basic-认证)
- [十九、全局侦听器](#十九全局侦听器)
- [二十、全局过滤器](#二十全局过滤器)
- [二十一、多账号体系认证](#二十一多账号体系认证)
- [二十二、单点登录（SSO）](#二十二单点登录sso)
- [二十三、OAuth2.0 认证](#二十三oauth20-认证)
- [二十四、微服务网关鉴权](#二十四微服务网关鉴权)
- [二十五、分布式会话](#二十五分布式会话)
- [二十六、持久层扩展（Redis 集成）](#二十六持久层扩展redis-集成)
- [二十七、配置项详解](#二十七配置项详解)
- [二十八、API 速查手册](#二十八api-速查手册)
- [二十九、权限数据缓存与动态鉴权](#二十九权限数据缓存与动态鉴权) ← 重点
- [三十、官方文档索引（按需查阅）](#三十官方文档索引按需查阅)

---

## 一、框架介绍

**Sa-Token** 是一个轻量级 Java 权限认证框架，主要解决：**登录认证**、**权限认证**、**单点登录**、**OAuth2.0**、**分布式 Session 会话**、**微服务网关鉴权** 等一系列权限相关问题。

### 1.1 核心优势

| 优势 | 说明 |
|------|------|
| **简单** | 零配置启动，开箱即用，低成本上手 |
| **强大** | 集成几十项权限相关特性，覆盖大部分业务场景 |
| **易用** | API 如丝般顺滑，大量高级特性只需一行代码 |
| **高扩展** | 几乎所有组件都提供扩展接口，90% 以上逻辑可重写 |

### 1.2 功能模块

Sa-Token 目前主要五大功能模块：

1. **登录认证** —— 单端登录、多端登录、同端互斥登录、七天内免登录
2. **权限认证** —— 权限认证、角色认证、会话二级认证
3. **单点登录** —— 内置三种 SSO 模式：同域、跨域、跨 Redis
4. **OAuth2.0** —— 轻松搭建 OAuth2.0 服务，支持 openid 模式
5. **微服务鉴权** —— 适配 Gateway、ShenYu、Zuul 等网关

### 1.3 快速体验

```java
// 会话登录，参数填登录人的账号id
StpUtil.login(10001);

// 校验当前客户端是否已经登录，如果未登录则抛出 NotLoginException 异常
StpUtil.checkLogin();

// 获取当前会话登录的账号id
StpUtil.getLoginId();

// 当前会话注销登录
StpUtil.logout();

// 将账号id为 10077 的会话踢下线
StpUtil.kickout(10077);
```

---

## 二、快速集成

### 2.1 添加依赖

**Spring Boot 3.x 项目**（推荐本项目使用）：

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
    <version>1.45.0</version>
</dependency>
```

**Spring Boot 2.x 项目**：

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot-starter</artifactId>
    <version>1.45.0</version>
</dependency>
```

### 2.2 基础配置（application.yml）

```yaml
############## Sa-Token 配置 (文档: https://sa-token.cc) ##############
sa-token:
  # token 名称（同时也是 Cookie 名称、Header 名称）
  token-name: satoken
  # token 有效期（单位：秒），默认30天，-1 代表永久有效
  timeout: 2592000
  # token 最低活跃频率（单位：秒），超过此时间未访问系统就会冻结，-1 代表不限制
  active-timeout: -1
  # 是否允许同一账号多地同时登录（true=允许一起登录, false=新登录挤掉旧登录）
  is-concurrent: true
  # 在多人登录同一账号时，是否共用一个 token（true=所有登录共用一个 token, false=每次登录新建一个 token）
  is-share: true
  # token 风格（可取值：uuid、simple-uuid、random-32、random-64、random-128、tik）
  token-style: uuid
  # 是否输出操作日志
  is-log: true
```

### 2.3 最简登录示例

```java
@RestController
public class LoginController {

    @PostMapping("/login")
    public SaResult login(@RequestParam String username, @RequestParam String password) {
        // 1. 校验账号密码（实际项目查数据库）
        if (!"admin".equals(username) || !"123456".equals(password)) {
            return SaResult.error("账号或密码错误");
        }
        // 2. 会话登录：参数填登录人的账号id
        StpUtil.login(10001L);
        // 3. 返回 Token 信息
        return SaResult.data(StpUtil.getTokenInfo());
    }

    @GetMapping("/logout")
    public SaResult logout() {
        StpUtil.logout();
        return SaResult.ok("已退出登录");
    }

    @GetMapping("/isLogin")
    public SaResult isLogin() {
        return SaResult.data(StpUtil.isLogin());
    }
}
```

> **说明**：调用 `StpUtil.login(id)` 后，Sa-Token 会自动生成 Token 并通过 Cookie 返回前端。前后台分离场景下，需要手动返回 Token 信息（见第九章）。

---

## 三、登录认证

### 3.1 登录与注销

```java
// 会话登录：参数填登录账号id（建议类型：long | int | String，不可传复杂对象）
StpUtil.login(Object id);

// 当前会话注销登录
StpUtil.logout();

// 获取当前会话是否已登录，返回 true=已登录，false=未登录
StpUtil.isLogin();

// 校验当前会话是否已登录，未登录则抛出 NotLoginException
StpUtil.checkLogin();
```

`StpUtil.login(id)` 实际上做了以下工作：
1. 检查此账号是否已有登录
2. 为账号生成 Token 凭证与 Session 会话
3. 通知全局侦听器
4. 将 Token 注入到请求上下文
5. 通过 Cookie 返回前端（前后台分离需手动返回）

### 3.2 会话查询

```java
// 获取当前会话账号id，未登录则抛出异常 NotLoginException
StpUtil.getLoginId();

// 类型转换版
StpUtil.getLoginIdAsString();    // 转为 String
StpUtil.getLoginIdAsInt();       // 转为 int
StpUtil.getLoginIdAsLong();      // 转为 long

// 未登录时返回默认值
StpUtil.getLoginIdDefaultNull();              // 返回 null
StpUtil.getLoginId(T defaultValue);           // 返回指定默认值
```

### 3.3 Token 查询

```java
// 获取当前会话的 token 值
StpUtil.getTokenValue();

// 获取当前 StpLogic 的 token 名称
StpUtil.getTokenName();

// 获取指定 token 对应的账号id，未登录返回 null
StpUtil.getLoginIdByToken(String tokenValue);

// 获取当前会话剩余有效期（单位：秒，-1 代表永久有效）
StpUtil.getTokenTimeout();

// 获取当前会话的 token 信息参数（包含 tokenName、tokenValue、isLogin）
StpUtil.getTokenInfo();
```

### 3.4 其他登录方式

```java
// 指定设备登录
StpUtil.login(10001, new SaLoginModel().setDevice("PC"));

// 记住我登录（七天内免登录）
StpUtil.login(10001, new SaLoginModel().setTimeout(7 * 24 * 3600).setIsLastingCookie(true));

// 登录时指定 Token 有效期
StpUtil.login(10001, new SaLoginModel().setTimeout(3600));  // 有效期1小时
```

### 3.5 NotLoginException 异常

未登录访问会抛出 `NotLoginException`，可通过 `getType()` 获取具体原因：

```java
NotLoginException.getType() 返回值：
- NotLoginException.NOT_TOKEN         // 未提供 token
- NotLoginException.INVALID_TOKEN     // token 无效
- NotLoginException.TOKEN_TIMEOUT     // token 已过期
- NotLoginException.BE_REPLACED       // token 已被顶下线
- NotLoginException.KICK_OUT          // token 已被踢下线
```

---

## 四、权限认证

### 4.1 实现 StpInterface 接口

权限认证的核心是获取"账号拥有的权限码集合"，Sa-Token 通过 `StpInterface` 接口将此能力暴露给开发者：

```java
/**
 * 自定义权限验证接口扩展
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 返回一个账号所拥有的权限码集合
     *
     * @param loginId   账号id
     * @param loginType 账号体系标识（多账号体系时使用）
     * @return 权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 实际项目中根据 loginId 查数据库返回权限列表
        List<String> list = new ArrayList<>();
        list.add("user.add");
        list.add("user.update");
        list.add("user.get");
        list.add("art.*");           // 支持通配符
        return list;
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     *
     * @param loginId   账号id
     * @param loginType 账号体系标识
     * @return 角色集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> list = new ArrayList<>();
        list.add("admin");
        list.add("super-admin");
        return list;
    }
}
```

> **注意**：`StpInterface` 接口由框架在鉴权时自动调用，只需注册为 Spring Bean 即可。

### 4.2 权限校验 API

```java
// 获取当前账号所拥有的权限集合
StpUtil.getPermissionList();

// 判断当前账号是否含有指定权限，返回 true 或 false
StpUtil.hasPermission("user.add");

// 校验当前账号是否含有指定权限，未通过则抛出 NotPermissionException
StpUtil.checkPermission("user.add");

// 校验权限 [指定多个，必须全部通过]
StpUtil.checkPermissionAnd("user.add", "user.delete", "user.get");

// 校验权限 [指定多个，只要其一通过即可]
StpUtil.checkPermissionOr("user.add", "user.delete", "user.get");
```

### 4.3 角色校验 API

角色和权限可独立验证：

```java
// 获取当前账号所拥有的角色集合
StpUtil.getRoleList();

// 判断当前账号是否拥有指定角色
StpUtil.hasRole("super-admin");

// 校验当前账号是否含有指定角色标识，未通过抛出 NotRoleException
StpUtil.checkRole("super-admin");

// 校验角色 [指定多个，必须全部通过]
StpUtil.checkRoleAnd("super-admin", "shop-admin");

// 校验角色 [指定多个，只要其一通过即可]
StpUtil.checkRoleOr("super-admin", "shop-admin");
```

### 4.4 权限通配符

Sa-Token 支持通配符指定泛权限：

```java
// 当账号拥有 "art.*" 权限时：
StpUtil.hasPermission("art.add");        // true
StpUtil.hasPermission("art.update");     // true
StpUtil.hasPermission("art.delete");     // true

// "*" 代表拥有所有权限
StpUtil.hasPermission("user.add");       // true（当权限集合包含 "*" 时）
```

### 4.5 全局异常拦截

鉴权失败会抛出异常，建议使用全局异常处理器统一返回：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public SaResult handleNotLogin(NotLoginException e) {
        return SaResult.error("未登录：" + e.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public SaResult handleNotPermission(NotPermissionException e) {
        return SaResult.error("无权限：" + e.getMessage());
    }

    @ExceptionHandler(NotRoleException.class)
    public SaResult handleNotRole(NotRoleException e) {
        return SaResult.error("无角色：" + e.getMessage());
    }
}
```

---

## 五、踢人下线

### 5.1 三种"下线"方式的区别

| 方式 | API | 说明 |
|------|-----|------|
| **注销下线** | `StpUtil.logout(loginId)` | 账号会话注销，对方再次访问提示未登录 |
| **强制下线**（踢人） | `StpUtil.kickout(loginId)` | 账号被踢下线，对方再次访问提示"已被踢下线" |
| **顶人下线** | `StpUtil.replaced(loginId)` | 账号被新登录顶下线，对方再次访问提示"已被顶下线" |

### 5.2 API 示例

```java
// 根据账号id踢人下线
StpUtil.kickout(10077);

// 根据账号id强制注销
StpUtil.logoutByLoginId(10077);

// 根据 Token 值踢人下线
StpUtil.kickoutByTokenValue("xxxx-xxxx-xxxx");

// 根据 Token 值注销
StpUtil.logoutByTokenValue("xxxx-xxxx-xxxx");
```

### 5.3 区别说明

- `logout`：会话被注销，对方再次访问提示 "未登录"
- `kickout`：会话标记为被踢，对方再次访问提示 "已被踢下线"
- `replaced`：会话标记为被顶，对方再次访问提示 "已被顶下线"

业务可根据不同提示信息决定下一步处理逻辑。

---

## 六、注解式鉴权

### 6.1 可用注解

| 注解 | 说明 |
|------|------|
| `@SaCheckLogin` | 校验当前会话是否已登录 |
| `@SaCheckRole("admin")` | 校验当前会话是否含有指定角色 |
| `@SaCheckPermission("user:add")` | 校验当前会话是否含有指定权限 |
| `@SaCheckSafe` | 校验当前会话是否已完成二级认证 |
| `@SaCheckDisable` | 校验当前会话是否已被服务禁用 |
| `@SaIgnore` | 声明此方法忽略鉴权 |

### 6.2 使用示例

```java
// 校验登录
@SaCheckLogin
@GetMapping("/user/info")
public UserInfo getUserInfo() { ... }

// 校验角色，必须同时具有 admin 和 super-admin 角色
@SaCheckRole(value = {"admin", "super-admin"}, mode = SaMode.AND)
@GetMapping("/admin/setting")
public String adminSetting() { ... }

// 校验权限，具有 user:add 或 user:update 之一即可
@SaCheckPermission(value = {"user:add", "user:update"}, mode = SaMode.OR)
@PostMapping("/user/save")
public String saveUser() { ... }

// 二级认证校验
@SaCheckSafe
@PostMapping("/user/delete")
public String deleteUser() { ... }

// 忽略鉴权
@SaIgnore
@GetMapping("/public/info")
public String publicInfo() { ... }
```

### 6.3 启用注解鉴权

在 Spring Boot 3 中，注解鉴权需要注册拦截器才能生效：

```java
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解鉴权功能
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
```

> **说明**：`SaInterceptor` 不传参数时只校验注解，不做路由拦截。

---

## 七、路由拦截式鉴权

### 7.1 基础用法

```java
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 根据路由划分模块，不同模块不同鉴权
            SaRouter.match("/user/**",    r -> StpUtil.checkPermission("user"));
            SaRouter.match("/admin/**",   r -> StpUtil.checkPermission("admin"));
            SaRouter.match("/goods/**",   r -> StpUtil.checkPermission("goods"));
            SaRouter.match("/orders/**",  r -> StpUtil.checkPermission("orders"));

            // 登录校验：指定路径需要登录
            SaRouter.match("/api/**", r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}
```

### 7.2 路由匹配 API

```java
// 匹配指定路径
SaRouter.match("/user/**");

// 匹配多个路径
SaRouter.match("/user/**", "/admin/**");

// 匹配 restful 风格路由
SaRouter.match("/user/{id}");

// 匹配正则表达式
SaRouter.match(Pattern.compile("^/api/.*"));

// 前端分离：排除特定路径
SaRouter.match("/**").notMatch("/login", "/register").check(r -> StpUtil.checkLogin());

// 自定义校验
SaRouter.match("/**", r -> {
    StpUtil.checkLogin();
    StpUtil.checkPermission("user");
});
```

### 7.3 常用拦截规则

```java
registry.addInterceptor(new SaInterceptor(handler -> {
    // 1. 全局登录校验（排除登录、注册等公开接口）
    SaRouter.match("/**")
        .notMatch("/login", "/register", "/public/**")
        .check(r -> StpUtil.checkLogin());

    // 2. 角色校验
    SaRouter.match("/admin/**", r -> StpUtil.checkRole("admin"));

    // 3. 权限校验
    SaRouter.match("/user/**", r -> StpUtil.checkPermission("user"));
})).addPathPatterns("/**");
```

---

## 八、Session 会话

Sa-Token 提供三种 Session 模型：

### 8.1 Account-Session（账号会话）

同一个账号共享的 Session，不同设备登录同一账号时共享数据：

```java
// 获取当前会话的 Account-Session
SaSession session = StpUtil.getSession();

// 存取值
session.set("name", "张三");
session.set("age", 25);

// 取值
String name = session.getString("name");
int age = session.getInt("age");

// 其他操作
session.has("name");              // 是否包含某个key
session.delete("name");           // 删除某个key
session.getDataMap();             // 获取所有数据
```

### 8.2 Token-Session（令牌会话）

每个 Token 独有的 Session，不同设备登录同一账号时数据独立：

```java
// 获取当前会话的 Token-Session
SaSession session = StpUtil.getTokenSession();

// 操作方式与 Account-Session 相同
session.set("device", "PC");
String device = session.getString("device");
```

### 8.3 自定义 Session

通过 SessionId 自定义会话：

```java
// 获取指定 id 的 Session
SaSession session = StpUtil.getSessionBySessionId("custom-session-id");

// 操作方式相同
session.set("key", "value");
```

### 8.4 Session 的应用场景

| 场景 | 适用 Session | 说明 |
|------|-------------|------|
| 用户基本信息缓存 | Account-Session | 同一账号多设备共享 |
| 设备信息存储 | Token-Session | 每次登录独立 |
| 临时数据存储 | Token-Session | 随 Token 生命周期 |
| 跨设备同步数据 | Account-Session | 修改一处处处生效 |

---

## 九、前后台分离

APP、小程序等不支持 Cookie 的终端，需要通过 Header 传递 Token。

### 9.1 后端配置

```yaml
sa-token:
  token-name: satoken
  # 是否从 Header 中读取 Token
  is-read-header: true
  # 是否从 Cookie 中读取 Token（前后台分离设为 false）
  is-read-cookie: false
  timeout: 2592000
```

### 9.2 后端登录接口

```java
@PostMapping("/login")
public SaResult login(@RequestBody LoginDTO dto) {
    Long userId = userService.checkLogin(dto.getUsername(), dto.getPassword());
    StpUtil.login(userId);

    // 返回 tokenName 和 tokenValue，前端存储
    SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
    return SaResult.data(tokenInfo);
}
```

### 9.3 前端请求拦截器（以 axios 为例）

```javascript
// 登录后存储 Token
const res = await axios.post('/login', { username: 'admin', password: '123456' });
const { tokenName, tokenValue } = res.data.data;
localStorage.setItem('tokenName', tokenName);
localStorage.setItem('tokenValue', tokenValue);

// 统一请求拦截器：每次自动带 Token
axios.interceptors.request.use((config) => {
    const tokenName = localStorage.getItem('tokenName') || 'satoken';
    const tokenValue = localStorage.getItem('tokenValue');
    if (tokenValue) {
        config.headers[tokenName] = tokenValue;
    }
    return config;
});
```

### 9.4 常见问题排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 登录成功但后续接口 401 | 前端只存了 tokenValue 没存 tokenName | 同时存储 tokenName 和 tokenValue |
| Postman 正常浏览器报跨域 | CORS 配置未放行自定义请求头 | 在跨域配置中放行 `satoken` 请求头 |
| 走网关后丢登录态 | 网关安全策略过滤了 satoken 头 | 把 Token 请求头加入网关放行列表 |

---

## 十、Token 风格定制

### 10.1 内置六种风格

| 风格 | 示例 | 说明 |
|------|------|------|
| `uuid` | `623368f0-ae24-4f20-9b3a-60c2d9e2d8c8` | 36位 UUID（默认） |
| `simple-uuid` | `623368f0ae244f209b3a60c2d9e2d8c8` | 32位 UUID（无中划线） |
| `random-32` | `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6` | 32位随机字符串 |
| `random-64` | 64位随机字符串 | 更高安全性 |
| `random-128` | 128位随机字符串 | 最高安全性 |
| `tik` | `gr_SScIN5PnXxLrvxdBk2VZm1oYv8YxJ` | 对内系统推荐的风格 |

### 10.2 配置方式

```yaml
sa-token:
  token-style: tik
```

### 10.3 自定义 Token 生成策略

```java
@Component
public class MySaTokenDao implements SaTokenDao {

    @Override
    public String createToken(Object loginId, String loginType, SaLoginModel loginModel) {
        // 自定义 Token 生成逻辑
        return "my-prefix-" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

## 十一、记住我模式

### 11.1 临时 Token（关闭浏览器即失效）

```java
// isLastingCookie=false：临时 Cookie，浏览器关闭即失效
StpUtil.login(10001, new SaLoginModel().setIsLastingCookie(false));
```

### 11.2 指定有效期

```java
// 7天内免登录
StpUtil.login(10001, new SaLoginModel()
    .setTimeout(7 * 24 * 3600)    // Token 有效期 7 天
    .setIsLastingCookie(true));    // 持久化 Cookie
```

### 11.3 临时有效期 vs 永久有效期

| 参数 | 说明 |
|------|------|
| `timeout` | Token 的最大有效期 |
| `active-timeout` | Token 的活跃超时时间（超过此时间未访问即冻结） |

```yaml
sa-token:
  timeout: 2592000        # 30天有效期
  active-timeout: 1800    # 30分钟未访问即冻结
```

---

## 十二、二级认证

在已登录的基础上再次认证，用于敏感操作（如资金转账、删除重要数据）。

### 12.1 开启二级认证

```java
// 在需要二级认证的接口前调用
StpUtil.openSafe(120);  // 开启二级认证，有效期120秒

// 校验是否处于二级认证
StpUtil.isSafe();

// 强制校验二级认证（未通过抛出 NotSafeException）
StpUtil.checkSafe();

// 关闭二级认证
StpUtil.closeSafe();
```

### 12.2 完整示例

```java
@RestController
public class PayController {

    // 第一步：验证码校验，开启二级认证
    @PostMapping("/pay/verifyCode")
    public SaResult verifyCode(String code) {
        if (!"123456".equals(code)) {
            return SaResult.error("验证码错误");
        }
        // 校验通过，开启二级认证，有效期120秒
        StpUtil.openSafe(120);
        return SaResult.ok("验证成功，请在120秒内完成支付");
    }

    // 第二步：执行支付（需要二级认证）
    @SaCheckSafe   // 注解校验二级认证
    @PostMapping("/pay/doPay")
    public SaResult doPay(Double amount) {
        // 执行支付逻辑
        return SaResult.ok("支付成功");
    }
}
```

---

## 十三、模拟他人账号与临时身份切换

### 13.1 模拟他人账号

```java
// 模拟登录账号 10001（不会改变当前会话登录状态）
StpUtil.login(10001, new SaLoginModel().setIsConcurrent(false));
```

### 13.2 临时身份切换

```java
// 切换身份为 10002
StpUtil.switchTo(10002, () -> {
    // 此代码块内，StpUtil.getLoginId() 返回 10002
    System.out.println("当前身份：" + StpUtil.getLoginId());
});
// 代码块执行完毕，身份自动恢复
```

---

## 十四、同端互斥登录

像 QQ 一样：手机电脑同时在线，但两个手机互斥登录。

### 14.1 配置

```yaml
sa-token:
  is-concurrent: false   # 不允许同账号多端同时登录
  is-share: false        # 每次登录新建 Token
```

### 14.2 按设备互斥

```java
// PC 登录
StpUtil.login(10001, new SaLoginModel().setDevice("PC"));

// APP 登录（会顶掉之前的 APP 登录，不影响 PC）
StpUtil.login(10001, new SaLoginModel().setDevice("APP"));
```

通过 `SaLoginModel.setDevice()` 指定设备类型，相同设备的登录互斥，不同设备可共存。

---

## 十五、账号封禁

### 15.1 基础封禁

```java
// 封禁账号 10001，3天后自动解封
StpUtil.disable(10001, 3 * 24 * 3600);

// 永久封禁
StpUtil.disable(10001, SaTokenDao.NEVER_EXPIRE);

// 解封
StpUtil.untieDisable(10001);

// 查询是否已被封禁
boolean isDisabled = StpUtil.isDisabled(10001);

// 查询剩余封禁时间（秒）
long time = StpUtil.getDisableTime(10001);
```

### 15.2 分类封禁

```java
// 按"业务分类"封禁：封禁评论功能 3 天
StpUtil.disable(10001, "comment", 3 * 24 * 3600);

// 校验是否在指定分类下被封禁
StpUtil.checkDisable(10001, "comment");

// 解封指定分类
StpUtil.untieDisable(10001, "comment");
```

### 15.3 阶梯封禁

```java
// 第一次违规：封禁 1 天
// 第二次违规：封禁 7 天
// 第三次违规：永久封禁
StpUtil.disable(10001, "comment", level -> switch (level) {
    case 1 -> 1 * 24 * 3600;
    case 2 -> 7 * 24 * 3600;
    default -> SaTokenDao.NEVER_EXPIRE;
});
```

---

## 十六、密码加密

Sa-Token 提供基础加密算法模块：

### 16.1 MD5 加密

```java
// MD5
SaSecureUtil.md5("123456");

// MD5 加盐
SaSecureUtil.md5("123456" + "salt");

// SHA1
SaSecureUtil.sha1("123456");

// SHA256
SaSecureUtil.sha256("123456");
```

### 16.2 AES 对称加密

```java
// 加密
String ciphertext = SaSecureUtil.aesEncrypt("key", "明文");

// 解密
String plaintext = SaSecureUtil.aesDecrypt("key", ciphertext);
```

### 16.3 RSA 非对称加密

```java
// 生成密钥对
SaKeyPair keyPair = SaSecureUtil.rsaGenerateKeyPair();
String publicKey = keyPair.getPublicKey();
String privateKey = keyPair.getPrivateKey();

// 公钥加密
String ciphertext = SaSecureUtil.rsaEncrypt(publicKey, "明文");

// 私钥解密
String plaintext = SaSecureUtil.rsaDecrypt(privateKey, ciphertext);
```

> **建议**：密码存储推荐使用 BCrypt，比 MD5 + Salt 更安全。本模块适合轻量加密场景。

---

## 十七、会话查询

```java
// 查询所有已登录的 Token
List<String> tokenList = StpUtil.searchTokenValue("", 0, 10, false);

// 查询包含指定关键字的 Token
List<String> tokenList = StpUtil.searchTokenValue("abc", 0, 10, true);

// 查询某个账号的所有登录 Token
List<String> tokenList = StpUtil.getTokenValueListByLoginId(10001);

// 查询所有已登录的账号id
List<String> loginIdList = StpUtil.searchSessionId("", 0, 10, false);

// 查询所有 Token-Session
List<String> sessionList = StpUtil.searchTokenSessionId("", 0, 10, false);
```

> **注意**：会话查询性能取决于底层存储。内存存储仅适合开发，生产环境建议使用 Redis。

---

## 十八、Http Basic 认证

```java
// 一行代码接入 Http Basic 认证
SaManager.getSaTokenDao().set("sa-token:http-basic:account", "admin:123456");

// 在接口中校验
@GetMapping("/basic")
public String basic() {
    SaBasicUtil.check("admin:123456");
    return "Basic 认证通过";
}
```

---

## 十九、全局侦听器

在用户登录、注销、被踢下线等关键操作时进行 AOP 处理。

### 19.1 实现 SaTokenListener 接口

```java
@Component
public class MySaTokenListener implements SaTokenListener {

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginModel loginModel) {
        System.out.println("用户登录：" + loginId + "，Token：" + tokenValue);
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        System.out.println("用户注销：" + loginId);
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        System.out.println("用户被踢下线：" + loginId);
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        System.out.println("用户被顶下线：" + loginId);
    }

    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
        System.out.println("用户被封禁：" + loginId);
    }

    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
        System.out.println("用户被解封：" + loginId);
    }

    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
        System.out.println("开启二级认证：" + tokenValue);
    }

    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
        System.out.println("关闭二级认证：" + tokenValue);
    }

    @Override
    public void doCreateSession(String id) {
        System.out.println("Session 创建：" + id);
    }

    @Override
    public void doLogoutSession(String id) {
        System.out.println("Session 注销：" + id);
    }

    @Override
    public void doRenewTimeout(String tokenValue, Object loginId, long timeout) {
        System.out.println("Token 续签：" + tokenValue);
    }
}
```

---

## 二十、全局过滤器

用于处理跨域、全局安全响应头等。

### 20.1 Servlet 环境（Spring MVC）

```java
@Configuration
public class SaTokenFilterConfigure {

    @Bean
    public SaServletFilter saServletFilter() {
        return new SaServletFilter()
            .addInclude("/**")
            .addExclude("/login", "/register", "/public/**")
            .setAuth(obj -> {
                StpUtil.checkLogin();
            })
            .setError(e -> SaResult.error(e.getMessage()));
    }
}
```

### 20.2 WebFlux 环境（Spring Cloud Gateway）

```java
@Configuration
public class SaTokenFilterConfigure {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude("/login", "/favicon.ico")
            .setAuth(obj -> {
                StpUtil.checkLogin();
            })
            .setError(e -> SaResult.error(e.getMessage()));
    }
}
```

> **说明**：本项目网关使用 WebFlux，应使用 `SaReactorFilter`。

---

## 二十一、多账号体系认证

一个系统多套账号分开鉴权（如商城的 User 表和 Admin 表）。

### 21.1 创建多账号体系

```java
// 用户体系
public class StpUserUtil {
    public static final String TYPE = "user";
    public static StpLogic stpLogic = new StpLogic(TYPE);

    public static void login(Object id) { stpLogic.login(id); }
    public static Object getLoginId() { return stpLogic.getLoginId(); }
    public static void logout() { stpLogic.logout(); }
    // ... 其他方法委托给 stpLogic
}

// 管理员体系
public class StpAdminUtil {
    public static final String TYPE = "admin";
    public static StpLogic stpLogic = new StpLogic(TYPE);

    public static void login(Object id) { stpLogic.login(id); }
    public static Object getLoginId() { return stpLogic.getLoginId(); }
    public static void logout() { stpLogic.logout(); }
}
```

### 21.2 使用

```java
// 用户登录
StpUserUtil.login(10001);
StpUserUtil.checkLogin();

// 管理员登录（与用户体系互不干扰）
StpAdminUtil.login(1);
StpAdminUtil.checkLogin();
```

---

## 二十二、单点登录（SSO）

Sa-Token SSO 提供三种模式：

| 模式 | 适用场景 | 说明 |
|------|----------|------|
| 模式一 | 前端同域 + 后端同 Redis | 共享 Cookie 同步会话 |
| 模式二 | 前端不同域 + 后端同 Redis | URL 重定向传播会话 |
| 模式三 | 前端不同域 + 后端不同 Redis | Http 请求获取会话 |

### 22.1 添加依赖

```xml
<!-- SSO 认证中心端 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-sso</artifactId>
    <version>1.45.0</version>
</dependency>

<!-- SSO 客户端端 -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-sso</artifactId>
    <version>1.45.0</version>
</dependency>
```

### 22.2 模式二（最常用）示例

**SSO 认证中心**：

```java
@RestController
public class SsoServerController {

    // 登录接口
    @PostMapping("/sso/login")
    public SaResult login(String username, String password) {
        // 校验账号密码
        if (!check(username, password)) {
            return SaResult.error("登录失败");
        }
        StpUtil.login(10001);
        return SaResult.data(StpUtil.getTokenValue());
    }

    // 校验 Ticket
    @GetMapping("/sso/checkTicket")
    public SaResult checkTicket(String ticket) {
        // 校验 ticket，返回登录信息
        return SaResult.data(SsoUtil.checkTicket(ticket));
    }
}
```

**SSO 客户端**：

```java
@RestController
public class SsoClientController {

    @GetMapping("/sso/login")
    public SaResult login(String back, String ticket) {
        // 1. 如果未携带 ticket，重定向到认证中心
        if (ticket == null) {
            return SaResult.data(SsoUtil.getRedirectUrl(back));
        }
        // 2. 校验 ticket，完成登录
        Object loginId = SsoUtil.checkTicket(ticket);
        StpUtil.login(loginId);
        return SaResult.ok("登录成功");
    }
}
```

---

## 二十三、OAuth2.0 认证

### 23.1 添加依赖

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-oauth2</artifactId>
    <version>1.45.0</version>
</dependency>
```

### 23.2 配置 OAuth2 客户端

```java
@Configuration
public class OAuth2Config {

    @PostConstruct
    public void init() {
        // 配置 OAuth2 客户端信息
        OAuth2Manager.setClientConfig(new ClientConfig()
            .setClientId("client-1")
            .setClientSecret("123456")
            .setAllowUrl("*")
            .setContractScopes("userinfo"));
    }
}
```

### 23.3 授权码模式核心端点

```java
@RestController
public class OAuth2Controller {

    // 1. 授权码获取端点
    @GetMapping("/oauth2/authorize")
    public Object authorize(String client_id, String redirect_uri, String response_type, String state) {
        StpUtil.checkLogin();   // 必须已登录
        return OAuth2Manager.authorize(client_id, redirect_uri, response_type, state);
    }

    // 2. 授权码换取 Access-Token
    @PostMapping("/oauth2/token")
    public Object token(String grant_type, String code, String client_id, String client_secret) {
        return OAuth2Manager.getToken(grant_type, code, client_id, client_secret);
    }

    // 3. Access-Token 换取用户信息
    @GetMapping("/oauth2/userinfo")
    public Object userinfo(String access_token) {
        return OAuth2Manager.getUserinfo(access_token);
    }
}
```

---

## 二十四、微服务网关鉴权

### 24.1 网关依赖（Spring Cloud Gateway）

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
    <version>1.45.0</version>
</dependency>
```

> **注意**：Spring Cloud Gateway 基于 WebFlux，必须使用 `sa-token-reactor-spring-boot3-starter`。

### 24.2 网关全局过滤器

```java
@Configuration
public class SaTokenGatewayConfigure {

    @Bean
    @Order(-100)
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude(
                "/auth/login",
                "/auth/register",
                "/favicon.ico",
                "/actuator/**"
            )
            .setAuth(obj -> {
                // 1. 全局登录校验
                SaRouter.match("/**").check(r -> StpUtil.checkLogin());

                // 2. 路由级权限校验
                SaRouter.match("/user/**",    r -> StpUtil.checkPermission("user"));
                SaRouter.match("/order/**",   r -> StpUtil.checkPermission("order"));
                SaRouter.match("/admin/**",   r -> StpUtil.checkRole("admin"));
            })
            .setError(e -> {
                // 统一异常返回
                if (e instanceof NotLoginException) {
                    return SaResult.error("未登录").setCode(401);
                }
                if (e instanceof NotPermissionException) {
                    return SaResult.error("无权限").setCode(403);
                }
                return SaResult.error(e.getMessage());
            });
    }
}
```

### 24.3 服务间调用传递 Token

在微服务架构中，服务间调用需要传递 Token：

```java
@Configuration
public class FeignTokenInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        // 从当前上下文获取 Token 并传递
        String tokenValue = StpUtil.getTokenValue();
        if (tokenValue != null) {
            template.header(StpUtil.getTokenName(), tokenValue);
        }
    }
}
```

### 24.4 网关鉴权架构建议

```
前端请求
   │
   ▼
┌─────────────────┐
│  Gateway (网关)  │ ← 统一鉴权：登录校验、权限校验
└────────┬────────┘
         │
    ┌────┴────┐
    ▼         ▼
┌───────┐ ┌───────┐
│Service│ │Service│ ← 业务服务：可选的细粒度鉴权（注解）
└───────┘ └───────┘
```

**最佳实践**：
- 网关层：统一登录校验 + 路由级权限校验
- 业务服务层：方法级注解鉴权（`@SaCheckPermission`）
- 服务间调用：通过 Feign 拦截器传递 Token

---

## 二十五、分布式会话

### 25.1 方案一：共享 Redis（推荐）

```xml
<!-- 添加 Redis 集成依赖 (官方推荐 sa-token-redis-template,见 https://sa-token.cc/doc.html#/up/integ-redis) -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
    <version>1.45.0</version>
</dependency>
<!-- 连接池 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      timeout: 10s
      lettuce:
        pool:
          max-active: 200
          max-idle: 50
          min-idle: 10
```

### 25.2 方案二：JWT 无状态

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-jwt</artifactId>
    <version>1.45.0</version>
</dependency>
```

```yaml
sa-token:
  jwt-secret-key: "your-jwt-secret-key"
```

> **建议**：本项目已使用 Redis 做分布式缓存，推荐方案一（共享 Redis），无需额外引入 JWT。

---

## 二十六、持久层扩展（Redis 集成）

### 26.1 添加依赖

```xml
<!-- Sa-Token 整合 Redis (官方推荐 sa-token-redis-template,见 https://sa-token.cc/doc.html#/up/integ-redis) -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
    <version>1.45.0</version>
</dependency>
<!-- 提供 Redis 连接池 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

### 26.2 配置

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0

sa-token:
  # Redis 集成后，Token 数据自动持久化到 Redis
  is-log: true
```

### 26.3 独立 Redis（将权限缓存与业务缓存分离）

```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-alone-redis</artifactId>
    <version>1.45.0</version>
</dependency>
```

```yaml
sa-token:
  alone-redis:
    host: 127.0.0.1
    port: 6380
    database: 1
```

> **建议**：生产环境将权限缓存与业务缓存分离，避免相互影响。

---

## 二十七、配置项详解

```yaml
sa-token:
  ############## 基础配置 ##############

  # token 名称（同时也是 Cookie 名称、Header 名称）
  token-name: satoken

  # token 有效期（单位：秒），默认30天，-1 代表永久有效
  timeout: 2592000

  # token 最低活跃频率（单位：秒），超过此时间未访问系统就会冻结，-1 代表不限制
  active-timeout: -1

  # 是否允许同一账号多地同时登录（true=允许, false=新登录挤掉旧登录）
  is-concurrent: true

  # 在多人登录同一账号时，是否共用一个 token（true=共用, false=每次新建）
  is-share: true

  # token 风格（uuid / simple-uuid / random-32 / random-64 / random-128 / tik）
  token-style: uuid

  # 是否输出操作日志
  is-log: true

  ############## Token 读取配置 ##############

  # 是否从 Cookie 中读取 Token
  is-read-cookie: true

  # 是否从 Header 中读取 Token
  is-read-header: true

  # 是否从 Body 中读取 Token
  is-read-body: false

  ############## Cookie 配置 ##############

  # Cookie 的有效路径
  cookie-path: /

  # Cookie 的安全标志（仅 HTTPS 传输）
  cookie-secure: false

  # Cookie 的 HttpOnly 标志
  cookie-http-only: false

  # Cookie 的 SameSite 标志
  cookie-same-site: Lax

  ############## 其他配置 ##############

  # token 前缀（如 "Bearer "）
  token-prefix: ""

  # 是否在登录后将 Token 写入响应头
  is-write-header: false

  # 是否尝试从请求体中读取 Token
  is-read-body: false
```

---

## 二十八、API 速查手册

### 28.1 登录相关

| API | 说明 |
|-----|------|
| `StpUtil.login(id)` | 登录 |
| `StpUtil.login(id, SaLoginModel)` | 登录（指定参数） |
| `StpUtil.logout()` | 注销当前会话 |
| `StpUtil.logout(loginId)` | 注销指定账号 |
| `StpUtil.logoutByTokenValue(token)` | 根据 Token 注销 |
| `StpUtil.isLogin()` | 是否已登录 |
| `StpUtil.checkLogin()` | 校验登录（未登录抛异常） |
| `StpUtil.getLoginId()` | 获取登录账号id |
| `StpUtil.getLoginIdAsLong()` | 获取登录账号id（Long） |
| `StpUtil.getLoginIdAsString()` | 获取登录账号id（String） |
| `StpUtil.getLoginIdDefaultNull()` | 获取登录账号id（未登录返回 null） |

### 28.2 Token 相关

| API | 说明 |
|-----|------|
| `StpUtil.getTokenValue()` | 获取当前 Token 值 |
| `StpUtil.getTokenName()` | 获取 Token 名称 |
| `StpUtil.getTokenInfo()` | 获取 Token 信息 |
| `StpUtil.getTokenTimeout()` | 获取 Token 剩余有效期 |
| `StpUtil.getLoginIdByToken(token)` | 根据 Token 获取账号id |
| `StpUtil.renewTimeout(timeout)` | 续签 Token |

### 28.3 权限相关

| API | 说明 |
|-----|------|
| `StpUtil.getPermissionList()` | 获取权限列表 |
| `StpUtil.hasPermission(code)` | 是否有指定权限 |
| `StpUtil.checkPermission(code)` | 校验权限（不通过抛异常） |
| `StpUtil.checkPermissionAnd(codes...)` | 校验权限（全部通过） |
| `StpUtil.checkPermissionOr(codes...)` | 校验权限（任一通过） |
| `StpUtil.getRoleList()` | 获取角色列表 |
| `StpUtil.hasRole(role)` | 是否有指定角色 |
| `StpUtil.checkRole(role)` | 校验角色（不通过抛异常） |
| `StpUtil.checkRoleAnd(roles...)` | 校验角色（全部通过） |
| `StpUtil.checkRoleOr(roles...)` | 校验角色（任一通过） |

### 28.4 踢人下线

| API | 说明 |
|-----|------|
| `StpUtil.kickout(loginId)` | 踢人下线 |
| `StpUtil.kickoutByTokenValue(token)` | 根据 Token 踢人 |
| `StpUtil.replaced(loginId)` | 顶人下线 |
| `StpUtil.logoutByLoginId(loginId)` | 强制注销 |

### 28.5 Session 相关

| API | 说明 |
|-----|------|
| `StpUtil.getSession()` | 获取 Account-Session |
| `StpUtil.getTokenSession()` | 获取 Token-Session |
| `StpUtil.getSessionBySessionId(id)` | 获取自定义 Session |

### 28.6 账号封禁

| API | 说明 |
|-----|------|
| `StpUtil.disable(loginId, time)` | 封禁账号 |
| `StpUtil.disable(loginId, service, time)` | 分类封禁 |
| `StpUtil.untieDisable(loginId)` | 解封 |
| `StpUtil.untieDisable(loginId, service)` | 分类解封 |
| `StpUtil.isDisabled(loginId)` | 是否被封禁 |
| `StpUtil.getDisableTime(loginId)` | 剩余封禁时间 |

### 28.7 二级认证

| API | 说明 |
|-----|------|
| `StpUtil.openSafe(timeout)` | 开启二级认证 |
| `StpUtil.closeSafe()` | 关闭二级认证 |
| `StpUtil.isSafe()` | 是否处于二级认证 |
| `StpUtil.checkSafe()` | 校验二级认证 |

---

## 附录：本项目集成建议

### A.1 依赖选择

本项目基于 Spring Boot 3.5.x + Spring Cloud Gateway（WebFlux），建议：

| 模块 | 依赖 | 说明 |
|------|------|------|
| 网关（demo-gateway） | `sa-token-reactor-spring-boot3-starter` | WebFlux 环境 |
| 业务服务 | `sa-token-spring-boot3-starter` | Servlet 环境 |
| Redis 持久化 | `sa-token-redis-template` | 分布式会话（官方推荐） |
| Redis 连接池 | `commons-pool2` | Lettuce 连接池支持 |

### A.2 架构建议

1. **网关层统一鉴权**：在 `demo-gateway` 配置 `SaReactorFilter`，统一处理登录校验和路由级权限校验
2. **业务服务细粒度鉴权**：业务服务使用 `@SaCheckPermission`、`@SaCheckRole` 注解做方法级鉴权
3. **权限数据加载**：业务服务实现 `StpInterface` 接口，从数据库查询权限和角色
4. **Redis 共享会话**：所有服务共享同一个 Redis，保证分布式会话一致性
5. **前后台分离**：前端通过 Header 传递 Token，后端配置 `is-read-header: true`、`is-read-cookie: false`

### A.3 注意事项

- 本项目使用 JDK 25，Sa-Token 1.45.0 支持最新技术栈（Spring Boot 3.x，JDK 17+）
- WebFlux 环境必须使用 reactor starter，否则无法工作
- 服务间 Feign 调用需通过 `RequestInterceptor` 传递 Token
- 生产环境建议使用独立 Redis 存储权限数据，避免与业务缓存相互影响

---

## 二十九、权限数据缓存与动态鉴权

> 本章节是项目集成的**核心实践部分**，解决生产环境中权限数据频繁查询数据库导致的性能问题，
> 以及路由规则需要动态调整（不重启服务）的灵活性问题。
>
> 对应官方文档：
> - [参考：把权限放在缓存里](https://sa-token.cc/doc.html#/fun/jur-cache)
> - [参考：把路由拦截鉴权动态化](https://sa-token.cc/doc.html#/fun/dynamic-router-check)

### 29.1 问题背景

#### 29.1.1 默认实现的性能问题

Sa-Token 默认通过 `StpInterface.getPermissionList()` 获取权限码集合。在以下场景会触发该调用：

- 每次 `StpUtil.checkPermission()` 校验
- 每次 `@SaCheckPermission` 注解鉴权
- 每次 `StpUtil.hasPermission()` 判断

如果 `StpInterface` 实现直接查数据库，则**每次接口请求都会查一次权限表**，在高并发场景下数据库压力巨大。

#### 29.1.2 路由拦截的灵活性问题

第七章展示的路由拦截是**硬编码**的：

```java
// 硬编码：修改路由规则需要重启服务
SaRouter.match("/user/**",    r -> StpUtil.checkPermission("user"));
SaRouter.match("/admin/**",   r -> StpUtil.checkRole("admin"));
```

生产环境中，权限规则经常需要动态调整（新增接口、调整权限分配），硬编码方式无法满足需求。

### 29.2 权限数据缓存方案

#### 29.2.1 方案一：在 StpInterface 实现中缓存（推荐）

在 `StpInterface` 实现类中引入 Redis 缓存，权限数据查询时优先走缓存：

```java
/**
 * 自定义权限验证接口扩展：带 Redis 缓存
 */
@Component
@AllArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionRepository permissionRepository;
    private final StringRedisTemplate redisTemplate;

    /** 权限缓存有效期：30 分钟 */
    private static final Duration PERMISSION_CACHE_TIMEOUT = Duration.ofMinutes(30);

    /** 角色缓存有效期：30 分钟 */
    private static final Duration ROLE_CACHE_TIMEOUT = Duration.ofMinutes(30);

    private static final String PERMISSION_CACHE_KEY = "sa:permission:%s";
    private static final String ROLE_CACHE_KEY = "sa:role:%s";

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String key = PERMISSION_CACHE_KEY.formatted(loginId);
        // 1. 先查缓存
        List<String> cached = redisTemplate.opsForValue().get(key)
            .map(json -> parsePermissionList(json))
            .orElse(null);
        if (cached != null) {
            return cached;
        }
        // 2. 缓存未命中，查数据库
        List<String> permissions = permissionRepository.findPermissionsByUserId(Long.parseLong(loginId.toString()));
        // 3. 写入缓存
        redisTemplate.opsForValue().set(key, toJson(permissions), PERMISSION_CACHE_TIMEOUT);
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String key = ROLE_CACHE_KEY.formatted(loginId);
        List<String> cached = redisTemplate.opsForValue().get(key)
            .map(this::parsePermissionList)
            .orElse(null);
        if (cached != null) {
            return cached;
        }
        List<String> roles = permissionRepository.findRolesByUserId(Long.parseLong(loginId.toString()));
        redisTemplate.opsForValue().set(key, toJson(roles), ROLE_CACHE_TIMEOUT);
        return roles;
    }

    /**
     * 权限变更时清除缓存（业务层调用）
     */
    public void clearPermissionCache(Long userId) {
        redisTemplate.delete(PERMISSION_CACHE_KEY.formatted(userId));
        redisTemplate.delete(ROLE_CACHE_KEY.formatted(userId));
    }

    // JSON 序列化/反序列化略
    private List<String> parsePermissionList(String json) { /* ... */ }
    private String toJson(List<String> list) { /* ... */ }
}
```

#### 29.2.2 方案二：使用 Sa-Token 内置 Session 缓存

利用 Sa-Token 的 Account-Session 缓存权限数据，随会话生命周期管理：

```java
@Component
@AllArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final PermissionRepository permissionRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1. 从 Account-Session 获取缓存
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        List<String> permissions = session.get("permissions", () -> {
            // 2. 缓存未命中，查数据库
            return permissionRepository.findPermissionsByUserId(Long.parseLong(loginId.toString()));
        });
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        return session.get("roles", () ->
            permissionRepository.findRolesByUserId(Long.parseLong(loginId.toString()))
        );
    }
}
```

**两种方案对比**：

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 方案一（Redis） | 缓存独立于会话，用户重新登录仍命中缓存 | 需自行管理缓存失效 | 权限变更不频繁 |
| 方案二（Session） | 随会话生命周期自动管理，登出即清除 | 用户重新登录需重新加载 | 权限随会话变更 |

#### 29.2.3 权限变更时清除缓存

当管理员修改了某用户的权限/角色后，需要主动清除该用户的缓存：

```java
@Service
@AllArgsConstructor
public class PermissionService {

    private final StpInterfaceImpl stpInterface;
    private final UserRoleRepository userRoleRepository;

    /**
     * 给用户分配角色（清除权限缓存）
     */
    @Transactional
    public void assignRole(Long userId, Long roleId) {
        userRoleRepository.insert(userId, roleId);
        // 清除该用户的权限和角色缓存
        stpInterface.clearPermissionCache(userId);
    }

    /**
     * 角色权限变更时，清除所有拥有该角色的用户缓存
     */
    public void onRolePermissionChanged(Long roleId) {
        List<Long> userIds = userRoleRepository.findUserIdsByRoleId(roleId);
        userIds.forEach(stpInterface::clearPermissionCache);
    }
}
```

### 29.3 数据库存储设计

#### 29.3.1 RBAC 权限模型表结构

标准的 RBAC（Role-Based Access Control）模型需要 5 张表：

```sql
-- 用户表（业务已有，示例）
CREATE TABLE t_sys_user (
    id          BIGINT PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    password    VARCHAR(128) NOT NULL,
    -- 通用字段
    created_by  VARCHAR(64),
    create_time TIMESTAMP,
    updated_by  VARCHAR(64),
    update_time TIMESTAMP,
    deleted     BOOLEAN DEFAULT FALSE,
    version     INT DEFAULT 0
);

-- 角色表
CREATE TABLE t_sys_role (
    id          BIGINT PRIMARY KEY,
    role_code   VARCHAR(64) NOT NULL,      -- 角色编码（如 admin、user）
    role_name   VARCHAR(128) NOT NULL,     -- 角色名称
    description VARCHAR(256),
    -- 通用字段同上
    UNIQUE (role_code)
);

-- 权限表
CREATE TABLE t_sys_permission (
    id              BIGINT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,  -- 权限编码（如 user:add、user:delete）
    permission_name VARCHAR(128) NOT NULL,  -- 权限名称
    resource_type   VARCHAR(32),            -- 资源类型（menu、button、api）
    description     VARCHAR(256),
    -- 通用字段同上
    UNIQUE (permission_code)
);

-- 用户-角色关联表
CREATE TABLE t_sys_user_role (
    id      BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE (user_id, role_id)
);

-- 角色-权限关联表
CREATE TABLE t_sys_role_permission (
    id            BIGINT PRIMARY KEY,
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE (role_id, permission_id)
);

-- 索引
CREATE INDEX idx_user_role_user_id ON t_sys_user_role(user_id);
CREATE INDEX idx_role_permission_role_id ON t_sys_role_permission(role_id);
```

#### 29.3.2 查询用户权限的 SQL

```sql
-- 查询用户的所有权限码
SELECT DISTINCT p.permission_code
FROM t_sys_user_role ur
JOIN t_sys_role_permission rp ON ur.role_id = rp.role_id
JOIN t_sys_permission p ON rp.permission_id = p.id
WHERE ur.user_id = #{userId}
  AND p.deleted = FALSE;

-- 查询用户的所有角色编码
SELECT DISTINCT r.role_code
FROM t_sys_user_role ur
JOIN t_sys_role r ON ur.role_id = r.id
WHERE ur.user_id = #{userId}
  AND r.deleted = FALSE;
```

### 29.4 路由拦截鉴权动态化

#### 29.4.1 数据库存储路由规则

新增一张路由权限规则表：

```sql
-- 路由权限规则表
CREATE TABLE t_sys_route_rule (
    id              BIGINT PRIMARY KEY,
    route_pattern   VARCHAR(256) NOT NULL,  -- 路由匹配模式（如 /user/**）
    check_type      VARCHAR(32) NOT NULL,   -- 校验类型：login/permission/role
    check_value     VARCHAR(256),           -- 校验值（如 user:add，多个用逗号分隔）
    description     VARCHAR(256),
    enabled         BOOLEAN DEFAULT TRUE,   -- 是否启用
    -- 通用字段同上
);

-- 示例数据
INSERT INTO t_sys_route_rule (route_pattern, check_type, check_value, description) VALUES
('/user/**',     'permission', 'user',           '用户模块需要 user 权限'),
('/admin/**',    'role',       'admin',          '管理模块需要 admin 角色'),
('/order/**',    'permission', 'order',          '订单模块需要 order 权限'),
('/public/**',   'login',      NULL,             '公开接口需要登录'),
('/api/auth/**', 'anonymous',  NULL,             '认证接口匿名访问');
```

#### 29.4.2 动态路由加载器

```java
/**
 * 路由规则加载器：从数据库加载路由权限规则，支持缓存
 */
@Component
@AllArgsConstructor
@Slf4j
public class RouteRuleLoader {

    private final RouteRuleRepository routeRuleRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String ROUTE_RULES_CACHE_KEY = "sa:route-rules";
    private static final Duration CACHE_TIMEOUT = Duration.ofMinutes(10);

    /**
     * 加载所有启用的路由规则（带缓存）
     */
    public List<RouteRule> loadRules() {
        // 1. 先查缓存
        String cached = redisTemplate.opsForValue().get(ROUTE_RULES_CACHE_KEY);
        if (cached != null) {
            return parseRules(cached);
        }
        // 2. 查数据库
        List<RouteRule> rules = routeRuleRepository.findAllEnabled();
        redisTemplate.opsForValue().set(ROUTE_RULES_CACHE_KEY, toJson(rules), CACHE_TIMEOUT);
        return rules;
    }

    /**
     * 路由规则变更时清除缓存
     */
    public void clearCache() {
        redisTemplate.delete(ROUTE_RULES_CACHE_KEY);
        log.info("[RouteRuleLoader] 路由规则缓存已清除");
    }
}

/**
 * 路由规则实体
 */
public record RouteRule(
    String routePattern,
    String checkType,      // login / permission / role / anonymous
    String checkValue,
    String description
) {}
```

#### 29.4.3 网关动态鉴权过滤器（WebFlux）

```java
/**
 * 网关动态鉴权过滤器
 * 基于数据库路由规则，动态校验权限，无需重启服务
 */
@Configuration
@AllArgsConstructor
public class DynamicAuthFilterConfigure {

    private final RouteRuleLoader routeRuleLoader;

    @Bean
    @Order(-100)
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude("/auth/login", "/auth/register", "/favicon.ico", "/actuator/**")
            .setAuth(obj -> {
                // 1. 动态加载路由规则
                List<RouteRule> rules = routeRuleLoader.loadRules();

                // 2. 遍历规则，匹配则执行校验
                for (RouteRule rule : rules) {
                    SaRouter.match(rule.routePattern()).check(r -> {
                        switch (rule.checkType()) {
                            case "login"      -> StpUtil.checkLogin();
                            case "permission" -> StpUtil.checkPermission(rule.checkValue());
                            case "role"       -> StpUtil.checkRole(rule.checkValue());
                            case "anonymous"  -> { /* 不做校验 */ }
                            default -> log.warn("未知的校验类型：{}", rule.checkType());
                        }
                    });
                }
            })
            .setError(e -> {
                if (e instanceof NotLoginException) {
                    return SaResult.error("未登录").setCode(401);
                }
                if (e instanceof NotPermissionException) {
                    return SaResult.error("无权限").setCode(403);
                }
                if (e instanceof NotRoleException) {
                    return SaResult.error("无角色").setCode(403);
                }
                return SaResult.error(e.getMessage());
            });
    }
}
```

#### 29.4.4 规则变更后刷新缓存

管理员通过后台修改路由规则后，调用 `clearCache()` 即可让新规则立即生效：

```java
@RestController
@AllArgsConstructor
@RequestMapping("/admin/route-rule")
public class RouteRuleController {

    private final RouteRuleRepository routeRuleRepository;
    private final RouteRuleLoader routeRuleLoader;

    @PostMapping
    public SaResult create(@RequestBody RouteRuleDTO dto) {
        routeRuleRepository.insert(dto);
        // 清除缓存，让新规则立即生效
        routeRuleLoader.clearCache();
        return SaResult.ok();
    }

    @PutMapping("/{id}")
    public SaResult update(@PathVariable Long id, @RequestBody RouteRuleDTO dto) {
        routeRuleRepository.update(id, dto);
        routeRuleLoader.clearCache();
        return SaResult.ok();
    }

    @DeleteMapping("/{id}")
    public SaResult delete(@PathVariable Long id) {
        routeRuleRepository.delete(id);
        routeRuleLoader.clearCache();
        return SaResult.ok();
    }
}
```

### 29.5 完整架构图

```
┌──────────────────────────────────────────────────────────┐
│                         前端请求                          │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│              Gateway (WebFlux) 动态鉴权过滤器             │
│                                                          │
│  1. 加载路由规则（Redis 缓存 → DB）                       │
│  2. 匹配路由，执行校验（login/permission/role）           │
│  3. 调用 StpUtil.checkLogin/checkPermission/checkRole     │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────┐
│                    业务服务（Servlet）                    │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │  @SaCheckPermission 注解鉴权（方法级细粒度）        │  │
│  └────────────────────────┬───────────────────────────┘  │
│                           │                              │
│                           ▼                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │  StpInterface 实现                                  │  │
│  │  1. 查 Account-Session 缓存                         │  │
│  │  2. 查 Redis 权限缓存                               │  │
│  │  3. 查数据库                                        │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            ▼              ▼              ▼
       ┌─────────┐  ┌───────────┐  ┌──────────┐
       │ Redis   │  │ 数据库     │  │ 管理后台  │
       │         │  │           │  │          │
       │ 权限缓存 │  │ 权限规则表 │  │ 规则管理  │
       │ 路由规则 │  │ 用户角色表 │  │ 角色管理  │
       └─────────┘  └───────────┘  └──────────┘
```

### 29.6 关键设计要点

1. **多级缓存**：Session 缓存 → Redis 缓存 → 数据库，逐层兜底
2. **缓存失效**：权限/角色变更时，主动清除对应用户的缓存
3. **路由规则动态化**：路由规则存数据库，通过缓存 + 主动刷新实现动态生效
4. **网关统一鉴权**：粗粒度（路由级）在网关，细粒度（方法级）在业务服务
5. **事务一致性**：权限变更操作使用 `@Transactional`，先更新数据库再清缓存

### 29.7 注意事项

- **缓存穿透**：对不存在的用户也要缓存空值，防止恶意请求穿透到数据库
- **缓存雪崩**：缓存过期时间加随机抖动（如 30 分钟 ± 5 分钟）
- **缓存一致性**：先更新数据库，再删除缓存（Cache Aside Pattern）
- **权限通配符**：Sa-Token 支持 `art.*` 通配符，可减少权限码数量
- **性能监控**：监控 `StpInterface` 调用次数和耗时，确保缓存生效

---

## 三十、官方文档索引（按需查阅）

> 以下为 Sa-Token 官方文档完整目录，按模块分类整理。
> 需要某项功能的详细实现时，点击链接直达官方文档。

### 30.1 开始

| 章节 | 链接 | 说明 |
|------|------|------|
| 框架介绍 | https://sa-token.cc/doc.html#/ | 框架概览、功能特性 |
| 在 SpringBoot 环境集成 | https://sa-token.cc/doc.html#/start/example | Servlet 环境集成 |
| 在 WebFlux 环境集成 | https://sa-token.cc/doc.html#/start/webflux-example | Reactive 环境集成（本项目网关使用） |
| 在 Solon 环境集成 | https://sa-token.cc/doc.html#/start/solon-example | Solon 框架集成 |
| 其它环境集成示例 | https://sa-token.cc/doc.html#/start/download | JFinal、Solon 等 |
| 集成示例大全下载 | https://sa-token.cc/doc.html#/more/download-demos | 官方示例代码 |

### 30.2 基础

| 章节 | 链接 | 说明 |
|------|------|------|
| 登录认证 | https://sa-token.cc/doc.html#/use/login-auth | 登录、注销、会话查询 |
| 权限认证 | https://sa-token.cc/doc.html#/use/jur-auth | 权限码、角色校验 |
| 踢人下线 | https://sa-token.cc/doc.html#/use/kick | 踢人、顶人、注销 |
| 注解鉴权 | https://sa-token.cc/doc.html#/use/at-check | @SaCheckLogin 等 |
| 路由拦截鉴权 | https://sa-token.cc/doc.html#/use/route-check | SaRouter 路由匹配 |
| Session 会话 | https://sa-token.cc/doc.html#/use/session | 三种 Session 模型 |
| 框架配置 | https://sa-token.cc/doc.html#/use/config | 所有配置项说明 |

### 30.3 深入

| 章节 | 链接 | 说明 |
|------|------|------|
| 集成 Redis | https://sa-token.cc/doc.html#/up/integ-redis | Redis 持久化 |
| 前后端分离 | https://sa-token.cc/doc.html#/up/not-cookie | 无 Cookie 模式 |
| 自定义 Token 风格 | https://sa-token.cc/doc.html#/up/token-style | 六种 Token 风格 |
| Token 提交前缀 | https://sa-token.cc/doc.html#/up/token-prefix | 如 "Bearer " 前缀 |
| 同端互斥登录 | https://sa-token.cc/doc.html#/up/mutex-login | QQ 模式互斥 |
| 记住我模式 | https://sa-token.cc/doc.html#/up/remember-me | 临时 Cookie vs 持久 Cookie |
| 登录参数 & 注销参数 | https://sa-token.cc/doc.html#/up/login-parameter | SaLoginModel 详解 |
| 二级认证 | https://sa-token.cc/doc.html#/up/safe-auth | 敏感操作二次校验 |
| 模拟他人 & 身份切换 | https://sa-token.cc/doc.html#/up/mock-person | 临时切换身份 |
| 账号封禁 | https://sa-token.cc/doc.html#/up/disable | 分类封禁、阶梯封禁 |
| 密码加密 | https://sa-token.cc/doc.html#/up/password-secure | MD5/SHA/AES/RSA |
| 会话查询 | https://sa-token.cc/doc.html#/up/search-session | 查询所有登录会话 |
| Http Basic/Digest 认证 | https://sa-token.cc/doc.html#/up/basic-auth | Basic 认证 |
| 全局侦听器 | https://sa-token.cc/doc.html#/up/global-listener | SaTokenListener |
| 全局过滤器 | https://sa-token.cc/doc.html#/up/global-filter | SaServletFilter/SaReactorFilter |
| 多账号认证 | https://sa-token.cc/doc.html#/up/many-account | 多套账号体系 |

### 30.4 单点登录（SSO）

| 章节 | 链接 | 说明 |
|------|------|------|
| 单点登录简述 | https://sa-token.cc/doc.html#/sso/readme | 三种模式介绍 |
| 搭建 SSO-Server | https://sa-token.cc/doc.html#/sso/sso-server | 认证中心搭建 |
| SSO-Server 开放 API | https://sa-token.cc/doc.html#/sso/sso-apidoc | 接口文档 |
| SSO 模式一 | https://sa-token.cc/doc.html#/sso/sso-type1 | 共享 Cookie 同步会话 |
| SSO 模式二 | https://sa-token.cc/doc.html#/sso/sso-type2 | URL 重定向传播会话（最常用） |
| SSO 模式三 | https://sa-token.cc/doc.html#/sso/sso-type3 | Http 请求获取会话 |
| 配置域名校验 | https://sa-token.cc/doc.html#/sso/sso-check-domain | 安全配置 |
| 定制化登录页面 | https://sa-token.cc/doc.html#/sso/sso-custom-login | 自定义 UI |
| 自定义 API 路由 | https://sa-token.cc/doc.html#/sso/sso-custom-api | 路由定制 |
| 平台中心跳转模式 | https://sa-token.cc/doc.html#/sso/sso-home-jump | 统一入口 |
| 匿名 client 接入 | https://sa-token.cc/doc.html#/sso/anon-client | 无需注册 client |
| 单点注销 | https://sa-token.cc/doc.html#/sso/signout | 全端下线 |
| 前后端分离整合方案 | https://sa-token.cc/doc.html#/sso/sso-h5 | H5/小程序 SSO |
| 消息推送机制 | https://sa-token.cc/doc.html#/sso/message-push | 服务端推送 |
| 用户数据同步/迁移 | https://sa-token.cc/doc.html#/sso/user-data-sync | 数据一致性 |
| NoSdk、ReSdk 模式 | https://sa-token.cc/doc.html#/sso/sso-nosdk | 非 Java 项目接入 |
| SSO 代码 API 参考 | https://sa-token.cc/doc.html#/sso/sso-dev | API 手册 |
| 常见问题总结 | https://sa-token.cc/doc.html#/sso/sso-questions | FAQ |

### 30.5 OAuth2.0

| 章节 | 链接 | 说明 |
|------|------|------|
| OAuth2.0 简述 | https://sa-token.cc/doc.html#/oauth2/readme | 协议介绍 |
| OAuth2-Server 搭建 | https://sa-token.cc/doc.html#/oauth2/oauth2-server | 服务端搭建 |
| OAuth2-Server 开放 API | https://sa-token.cc/doc.html#/oauth2/oauth2-apidoc | 接口文档 |
| 自定义数据加载器 | https://sa-token.cc/doc.html#/oauth2/oauth2-data-loader | 数据加载定制 |
| 配置 client 域名校验 | https://sa-token.cc/doc.html#/oauth2/oauth2-check-domain | 安全配置 |
| 自定义 Scope 权限及处理器 | https://sa-token.cc/doc.html#/oauth2/oauth2-custom-scope | Scope 扩展 |
| 为 Scope 划分等级 | https://sa-token.cc/doc.html#/oauth2/oauth2-scope-level | 层级 Scope |
| 自定义 grant_type | https://sa-token.cc/doc.html#/oauth2/oauth2-custom-grant_type | 授权类型扩展 |
| 定制化登录与授权页面 | https://sa-token.cc/doc.html#/oauth2/oauth2-custom-login | UI 定制 |
| 自定义 API 路由 | https://sa-token.cc/doc.html#/oauth2/oauth2-custom-api | 路由定制 |
| OAuth2-Server 前后台分离 | https://sa-token.cc/doc.html#/oauth2/oauth2-h5 | H5 接入 |
| OpenId 与 UnionId | https://sa-token.cc/doc.html#/oauth2/oauth2-openid | 身份标识 |
| 开启 OIDC 协议 | https://sa-token.cc/doc.html#/oauth2/oauth2-oidc | OpenID Connect |
| 使用注解校验 Access-Token | https://sa-token.cc/doc.html#/oauth2/oauth2-at-check | 注解鉴权 |
| OAuth2 与登录会话数据互通 | https://sa-token.cc/doc.html#/oauth2/oauth2-interworking | 会话互通 |
| OAuth2 代码 API 参考 | https://sa-token.cc/doc.html#/oauth2/oauth2-dev | API 手册 |
| 常见问题总结 | https://sa-token.cc/doc.html#/oauth2/oauth2-questions | FAQ |

### 30.6 微服务

| 章节 | 链接 | 说明 |
|------|------|------|
| 分布式 Session 会话 | https://sa-token.cc/doc.html#/micro/dcs-session | 跨服务会话共享 |
| 网关统一鉴权 | https://sa-token.cc/doc.html#/micro/gateway-auth | Gateway 鉴权（**本项目重点**） |
| 内部服务外网隔离 | https://sa-token.cc/doc.html#/micro/same-token | 服务间调用鉴权 |
| 依赖引入说明 | https://sa-token.cc/doc.html#/micro/import-intro | 各环境依赖选择 |

### 30.7 插件

| 章节 | 链接 | 说明 |
|------|------|------|
| AOP 注解鉴权 | https://sa-token.cc/doc.html#/plugin/aop-at | 注解鉴权增强 |
| 临时 Token 认证 | https://sa-token.cc/doc.html#/plugin/temp-token | 短时 Token |
| Quick-Login 快速登录插件 | https://sa-token.cc/doc.html#/plugin/quick-login | 开发期快速登录 |
| Alone 独立 Redis 插件 | https://sa-token.cc/doc.html#/plugin/alone-redis | 权限缓存与业务缓存分离 |
| 缓存层扩展 | https://sa-token.cc/doc.html#/plugin/dao-extend | 自定义 SaTokenDao |
| JSON 序列化扩展 | https://sa-token.cc/doc.html#/plugin/json-extend | 自定义序列化 |
| 序列化插件扩展包 | https://sa-token.cc/doc.html#/plugin/custom-serializer | 序列化扩展 |
| 和 Thymeleaf 集成 | https://sa-token.cc/doc.html#/plugin/thymeleaf-extend | 模板引擎集成 |
| 和 Freemarker 集成 | https://sa-token.cc/doc.html#/plugin/freemarker-extend | 模板引擎集成 |
| 注解鉴权 SpEL 表达式 | https://sa-token.cc/doc.html#/plugin/spel-at | 动态权限表达式 |
| 和 jwt 集成 | https://sa-token.cc/doc.html#/plugin/jwt-extend | JWT 无状态 |
| 和 Dubbo 集成 | https://sa-token.cc/doc.html#/plugin/dubbo-extend | Dubbo 鉴权 |
| 和 gRPC 集成 | https://sa-token.cc/doc.html#/plugin/grpc-extend | gRPC 鉴权 |
| API 接口参数签名 | https://sa-token.cc/doc.html#/plugin/api-sign | 接口签名校验 |
| API Key 接口调用秘钥 | https://sa-token.cc/doc.html#/plugin/api-key | API Key 鉴权 |
| Sa-Token 插件开发指南 | https://sa-token.cc/doc.html#/fun/plugin-dev | 自定义插件 |
| 自定义 SaTokenContext 指南 | https://sa-token.cc/doc.html#/fun/sa-token-context | 上下文扩展 |

### 30.8 API 手册

| 章节 | 链接 | 说明 |
|------|------|------|
| StpUtil 鉴权工具类 | https://sa-token.cc/doc.html#/api/stp-util | 核心 API |
| SaSession 会话对象 | https://sa-token.cc/doc.html#/api/sa-session | 会话 API |
| SaTokenDao 数据持久接口 | https://sa-token.cc/doc.html#/api/sa-token-dao | 持久层接口 |
| SaStrategy 全局策略 | https://sa-token.cc/doc.html#/api/sa-strategy | 策略定制 |
| 全局类、方法 | https://sa-token.cc/doc.html#/more/common-action | 全局工具 |

### 30.9 框架设计

| 章节 | 链接 | 说明 |
|------|------|------|
| 仓库目录 | https://sa-token.cc/doc.html#/arch/dir-intro | 源码结构 |
| 数据结构 | https://sa-token.cc/doc.html#/arch/data-structure | 内部数据模型 |

### 30.10 附录（重要参考）

| 章节 | 链接 | 说明 |
|------|------|------|
| 常见问题排查 | https://sa-token.cc/doc.html#/more/common-questions | **问题排查首选** |
| 框架名词解释 | https://sa-token.cc/doc.html#/more/noun-intro | 概念说明 |
| Sa-Token 功能结构图 | https://sa-token.cc/doc.html#/fun/auth-flow | 架构图 |
| 全局 Log 输出 | https://sa-token.cc/doc.html#/fun/log | 日志配置 |
| 异步 & Mock 上下文 | https://sa-token.cc/doc.html#/fun/async--mock | 异步场景 |
| 未登录场景值详解 | https://sa-token.cc/doc.html#/fun/not-login-scene | 异常细分 |
| Token 有效期详解 | https://sa-token.cc/doc.html#/fun/token-timeout | 过期策略 |
| Session 模型详解 | https://sa-token.cc/doc.html#/fun/session-model | 会话模型 |
| 数据读写三大作用域 | https://sa-token.cc/doc.html#/fun/three-scope | 作用域说明 |
| TokenInfo 参数详解 | https://sa-token.cc/doc.html#/fun/token-info | Token 参数 |
| 异常细分状态码 | https://sa-token.cc/doc.html#/fun/exception-code | 异常码 |
| 自定义注解 | https://sa-token.cc/doc.html#/fun/custom-annotations | 注解扩展 |
| 防火墙 | https://sa-token.cc/doc.html#/fun/firewall | 安全防护 |
| **把权限放在缓存里** | https://sa-token.cc/doc.html#/fun/jur-cache | **权限缓存方案** |
| **把路由拦截鉴权动态化** | https://sa-token.cc/doc.html#/fun/dynamic-router-check | **动态路由鉴权** |
| 解决反向代理 uri 丢失 | https://sa-token.cc/doc.html#/fun/curr-domain | 代理问题 |
| 解决跨域问题 | https://sa-token.cc/doc.html#/fun/cors-filter | CORS 配置 |
| 技术选型：SSO 与 OAuth2 对比 | https://sa-token.cc/doc.html#/fun/sso-vs-oauth2 | 选型参考 |
| 集成 MongoDB 参考一 | https://sa-token.cc/doc.html#/up/integ-spring-mongod-1 | MongoDB 集成 |
| 集成 MongoDB 参考二 | https://sa-token.cc/doc.html#/up/integ-spring-mongod-2 | MongoDB 集成 |
| 从 Shiro、SpringSecurity、JWT 迁移 | https://sa-token.cc/doc.html#/fun/auth-framework-function-test | 框架迁移 |
| issue 提问模板 | https://sa-token.cc/doc.html#/fun/issue-template | 提问规范 |
| 为 Sa-Token 贡献代码 | https://sa-token.cc/doc.html#/fun/git-pr | 贡献指南 |

### 30.11 其他

| 章节 | 链接 | 说明 |
|------|------|------|
| 更新日志 | https://sa-token.cc/doc.html#/more/update-log | 版本变更 |
| 框架生态 | https://sa-token.cc/doc.html#/more/link | 相关项目 |
| 框架博客 | https://sa-token.cc/doc.html#/more/blog | 技术文章 |
| 推荐公众号 | https://sa-token.cc/doc.html#/more/tj-gzh | 关注渠道 |
| 加入讨论群 | https://sa-token.cc/doc.html#/more/join-group | 社区交流 |
| Sa-Token 内容合作群 | https://sa-token.cc/doc.html#/more/content-cooperation | 内容合作 |
| 赞助 Sa-Token | https://sa-token.cc/doc.html#/more/sa-token-donate | 赞助渠道 |
| 需求提交 | https://sa-token.cc/doc.html#/more/demand-commit | 需求反馈 |
| 问卷调查 | https://sa-token.cc/doc.html#/more/wenjuan | 满意度调查 |

---

**参考文档**：
- [Sa-Token 官方文档](https://sa-token.cc/doc.html)
- [Sa-Token GitHub](https://github.com/dromara/sa-token)
- [Sa-Token 示例项目](https://gitee.com/dromara/sa-token/tree/master/sa-token-demo)
- [把权限放在缓存里](https://sa-token.cc/doc.html#/fun/jur-cache)
- [把路由拦截鉴权动态化](https://sa-token.cc/doc.html#/fun/dynamic-router-check)
- [网关统一鉴权](https://sa-token.cc/doc.html#/micro/gateway-auth)
