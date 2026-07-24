package com.example.shared.utils.concurrent;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import com.example.shared.utils.function.TriFunction;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * 基于 JDK 25 正式版/最新预览版 API 的虚拟线程执行器
 * * 注意：JDK 24+ 中 ShutdownOnFailure 已通过 StructuredTaskScope.Config
 * 或特定的 open 方法进行初始化。
 */
@Slf4j
public class VirtualThreadExecutor {

  public static final int DEFAULT_CONCURRENCY = 5;

  private VirtualThreadExecutor() {
  }

  // ========================================================================
  // 1. Fire-and-Forget
  // ========================================================================
  public static void executeAsync(Runnable task) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    Thread.startVirtualThread(() -> {
      try (var ignored = MDCContext.of(contextMap)) {
        task.run();
      } catch (Throwable t) {
        log.error("Async task execution failed", t);
      }
    });
  }

  // ========================================================================
  // 2. Structured Concurrency (异构聚合)
  // ========================================================================
  public static <T, U, R> R combine(Callable<T> task1, Callable<U> task2, BiFunction<T, U, R> combiner) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    // JDK 24/25 修正：使用 StructuredTaskScope.open()
    try (var scope = StructuredTaskScope.open()) {
      StructuredTaskScope.Subtask<T> t1 = scope.fork(wrap(task1, contextMap));
      StructuredTaskScope.Subtask<U> t2 = scope.fork(wrap(task2, contextMap));

      scope.join();
      // 在 JDK 24+ 中，检查失败的方式有所变化，通常直接从 subtask 获取或处理异常
      handleSubtaskFailures(List.of(t1, t2));

      return combiner.apply(t1.get(), t2.get());
    } catch (Exception e) {
      throw handleException(e);
    }
  }

  public static <T, U, V, R> R combine(Callable<T> task1, Callable<U> task2, Callable<V> task3, TriFunction<T, U, V, R> combiner) {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    try (var scope = StructuredTaskScope.open()) {
      var t1 = scope.fork(wrap(task1, contextMap));
      var t2 = scope.fork(wrap(task2, contextMap));
      var t3 = scope.fork(wrap(task3, contextMap));

      scope.join();
      handleSubtaskFailures(List.of(t1, t2, t3));

      return combiner.apply(t1.get(), t2.get(), t3.get());
    } catch (Exception e) {
      throw handleException(e);
    }
  }

  // ========================================================================
  // 3. Batch Processing (同构处理)
  // ========================================================================
  public static <T> List<T> all(Collection<Callable<T>> tasks, int maxConcurrency) {
    if (tasks == null || tasks.isEmpty()) {
      return List.of();
    }

    Semaphore limiter = (maxConcurrency < tasks.size()) ? new Semaphore(maxConcurrency) : null;
    Map<String, String> contextMap = MDC.getCopyOfContextMap();

    try (var scope = StructuredTaskScope.open()) {
      List<StructuredTaskScope.Subtask<T>> subtasks = tasks.stream()
        .map(task -> scope.fork(() -> {
          try (var ignored = MDCContext.of(contextMap)) {
            if (limiter != null) {
              limiter.acquire();
            }
            return task.call();
          } finally {
            if (limiter != null) {
              limiter.release();
            }
          }
        }))
        .toList(); // 此时返回类型明确为 List<Subtask<T>>

      scope.join();
      handleSubtaskFailures(subtasks);

      return subtasks.stream()
        .map(StructuredTaskScope.Subtask::get)
        .toList();
    } catch (Exception e) {
      throw handleException(e);
    }
  }

  // ========================================================================
  // 内部辅助逻辑
  // ========================================================================

  /**
   * JDK 24+ 移除了简单的 throwIfFailed，我们需要手动检查 Subtask 状态
   */
  private static void handleSubtaskFailures(List<? extends StructuredTaskScope.Subtask<?>> subtasks) {
    for (var subtask : subtasks) {
      if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
        throw new SystemException(CommonError.CONCURRENCY_ERROR, subtask.exception()).withLogDetail("子任务执行失败");
      }
    }
  }

  private static <T> Callable<T> wrap(Callable<T> task, Map<String, String> contextMap) {
    return () -> {
      try (var ignored = MDCContext.of(contextMap)) {
        return task.call();
      }
    };
  }

  private static RuntimeException handleException(Exception e) {
    return switch (e) {
      case ExecutionException ee when ee.getCause() instanceof RuntimeException re -> re;
      case ExecutionException ee ->
        new SystemException(CommonError.CONCURRENCY_ERROR, ee.getCause()).withLogDetail("并发任务执行失败");

      case InterruptedException ie -> {
        Thread.currentThread().interrupt();
        yield new SystemException(CommonError.UNKNOWN_ERROR, ie).withLogDetail("任务执行被中断");
      }
      case TimeoutException te -> new SystemException(CommonError.TIMEOUT_ERROR, te).withLogDetail("并发任务执行超时");
      default ->
        new SystemException(CommonError.UNKNOWN_ERROR, e).withLogDetail("未知的并发错误: %s".formatted(e.getClass().getName()));
    };
  }

  private record MDCContext(Map<String, String> context) implements AutoCloseable {
    static MDCContext of(Map<String, String> context) {
      if (context != null) {
        MDC.setContextMap(context);
      }
      return new MDCContext(context);
    }

    @Override
    public void close() {
      MDC.clear();
    }
  }


}
