package com.example.iam.application.service;

import com.example.iam.api.dto.LoginLogDTO;
import com.example.iam.api.query.ListLoginLogsQuery;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link LoginLogAppService} 单元测试。
 *
 * <p>LoginLogAppService 为只读查询服务,封装 loadAll + 内存过滤 + 分页切片逻辑。
 * 本测试覆盖过滤条件(userId/channelType/success)与分页切片的关键行为。
 *
 * @author iam-service
 */
@DisplayName("登录日志查询应用服务测试")
@ExtendWith(MockitoExtension.class)
class LoginLogAppServiceTest {

  private static final Long USER_ID = 7001L;
  private static final String LOGIN_NAME = "user001";
  private static final UserNo OPERATOR = UserNo.of("admin");

  @Mock private LoginLogRepository loginLogRepository;

  @InjectMocks
  private LoginLogAppService loginLogAppService;

  @Nested
  @DisplayName("list 分页查询")
  class ListTest {

    @Test
    @DisplayName("无过滤条件时返回全部日志并按时间倒序分页")
    void should_return_all_logs_when_no_filter() {
      LoginLog log1 = buildSuccessLog(7001L, "user001", ChannelType.INTERNET,
          LocalDateTime.of(2026, 7, 26, 10, 0));
      LoginLog log2 = buildSuccessLog(7002L, "user002", ChannelType.BRANCH,
          LocalDateTime.of(2026, 7, 26, 11, 0));
      when(loginLogRepository.loadAll()).thenReturn(List.of(log1, log2));
      ListLoginLogsQuery query = new ListLoginLogsQuery(
          null, null, null, null, null, null, PageQuery.firstPage(10));

      PageData<LoginLogDTO> page = loginLogAppService.list(query);

      assertThat(page.totalCount()).isEqualTo(2);
      assertThat(page.items()).hasSize(2);
      assertThat(page.items().get(0).userId()).isEqualTo(7002L);
      assertThat(page.items().get(1).userId()).isEqualTo(7001L);
    }

    @Test
    @DisplayName("按 userId 与 success 过滤返回匹配日志")
    void should_filter_by_userId_and_success() {
      LoginLog successLog = buildSuccessLog(USER_ID, LOGIN_NAME, ChannelType.INTERNET,
          LocalDateTime.now());
      LoginLog failureLog = buildFailureLog(USER_ID, LOGIN_NAME, ChannelType.INTERNET,
          LocalDateTime.now());
      when(loginLogRepository.loadAll()).thenReturn(List.of(successLog, failureLog));
      ListLoginLogsQuery query = new ListLoginLogsQuery(
          USER_ID, null, null, null, null, Boolean.TRUE, PageQuery.firstPage(10));

      PageData<LoginLogDTO> page = loginLogAppService.list(query);

      assertThat(page.totalCount()).isEqualTo(1);
      assertThat(page.items()).hasSize(1);
      assertThat(page.items().get(0).success()).isTrue();
    }

    @Test
    @DisplayName("无效渠道类型过滤条件被忽略返回空结果")
    void should_return_empty_when_channelType_invalid() {
      LoginLog log = buildSuccessLog(USER_ID, LOGIN_NAME, ChannelType.INTERNET,
          LocalDateTime.now());
      when(loginLogRepository.loadAll()).thenReturn(List.of(log));
      ListLoginLogsQuery query = new ListLoginLogsQuery(
          null, null, "INVALID_CHANNEL", null, null, null, PageQuery.firstPage(10));

      PageData<LoginLogDTO> page = loginLogAppService.list(query);

      assertThat(page.totalCount()).isZero();
      assertThat(page.items()).isEmpty();
    }
  }

  private LoginLog buildSuccessLog(Long userId, String loginName, ChannelType channelType,
                                   LocalDateTime loginTime) {
    return LoginLog.createSuccess(
        com.example.iam.types.LoginLogId.of(userId * 10),
        userId, loginName, channelType, loginTime,
        "127.0.0.1", "Mozilla/5.0", OPERATOR);
  }

  private LoginLog buildFailureLog(Long userId, String loginName, ChannelType channelType,
                                   LocalDateTime loginTime) {
    return LoginLog.createFailure(
        com.example.iam.types.LoginLogId.of(userId * 10 + 1),
        userId, loginName, channelType, loginTime,
        "127.0.0.1", "Mozilla/5.0",
        com.example.iam.types.LoginFailureRecordId.of(userId * 100 + 1),
        "WRONG_PASSWORD", "密码错误", OPERATOR);
  }
}
