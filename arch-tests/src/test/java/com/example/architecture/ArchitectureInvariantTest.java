package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构不变量守护（spec 2026-11-03-auth-permission-redesign §8）。
 *
 * <p>七条规则把"好设计"固化为红绿测试——任何退化尝试会在构建期被拦截，
 * 这是"不在坏设计上反复修改"的机器强制层。
 *
 * @author arch-tests
 */
@DisplayName("架构不变量（七条）")
class ArchitectureInvariantTest {

  private static JavaClasses all;

  @BeforeAll
  static void importAllClasses() {
    // 锚点类反查所属 jar（getResources 对无目录条目的 jar 不可靠；starter 是 boot jar 不可用）
    Class<?>[] anchors = {
      com.example.shared.security.SessionClaims.class,                       // shared-security-contract
      com.example.shared.permission.PermissionAutoConfiguration.class,       // shared-permission-starter
      com.example.shared.web.core.api.ApiResult.class,                       // shared-api（底座代表）
      com.example.gateway.security.SessionContextInjector.class,             // gateway-framework
      com.example.bff.shared.business.BffBusinessApi.class,                  // bff-framework
      com.example.bff.internet.adapter.controller.BffBusinessController.class,   // internet-bff-web
      com.example.bff.intranet.adapter.controller.BffBusinessController.class,   // intranet-bff-web
      com.example.auth.api.PermissionCheckApi.class,                         // auth-api
      com.example.auth.adapter.channel.SessionClaimsWriter.class,            // auth-adapter
      com.example.auth.infrastructure.channel.SessionPlanSelectedEventListener.class, // auth-infrastructure
      com.example.auth.application.authorization.PermissionQueryService.class,          // auth-application
      com.example.annuity.adapter.controller.AnnuityController.class,        // annuity-adapter
      com.example.annuity.application.service.AnnuityAppService.class,       // annuity-application（不变量8 ID 门面覆盖）
      com.example.annuity.infrastructure.gateway.MockFileIntegrationGateway.class, // annuity-infrastructure
      com.example.approval.adapter.controllers.ApprovalFlowAdapter.class,    // approval-adapter
      com.example.approval.application.service.ApprovalInstanceService.class, // approval-application（IdService 收编点）
      com.example.approval.domain.aggregate.root.ApprovalInstance.class,     // approval-domain（ApprovalIdGenerator 端口）
      com.example.approval.infrastructure.id.IdServiceApprovalIdGenerator.class, // approval-infrastructure
      com.example.file.adapter.controllers.FileTaskAdapter.class,            // file-adapter
      com.example.file.application.usecase.ApplyUploadTokenUseCase.class,    // file-application（IdService 收编点）
      com.example.file.infrastructure.storage.RedisFileTokenStore.class,  // file-infrastructure（Redisson SETNX 原语，非缓存）
      com.example.integration.adapter.trade.TradeQueryAdapter.class,         // integration-service-adapter
      com.example.core.api.context.BusinessMetaContext.class,                // business-core-api
      com.example.core.application.business.guard.DefaultBusinessAccessGuard.class, // business-core-application
      com.example.core.adapter.context.SessionContextResolver.class          // business-core-adapter
    };
    java.util.Set<com.tngtech.archunit.core.importer.Location> locations = new java.util.LinkedHashSet<>();
    for (Class<?> anchor : anchors) {
      java.security.CodeSource source = anchor.getProtectionDomain().getCodeSource();
      if (source != null) {
        locations.add(com.tngtech.archunit.core.importer.Location.of(source.getLocation()));
      }
    }
    all = new ClassFileImporter().importLocations(locations);
  }

  /** 业务服务 api 契约的包前缀（判据：框架层依赖里不允许出现这些包）. */
  private static final String[] BUSINESS_API_PACKAGES = {
    "com.example.auth.api..",
    "com.example.annuity.api..",
    "com.example.approval.api..",
    "com.example.file.api..",
    "com.example.integration.api.."
  };

