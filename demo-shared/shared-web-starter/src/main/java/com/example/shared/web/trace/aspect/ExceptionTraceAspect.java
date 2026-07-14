package com.example.shared.web.trace.aspect;

import com.example.shared.web.trace.spi.BizContextAccessor;
import com.example.shared.web.trace.spi.ThrowableSupplier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;

import static com.example.shared.web.trace.constant.TraceConstants.CONTEXT_BACKUP_KEY;

/**
 * 异常处理上下文恢复切面
 * 作用：拦截 @ExceptionHandler 方法，利用 Request 中备份的数据恢复 MDC，
 * 从而确保 Error Log 能打印出业务 ID。
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE) // 确保最先执行，建立好环境给 ExceptionHandler 用
@RequiredArgsConstructor
public class ExceptionTraceAspect {

  private final BizContextAccessor contextAccessor;

  // 拦截所有类中标记了 @ExceptionHandler 的方法
  @Around("@annotation(org.springframework.web.bind.annotation.ExceptionHandler)")
  public Object restoreContextForHandler(ProceedingJoinPoint joinPoint) throws Throwable {

    // 1. 从 Request 救生艇中捞回数据
    Map<String, String> backupContext = getBackupContext();

    // 2. 重新开启 Scope，并执行原本的 ExceptionHandler
    // 这里复用了 contextAccessor，它会自动处理 MDC.put 和 Scope.close
    return contextAccessor.withContext(backupContext, (ThrowableSupplier<Object>) joinPoint::proceed);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getBackupContext() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      HttpServletRequest request = sra.getRequest();
      Object backup = request.getAttribute(CONTEXT_BACKUP_KEY);
      if (backup instanceof Map) {
        return (Map<String, String>) backup;
      }
    }
    return Collections.emptyMap();
  }
}
