package com.example.shared.client.interceptor;

import com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import retrofit2.Invocation;

import java.io.IOException;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/6 12:18
 */
public class EnhancedErrorInterceptor implements GlobalInterceptor {
  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    try {
      return chain.proceed(request);
    } catch (Exception e) {
      // 获取 Retrofit 注入的方法信息
      var invocation = request.tag(Invocation.class);
      String methodDesc = (invocation != null)
        ? "%s.%s".formatted(invocation.method().getDeclaringClass().getSimpleName(), invocation.method().getName())
        : request.url().toString();

//      throw new IOException(STR."外部调用异常 [\{methodDesc}]: \{request.url()} \{e.getMessage()}", e);
      throw new IOException("外部调用异常 [%s]: %s %s".formatted(methodDesc, request.url().host(), e.getMessage()), e);
    }
  }
}