  /**
   * 不变量 1：demo-shared（纯技术底座）零业务服务 api 依赖.
   */
  @Test
  @DisplayName("1. demo-shared 零业务 api 依赖")
  void demoSharedMustNotDependOnBusinessApis() {
    for (String pkg : BUSINESS_API_PACKAGES) {
      noClasses().that().resideInAPackage("com.example.shared..")
        .should().dependOnClassesThat().resideInAPackage(pkg)
        .because("技术底座不得反向依赖业务契约（" + pkg + "）")
        .check(all);
    }
  }

  /**
   * 不变量 2：shared-security-contract 零业务 api 依赖、零传输层注解.
   */
  @Test
  @DisplayName("2. 安全契约层零业务依赖与传输注解")
  void securityContractMustBePure() {
    for (String pkg : BUSINESS_API_PACKAGES) {
      noClasses().that().resideInAPackage("com.example.shared.security..")
        .should().dependOnClassesThat().resideInAPackage(pkg)
        .because("安全契约必须保持纯净（" + pkg + "）")
        .check(all);
    }
    noClasses().that().resideInAPackage("com.example.shared.security..")
      .should().beAnnotatedWith("org.springframework.web.service.annotation.HttpExchange")
      .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RequestMapping")
      .because("契约层不得携带传输层注解（传输关注点归 auth-api 客户端子接口）")
      .check(all);
  }

  /**
   * 不变量 3：gateway-framework 零业务 artifact 依赖（网关壳 channel-gateway
   * 作为接入层允许依赖 auth-api 供 @EnableExchangeClients 扫描）.
   */
  @Test
  @DisplayName("3. 网关框架零业务 api 依赖")
  void gatewayFrameworkMustNotDependOnBusinessApis() {
    for (String pkg : BUSINESS_API_PACKAGES) {
      noClasses().that().resideInAPackage("com.example.gateway..")
        .should().dependOnClassesThat().resideInAPackage(pkg)
        .because("网关框架不得依赖业务契约（" + pkg + "）")
        .check(all);
    }
  }

  /**
   * 不变量 4/5：SessionClaims 与 ChannelType 只允许在契约层定义.
   */
  @Test
  @DisplayName("4/5. 会话声明与渠道枚举唯一出处")
  void sessionClaimsAndChannelTypeMustHaveSingleDefinition() {
    classes().that().haveSimpleName("SessionClaims")
      .should().resideInAPackage("com.example.shared.security")
      .because("SessionClaims 只允许在 shared-security-contract 定义（防止契约分裂）")
      .check(all);
    classes().that().haveSimpleName("ChannelType")
      .should().resideInAPackage("com.example.shared.security")
      .because("ChannelType 只允许在 shared-security-contract 定义（防止渠道枚举漂移）")
      .check(all);
  }

  /**
   * 不变量 6：认证代码（sa-token）只允许出现在网关框架与 auth-service.
   */
  @Test
  @DisplayName("6. sa-token 认证代码位置受限")
  void saTokenUsageMustBeConfined() {
    noClasses().that().resideInAPackage("com.example..")
      .and().resideOutsideOfPackage("com.example.gateway..")
      .and().resideOutsideOfPackage("com.example.auth..")
      .and().resideOutsideOfPackage("com.example.shared.security..") // 契约测试类无 sa-token，防御性放行
      .should().dependOnClassesThat().resideInAPackage("cn.dev33.satoken..")
      .because("认证职责只在网关与 auth-service，其余服务消费已签名声明")
      .check(all);
  }

