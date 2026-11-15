package com.example.core.starter;

import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.infrastructure.business.converter.BusinessApplicationConverter;
import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.converter.BusinessFormConverter;
import com.example.core.infrastructure.business.converter.ExternalInteractionConverter;
import com.example.core.infrastructure.business.converter.StepExecutionConverter;
import com.example.core.infrastructure.business.mapper.BusinessApplicationMapper;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.core.infrastructure.business.mapper.BusinessFormMapper;
import com.example.core.infrastructure.business.mapper.ExternalInteractionMapper;
import com.example.core.infrastructure.business.mapper.StepExecutionMapper;
import com.example.core.infrastructure.business.repository.BusinessApplicationRepositoryImpl;
import com.example.core.infrastructure.business.repository.BusinessBatchRepositoryImpl;
import com.example.core.infrastructure.business.repository.BusinessFormRepositoryImpl;
import com.example.core.infrastructure.business.repository.ExternalInteractionRepositoryImpl;
import com.example.core.infrastructure.business.repository.StepExecutionRepositoryImpl;
import com.example.shared.domain.event.EventBus;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * business-core-kernel 宿主装配入口（K-05 / A-04）。
 *
 * <p><b>宿主契约</b>：业务服务只依赖 {@code business-core-starter}，
 * 不再出现：</p>
 * <ul>
 *   <li>{@code @SpringBootApplication(scanBasePackages = "com.example.core")}</li>
 *   <li>{@code @MapperScan("com.example.core.infrastructure.business.mapper")}</li>
 *   <li>为注册 kernel 仓储而复制 {@code extends XxxRepositoryImpl} 的空壳子类</li>
 * </ul>
 *
 * <p>本类承担三项宿主无关的装配职责：</p>
 * <ol>
 *   <li><b>kernel mapper 扫描</b>：kernel 的 MyBatis-Flex Mapper 位于固定包，
 *       由 starter 统一注册；宿主只需扫描自己的 mapper 包。</li>
 *   <li><b>异步能力</b>：kernel 的 {@code ApplicationSpawnedListener} /
 *       {@code StepAutoAdvanceListener} / {@code ExternalInteractionRequestedListener}
 *       依赖 {@code @Async} 才能做到“提交后事务外推进”。@EnableAsync 放在 starter，
 *       避免宿主漏配导致监听器被静默同步执行。</li>
 *   <li><b>kernel 仓储默认装配</b>：五个通用仓储由 starter 直接注册为 Bean，
 *       宿主无需继承实现类；声明同类型 Bean 即可覆盖（{@code @ConditionalOnMissingBean}）。
 *       申请单按文件任务反查会使用宿主提供的 {@link ParsedFileReferenceGateway}。</li>
 * </ol>
 *
 * <p>kernel 的组件/领域服务/自动配置仍由 {@code business-core-adapter} 的
 * {@code CoreKernelAutoConfiguration} 扫描与装配（starter 通过依赖传递引入）。
 * 本模块是<b>宿主入口</b>，不是第二套实现。</p>
 *
 * <p>可通过 {@code core.kernel.enabled=false} 整体关闭 kernel 自动装配
 * （仅用于不承载流程的业务服务或隔离测试）。</p>
 *
 * @author business-core-kernel
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "core.kernel", name = "enabled",
  havingValue = "true", matchIfMissing = true)
@EnableAsync
@MapperScan("com.example.core.infrastructure.business.mapper")
public class BusinessCoreStarterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApplicationRepository.class)
  public ApplicationRepository businessApplicationRepository(
      EventBus eventBus,
      BusinessApplicationMapper mapper,
      BusinessApplicationConverter converter,
      ObjectProvider<ParsedFileReferenceGateway> parsedFileReferenceGateway) {
    return new BusinessApplicationRepositoryImpl(
      eventBus, mapper, converter, parsedFileReferenceGateway.getIfAvailable());
  }

  @Bean
  @ConditionalOnMissingBean(BatchRepository.class)
  public BatchRepository businessBatchRepository(
      EventBus eventBus,
      BusinessBatchMapper mapper,
      BusinessBatchConverter converter) {
    return new BusinessBatchRepositoryImpl(eventBus, mapper, converter);
  }

  @Bean
  @ConditionalOnMissingBean(FormRepository.class)
  public FormRepository businessFormRepository(
      EventBus eventBus,
      BusinessFormMapper mapper,
      BusinessFormConverter converter) {
    return new BusinessFormRepositoryImpl(eventBus, mapper, converter);
  }

  @Bean
  @ConditionalOnMissingBean(ExternalInteractionRepository.class)
  public ExternalInteractionRepository externalInteractionRepository(
      EventBus eventBus,
      ExternalInteractionMapper mapper,
      ExternalInteractionConverter converter) {
    return new ExternalInteractionRepositoryImpl(eventBus, mapper, converter);
  }

  @Bean
  @ConditionalOnMissingBean(StepExecutionRepository.class)
  public StepExecutionRepository stepExecutionRepository(
      EventBus eventBus,
      StepExecutionMapper mapper,
      StepExecutionConverter converter) {
    return new StepExecutionRepositoryImpl(eventBus, mapper, converter);
  }
}
