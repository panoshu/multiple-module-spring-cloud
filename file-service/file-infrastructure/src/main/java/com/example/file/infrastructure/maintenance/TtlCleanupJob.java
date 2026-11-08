package com.example.file.infrastructure.maintenance;

import com.example.file.domain.repository.SubTaskDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据暂存区 TTL 清理任务（2026-11-08 批次5 E1——设计承诺兑现：file 是"临时工"）.
 *
 * <p><b>设计文档 §一</b>："引擎是临时工，通过 TTL 机制自动清理过期数据"——
 * 此前承诺未兑现（暂存数据无限膨胀），本任务落地.</p>
 *
 * <p><b>品类差异化 TTL</b>（设计决策 D3）：FORM 数据默认 30 天（SubTaskData 的
 * expiresAt 在创建时写入）；MATERIAL 随业务归档不自动清理；EXPORT 由
 * 存储后端生命周期管理——本任务当前覆盖 FORM 暂存数据（SubTaskData 状态
 * 过期标记；物理清理由归档策略另行管理）.</p>
 *
 * <p><b>启停</b>：{@code file.ttl.cleanup-enabled=true}（默认开）；间隔
 * {@code file.ttl.cleanup-interval}（默认 1 小时）.</p>
 *
 * <p><b>认领式扩展</b>：多实例部署时本实现为幂等 UPDATE（状态机推进式），
 * 天然多实例安全；物理删除场景需升级为 SELECT FOR UPDATE SKIP LOCKED 认领.</p>
 *
 * @author file-service
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "file.ttl", name = "cleanup-enabled", havingValue = "true",
  matchIfMissing = true)
public class TtlCleanupJob {

  private final SubTaskDataRepository subTaskRepository;

  public TtlCleanupJob(SubTaskDataRepository subTaskRepository) {
    this.subTaskRepository = subTaskRepository;
  }

  @Scheduled(fixedDelayString = "${file.ttl.cleanup-interval:PT1H}",
    initialDelayString = "${file.ttl.cleanup-initial-delay:PT5M}")
  public void cleanupExpiredSubTasks() {
    try {
      subTaskRepository.markExpiredBefore(java.time.LocalDateTime.now());
      log.info("[TtlCleanupJob] 子任务过期标记完成");
    } catch (Exception e) {
      log.error("[TtlCleanupJob] TTL 清理失败（下轮重试）", e);
    }
  }
}