  /**
   * 不变量 7：BFF 层零业务规则（不得触及 kernel 的 application/domain/infrastructure）.
   */
  @Test
  @DisplayName("7. BFF 零业务规则")
  void bffMustNotContainBusinessRules() {
    noClasses().that().resideInAPackage("com.example.bff..")
      .should().dependOnClassesThat().resideInAPackage("com.example.core.application..")
      .orShould().dependOnClassesThat().resideInAPackage("com.example.core.domain..")
      .orShould().dependOnClassesThat().resideInAPackage("com.example.core.infrastructure..")
      .because("BFF 只做渠道适配与聚合编排（kernel 契约 DTO 除外），业务规则在领域层")
      .check(all);
  }

  /**
   * 不变量 8：共享基础设施门面强制——业务不得绕过 IdService / ICacheTemplate 门面自建.
   *
   * <p>背景（2026-11-04/05 共享能力收编治理的机器固化）：审计发现 approval 全线
   * currentTimeMillis 造 ID、auth 自建 Redis 缓存轮子、file 直调 UlidAlgorithm——
   * 收编完成后本条防止回潮。三条子规则分别锁定 id / 分布式缓存 / 本地缓存门面.
   */
  @Test
  @DisplayName("8. 共享能力门面强制（id/cache 禁直连）")
  void sharedInfrastructureFacadeMustBeEnforced() {
    // 8a ID：业务禁直调 id-core 算法内部（绕过策略注册、启动校验与格式化）
    noClasses().that().resideInAPackage("com.example..")
      .and().resideOutsideOfPackage("com.example.shared..")
      .should().dependOnClassesThat().resideInAPackage("com.example.shared.id.algorithm..")
      .because("业务 ID 一律经 IdService 门面（@IdDefinition 策略可换、启动校验兜底）；"
        + "直调算法内部（如 UlidAlgorithm）在换策略时无法平滑迁移")
      .check(all);

    // 8b 分布式缓存：业务禁直摸 RedisTemplate（白名单为 Redis 原生语义，非缓存职责）
    noClasses().that().resideInAPackage("com.example..")
      .and().resideOutsideOfPackage("com.example.shared..")
      .and().doNotHaveFullyQualifiedName(
        "com.example.auth.infrastructure.channel.repository.SessionRepositoryImpl")
      .and().doNotHaveFullyQualifiedName(
        "com.example.auth.infrastructure.permission.cache.PermissionInvalidationListener")
      .and().doNotHaveFullyQualifiedName(
        "com.example.file.infrastructure.storage.RedisFileTokenStore")
      .and().doNotHaveFullyQualifiedName(
        "com.example.file.infrastructure.storage.RedisFileTokenStoreAutoConfiguration")
      .should().dependOnClassesThat().belongToAnyOf(
        org.springframework.data.redis.core.RedisTemplate.class,
        org.springframework.data.redis.core.StringRedisTemplate.class)
      .because("缓存一律经 ICacheTemplate / 领域缓存 SPI（两级缓存、防击穿、广播失效）；"
        + "白名单为 Redis 原生语义——SessionRepositoryImpl 是会话持久化 Repository、"
        + "PermissionInvalidationListener 是 pub/sub 失效广播、"
        + "RedisFileTokenStore 是 SETNX 一次性令牌原语（2026-11-05 客户端统一治理："
        + "Redisson RBucket 换 StringRedisTemplate 等价实现），复审时须逐一确认")
      .check(all);

    // 8c 本地缓存：业务禁自建 Caffeine（白名单：MenuNodeCache 进程内目录缓存）
    noClasses().that().resideInAPackage("com.example..")
      .and().resideOutsideOfPackage("com.example.shared..")
      .and().doNotHaveFullyQualifiedName(
        "com.example.auth.application.permission.MenuNodeCache")
      .should().dependOnClassesThat().belongToAnyOf(
        com.github.benmanes.caffeine.cache.Caffeine.class,
        com.github.benmanes.caffeine.cache.Cache.class)
      .because("本地缓存经统一缓存端口（L1_ONLY 模式）；MenuNodeCache 为进程内目录缓存白名单"
        + "（枚举键 + 泛型列表值类型），新增本地缓存须走端口或修订白名单理由")
      .check(all);
  }
}
