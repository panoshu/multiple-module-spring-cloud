package com.example.shared.client.autoconfigure;

import com.github.lianjiatech.retrofit.spring.boot.interceptor.NetworkInterceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.okhttp.LogbookInterceptor;

import java.io.IOException;

@Configuration
@ConditionalOnClass(NetworkInterceptor.class)
public class RetrofitLoggingConfig {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(Logbook.class)
  public LogBookNetworkInterceptor logBookNetworkInterceptor(Logbook logbook) {
    return new LogBookNetworkInterceptor(logbook);
  }

  public static class LogBookNetworkInterceptor implements NetworkInterceptor {
    private final LogbookInterceptor delegate;

    public LogBookNetworkInterceptor(Logbook logbook) {
      this.delegate = new LogbookInterceptor(logbook);
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
      return delegate.intercept(chain);
    }
  }
}
