package com.example.share.logging.aspect;

import com.example.share.logging.annotation.WithDurationLogging;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * MethodTimingAspect
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/23 22:43
 */
@Slf4j
@Aspect
public class MethodTimingAspect {

  @Around("@annotation(com.example.share.logging.annotation.WithDurationLogging)")
  public Object logMethodDuration(ProceedingJoinPoint joinPoint) throws Throwable {
    long startTime = System.nanoTime();
    boolean isSuccess = true;

    try {
      return joinPoint.proceed();
    } catch (Throwable t) {
      isSuccess = false;
      throw t; // 异常必须抛出，否则业务层捕获不到
    } finally {
      long durationNanos = System.nanoTime() - startTime;

      // 获取注解上的配置
      WithDurationLogging annotation = getAnnotation(joinPoint);
      if (annotation != null) {
        logDuration(joinPoint, durationNanos, isSuccess, annotation);
      }
    }
  }

  private void logDuration(ProceedingJoinPoint joinPoint, long durationNanos, boolean isSuccess, WithDurationLogging annotation) {
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);

    // [Fix 3] 阈值过滤：如果配置了阈值且未超时，直接返回
    if (annotation.threshold() > 0 && durationMillis < annotation.threshold()) {
      return;
    }

    // [Fix 2] 精度优化：小于 1ms 显示微秒，否则显示毫秒
    String timeDisplay;
    if (durationMillis < 1) {
      timeDisplay = (durationNanos / 1000) + "µs";
    } else {
      timeDisplay = durationMillis + "ms";
    }

    String methodName = joinPoint.getSignature().toShortString();
    String status = isSuccess ? "Y" : "N"; // 标记是否发生异常

    // [Fix 3] 动态日志级别
    String logContent = "Method: {}, Cost: {}, Success: {}";
    if ("WARN".equalsIgnoreCase(annotation.level())) {
      log.warn(logContent, methodName, timeDisplay, status);
    } else if ("DEBUG".equalsIgnoreCase(annotation.level())) {
      log.debug(logContent, methodName, timeDisplay, status);
    } else {
      log.info(logContent, methodName, timeDisplay, status);
    }
  }

  private WithDurationLogging getAnnotation(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    return method.getAnnotation(WithDurationLogging.class);
  }
}
