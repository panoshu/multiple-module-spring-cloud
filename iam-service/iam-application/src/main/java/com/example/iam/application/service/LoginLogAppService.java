package com.example.iam.application.service;

import com.example.iam.api.dto.LoginFailureRecordDTO;
import com.example.iam.api.dto.LoginLogDTO;
import com.example.iam.api.query.ListLoginLogsQuery;
import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 登录日志查询应用服务。
 *
 * <p>负责登录审计日志的分页查询编排,供运维与安全排查使用。
 * 本服务为只读查询服务,不涉及聚合根写入;登录日志的写入由
 * {@link AbstractChannelAuthService} 在登录流程中通过 {@link LoginLogRepository#save} 完成。
 *
 * <p>查询流程:
 * <ol>
 *   <li>通过 {@link LoginLogRepository#loadAll} 加载全部登录日志</li>
 *   <li>按 userId / loginName / channelType / 时间范围 / 成功标志进行内存过滤</li>
 *   <li>按 loginTime 倒序排序,保证最近日志优先展示</li>
 *   <li>分页切片返回</li>
 * </ol>
 *
 * <p>简化实现说明:当前 {@link LoginLogRepository} 未提供通用分页查询方法,
 * 暂以 loadAll + 内存过滤实现;后续 Repository 扩展分页方法后可优化。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogAppService {

  private final LoginLogRepository loginLogRepository;

  /**
   * 登录日志分页查询。
   *
   * @param query 查询条件
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<LoginLogDTO> list(ListLoginLogsQuery query) {
    List<LoginLog> all = loginLogRepository.loadAll();
    List<LoginLog> filtered = all.stream()
        .filter(loginLog -> matchesUserId(loginLog, query.userId()))
        .filter(loginLog -> matchesLoginName(loginLog, query.loginName()))
        .filter(loginLog -> matchesChannelType(loginLog, query.channelType()))
        .filter(loginLog -> matchesTimeRange(loginLog, query.startTime(), query.endTime()))
        .filter(loginLog -> matchesSuccess(loginLog, query.success()))
        .sorted(Comparator.comparing(LoginLog::loginTime, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 列表分页切片。
   */
  private PageData<LoginLogDTO> paginate(List<LoginLog> logs, PageQuery pageQuery) {
    int total = logs.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<LoginLogDTO> items = logs.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesUserId(LoginLog loginLog, Long userId) {
    if (userId == null) {
      return true;
    }
    return Objects.equals(loginLog.userId(), userId);
  }

  private boolean matchesLoginName(LoginLog loginLog, String loginName) {
    if (loginName == null || loginName.isBlank()) {
      return true;
    }
    return loginLog.loginName() != null && loginLog.loginName().contains(loginName);
  }

  private boolean matchesChannelType(LoginLog loginLog, String channelType) {
    if (channelType == null || channelType.isBlank()) {
      return true;
    }
    try {
      ChannelType target = ChannelType.valueOf(channelType);
      return loginLog.channelType() == target;
    } catch (IllegalArgumentException e) {
      log.warn("无效的渠道类型过滤条件,忽略: channelType={}", channelType);
      return false;
    }
  }

  private boolean matchesTimeRange(LoginLog loginLog, LocalDateTime startTime, LocalDateTime endTime) {
    LocalDateTime loginTime = loginLog.loginTime();
    if (loginTime == null) {
      return true;
    }
    if (startTime != null && loginTime.isBefore(startTime)) {
      return false;
    }
    if (endTime != null && loginTime.isAfter(endTime)) {
      return false;
    }
    return true;
  }

  private boolean matchesSuccess(LoginLog loginLog, Boolean success) {
    if (success == null) {
      return true;
    }
    return loginLog.isSuccess() == success;
  }

  /**
   * 领域对象转 DTO。
   */
  private LoginLogDTO toDTO(LoginLog loginLog) {
    List<LoginFailureRecordDTO> failureRecords = loginLog.failureRecords().stream()
        .map(this::toFailureRecordDTO)
        .toList();
    return new LoginLogDTO(
        loginLog.id().value(),
        loginLog.userId(),
        loginLog.loginName(),
        loginLog.channelType() != null ? loginLog.channelType().name() : null,
        loginLog.isSuccess(),
        loginLog.loginTime(),
        loginLog.loginIp(),
        loginLog.userAgent(),
        failureRecords
    );
  }

  private LoginFailureRecordDTO toFailureRecordDTO(LoginFailureRecord record) {
    return new LoginFailureRecordDTO(
        record.id().value(),
        record.reason(),
        record.detail(),
        record.failureTime()
    );
  }
}
