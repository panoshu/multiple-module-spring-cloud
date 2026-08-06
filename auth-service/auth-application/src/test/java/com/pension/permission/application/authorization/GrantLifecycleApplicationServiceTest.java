package com.pension.permission.application.authorization;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.types.GrantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GrantLifecycleApplicationService 测试")
@ExtendWith(MockitoExtension.class)
class GrantLifecycleApplicationServiceTest {

  @Mock
  private GrantRepository grantRepository;

  @Mock
  private EventBus eventBus;

  @InjectMocks
  private GrantLifecycleApplicationService service;

  private Grant stubGrantLoaded(GrantId grantId, DomainEvent event) {
    Grant grant = mock(Grant.class);
    when(grantRepository.load(grantId)).thenReturn(Optional.of(grant));
    when(grant.domainEvents()).thenReturn(List.of(event));
    return grant;
  }

  @Nested
  @DisplayName("approve: 审批通过")
  class ApproveTest {

    @Test
    @DisplayName("应加载 Grant、调用 approve、保存并发布事件")
    void shouldLoadApproveSaveAndPublishEvent() {
      var grantId = new GrantId("grant-1");
      var operator = UserNo.of("admin-1");
      var event = mock(DomainEvent.class);
      var grant = stubGrantLoaded(grantId, event);
      var command = new ApproveGrantCommand(grantId, operator);

      service.approve(command);

      verify(grant).approve(operator);
      verify(grantRepository).save(grant);
      verify(eventBus).publish(event);
    }
  }

  @Nested
  @DisplayName("reject: 驳回")
  class RejectTest {

    @Test
    @DisplayName("应加载 Grant、调用 reject、保存并发布事件")
    void shouldLoadRejectSaveAndPublishEvent() {
      var grantId = new GrantId("grant-1");
      var operator = UserNo.of("admin-1");
      var event = mock(DomainEvent.class);
      var grant = stubGrantLoaded(grantId, event);
      var command = new RejectGrantCommand(grantId, operator);

      service.reject(command);

      verify(grant).reject(operator);
      verify(grantRepository).save(grant);
      verify(eventBus).publish(event);
    }
  }

  @Nested
  @DisplayName("revoke: 撤销")
  class RevokeTest {

    @Test
    @DisplayName("应加载 Grant、调用 revoke、保存并发布事件")
    void shouldLoadRevokeSaveAndPublishEvent() {
      var grantId = new GrantId("grant-1");
      var operator = UserNo.of("admin-1");
      var event = mock(DomainEvent.class);
      var grant = stubGrantLoaded(grantId, event);
      var command = new RevokeGrantCommand(grantId, operator);

      service.revoke(command);

      verify(grant).revoke(operator);
      verify(grantRepository).save(grant);
      verify(eventBus).publish(event);
    }
  }

  @Nested
  @DisplayName("异常场景")
  class NotFoundTest {

    @Test
    @DisplayName("Grant 不存在时应抛 IllegalArgumentException 且不保存不发布")
    void shouldThrowWhenGrantNotFound() {
      var grantId = new GrantId("grant-1");
      when(grantRepository.load(grantId)).thenReturn(Optional.empty());
      var command = new ApproveGrantCommand(grantId, UserNo.of("admin-1"));

      assertThatThrownBy(() -> service.approve(command))
        .isInstanceOf(IllegalArgumentException.class);

      verify(grantRepository, never()).save(any());
      verify(eventBus, never()).publish(any());
    }
  }
}
