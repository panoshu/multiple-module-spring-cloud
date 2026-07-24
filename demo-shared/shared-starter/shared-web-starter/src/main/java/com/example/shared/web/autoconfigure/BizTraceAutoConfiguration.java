package com.example.shared.web.autoconfigure;

import com.example.shared.web.trace.aspect.BizContextAspect;
import com.example.shared.web.trace.aspect.ExceptionTraceAspect;
import com.example.shared.web.trace.config.TraceContextProperties;
import com.example.shared.web.trace.filter.BizContextPropagationFilter;
import com.example.shared.web.trace.impl.MdcBizContextAccessor;
import com.example.shared.web.trace.integration.FeignTraceInterceptor;
import com.example.shared.web.trace.integration.OkHttpTraceInterceptor;
import com.example.shared.web.trace.integration.SpringWebTraceInterceptor;
import com.example.shared.web.trace.spi.BizContextAccessor;
import com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 业务链路追踪自动装配类
 */
@Configuration
@EnableConfigurationProperties(TraceContextProperties.class)
public class BizTraceAutoConfiguration {

  // 1. 基础 SPI 实现 (纯 MDC)
  @Bean
  @ConditionalOnMissingBean
  public BizContextAccessor bizContextAccessor() {
    return new MdcBizContextAccessor();
  }

  // 2. Web 环境基础设施 (Filter + Aspect)
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  static class WebMvcConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BizContextPropagationFilter bizContextPropagationFilter(
      TraceContextProperties properties,
      BizContextAccessor accessor) {
      return new BizContextPropagationFilter(properties, accessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public BizContextAspect bizContextAspect(
      TraceContextProperties properties,
      BizContextAccessor accessor) {
      return new BizContextAspect(properties, accessor);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExceptionTraceAspect exceptionTraceAspect(BizContextAccessor accessor) {
      return new ExceptionTraceAspect(accessor);
    }
  }

  // 3. Feign 支持 (按需加载)
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RequestInterceptor.class)
  static class FeignConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public FeignTraceInterceptor feignTraceInterceptor(TraceContextProperties properties) {
      return new FeignTraceInterceptor(properties);
    }
  }

  // 4. Retrofit/OkHttp 支持 (按需加载)
  // 假设你有对应的适配器类 RetrofitTracingInterceptorAdapter
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor")
  static class RetrofitConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public GlobalInterceptor retrofitTraceInterceptor(TraceContextProperties properties) {
      // 这里复用 OkHttpTraceInterceptor
      return chain -> new OkHttpTraceInterceptor(properties).intercept(chain);
    }
  }

  // 5. Spring Web Client 支持 (RestClient & RestTemplate)
  @Configuration(proxyBeanMethods = false)
  // 只有当 classpath 下有 ClientHttpRequestInterceptor 接口时才加载 (Spring Web 基础包)
  @ConditionalOnClass(org.springframework.http.client.ClientHttpRequestInterceptor.class)
  static class SpringWebClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringWebTraceInterceptor springWebTraceInterceptor(TraceContextProperties properties) {
      return new SpringWebTraceInterceptor(properties);
    }

    // 5.1 RestClient 支持 (Spring 6.1+ / Boot 3.2+)
    @Bean
    @ConditionalOnClass(RestClient.class) // 只有存在 RestClient 类时才注册 Customizer
    @ConditionalOnMissingBean(name = "traceRestClientCustomizer")
    public RestClientCustomizer traceRestClientCustomizer(SpringWebTraceInterceptor interceptor) {
      return builder -> builder.requestInterceptor(interceptor);
    }

    // 5.2 RestTemplate 支持
    @Bean
    @ConditionalOnClass(RestTemplate.class) // 只有存在 RestTemplate 类时才注册 Customizer
    @ConditionalOnMissingBean(name = "traceRestTemplateCustomizer")
    public RestTemplateCustomizer traceRestTemplateCustomizer(SpringWebTraceInterceptor interceptor) {
      return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
  }
}
