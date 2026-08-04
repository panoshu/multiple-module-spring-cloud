package com.example.file.application.service;

import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.shared.identifier.id.FileId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件访问审计流水写入器
 * <p>
 * 抽取为独立 Spring Bean，使其上的 {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}
 * 注解能够通过 Spring AOP 代理生效。UseCase 内部以 {@code this.xxx()} 方式自调用时不会经过代理，
 * 导致 REQUIRES_NEW 失效；将写入逻辑移至本 Bean 后，UseCase 通过构造注入并调用本 Bean 的方法，
 * 代理可正常拦截并开启新事务。
 * <p>
 * 参考项目既有实现：{@code StepAutoAdvanceListener} 中的 REQUIRES_NEW 即标注在独立 @Component 上。
 */
@Component
@RequiredArgsConstructor
public class FileAccessLogWriter {

  private final FileAccessLogRepository logRepository;

  /**
   * 写入 ACCESS 成功流水（独立新事务，不受调用方事务回滚影响）。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeAccessLogSuccess(FileMetadata meta, SessionUser session, String clientIp, String token) {
    FileAccessLog accessLog = FileAccessLog.access(
      meta.id(), meta.usage(), meta.accessScope(), session.userNo(),
      meta.sourceApp(), clientIp, TokenHashUtil.sha256(token),
      FileAccessResult.SUCCESS, null
    );
    logRepository.save(accessLog);
  }

  /**
   * 写入 ACCESS 失败流水（独立新事务，不受调用方事务回滚影响）。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeAccessLogFailed(FileId fileId, FileTokenPayload payload, SessionUser session,
                                   String clientIp, String token, String reason) {
    FileAccessScope scope = new FileAccessScope(session.customerNo(), session.productNo());
    FileAccessLog accessLog = FileAccessLog.access(
      fileId, payload.usage(), scope, session.userNo(),
      "unknown", clientIp, TokenHashUtil.sha256(token),
      FileAccessResult.FAIL, reason
    );
    logRepository.save(accessLog);
  }
}
