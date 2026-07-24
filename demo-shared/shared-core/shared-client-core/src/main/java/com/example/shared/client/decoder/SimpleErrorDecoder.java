package com.example.shared.client.decoder;


import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import com.github.lianjiatech.retrofit.spring.boot.core.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * 共享错误解码器
 * 统一处理 HTTP 错误响应、IO异常、业务异常
 */
@Slf4j
public class SimpleErrorDecoder implements ErrorDecoder {

  @Override
  public RuntimeException invalidRespDecode(Request request, Response response) {
    if (!response.isSuccessful()) {
      return processFailResponse(request, response);
    } else {
      return null;
    }

  }

  @Override
  public RuntimeException ioExceptionDecode(Request request, IOException cause) {
    // 3. IO 异常（超时、连接拒绝、DNS失败）归类为“网络错误”
    // cause.getMessage() 可能包含 EnhancedErrorInterceptor 拼装的详细信息
    return new SystemException(CommonError.NETWORK_ERROR, cause)
      .withLogDetail("Network IO Error: %s %s".formatted(cause.getMessage(), request.url()));
  }

  @Override
  public RuntimeException exceptionDecode(Request request, Exception cause) {
    // 4. 其他未知异常
    return new SystemException(CommonError.UNKNOWN_ERROR, cause)
      .withLogDetail("Host: %s".formatted(request.url()));
  }

  private RuntimeException processFailResponse(Request request, Response response) {
    String serviceName = request.url().host();
    int code = response.code();

    // 1. 优先处理 404，通常意味着路径错误或资源不存在
    if (code == HttpStatus.NOT_FOUND.value()) {
      // detail: 放入具体的 path，用户只看 "资源不存在"
      return new SystemException(CommonError.NOT_FOUND).withLogDetail("Service [%s] 404 Not Found: %s".formatted(serviceName, request.url().encodedPath()));
    }

    // 读取响应体以获取更多错误细节（如上游的具体报错信息）
    String body = safeReadBody(response);

    // 2. 其他错误码 (500, 400等) 归类为“外部调用错误”
    // 错误信息格式：[服务名] [状态码] [响应体截断] - 外部调用错误
    // 关键点：将 body 和 url 放入 internalDetail (最后一个参数)
    // 前端显示：SystemCode.REMOTE_SERVICE_ERROR 的 message ("外部服务调用异常")
    // 后端日志：SystemException... [Detail: Service [trade-service] responded 500. Body: {"code":"fail"}]
    return new SystemException(CommonError.REMOTE_SERVICE_ERROR).withLogDetail("Service [%s] responded %s. Body: %s".formatted(serviceName, code, body));
  }

  /**
   * 安全读取 Body，避免影响流或抛出新异常
   */
  private String safeReadBody(Response response) {
    try {
      if (response.body() != null) {
        // string() 会自动根据 Content-Type 处理字符集，并关闭流
        return response.body().string();
      }
    } catch (Exception e) {
      log.warn("SimpleErrorDecoder: Failed to read response body", e);
      return "无法读取响应体";
    }
    return "";
  }
}
