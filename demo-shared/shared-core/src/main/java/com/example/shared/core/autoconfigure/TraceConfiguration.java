package com.example.shared.core.autoconfigure;

import com.example.shared.core.trace.aspect.AutoBizTraceAspect;
import com.example.shared.core.trace.concurrency.MdcTaskDecorator;
import com.example.shared.core.trace.filter.TraceClientInterceptor;
import com.example.shared.core.trace.filter.TraceContextFilter;
import com.example.shared.core.trace.properties.TraceProperties;
import jakarta.servlet.Filter;
import okhttp3.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 全链路追踪自动装配配置
 * * 只有当 shared.trace.enable=true (或不配置) 时才生效
 */
@EnableConfigurationProperties(TraceProperties.class)
@ConditionalOnProperty(prefix = "shared.trace", name = "enable", havingValue = "true", matchIfMissing = true)
public class TraceConfiguration {

  // —————— 1. AOP 切面 (负责从 Controller 参数抓取 ID) ——————
  @Bean
  @ConditionalOnMissingBean // 允许用户自定义 Bean 覆盖
  public AutoBizTraceAspect autoBizTraceAspect(TraceProperties properties) {
    return new AutoBizTraceAspect(properties);
  }

  // —————— 2. 线程池装饰器 (负责异步线程的 ID 传递) ——————
  @Bean
  @ConditionalOnMissingBean
  public MdcTaskDecorator mdcTaskDecorator() {
    return new MdcTaskDecorator();
  }

  // TODO: 如果使用了自定义的 Executor，需要像这样配置它：
    /*
    @Bean("applicationTaskExecutor")
    public Executor applicationTaskExecutor(MdcTaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setTaskDecorator(decorator); // 关键：设置装饰器
        executor.initialize();
        return executor;
    }
    */

  // —————— 3. 服务端 Filter (负责 HTTP Header <-> MDC 的透传) ——————
  // 仅在 Servlet Web 环境下生效，且依赖 Servlet API
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(Filter.class)
  @ConditionalOnProperty(prefix = "shared.trace", name = "enable-filter", havingValue = "true", matchIfMissing = true)
  @ConditionalOnMissingBean
  public TraceContextFilter traceContextFilter() {
    return new TraceContextFilter();
  }

  // —————— 4. 客户端 Interceptor (负责 MDC -> HTTP Header 的透传) ——————
  // 仅当 classpath 下有 OkHttp 时才创建 (避免没引依赖报错)
  @Bean
  @ConditionalOnClass(Interceptor.class)
  @ConditionalOnMissingBean
  public TraceClientInterceptor traceClientInterceptor() {
    return new TraceClientInterceptor();
  }
}
