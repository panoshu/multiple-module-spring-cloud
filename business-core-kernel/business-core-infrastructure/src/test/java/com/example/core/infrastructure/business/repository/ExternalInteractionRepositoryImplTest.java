package com.example.core.infrastructure.business.repository;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.shared.domain.event.DomainEvent;
import com.example.core.infrastructure.business.converter.ExternalInteractionConverter;
import com.example.core.infrastructure.business.entity.ExternalInteractionDO;
import com.example.core.infrastructure.business.mapper.ExternalInteractionMapper;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.event.EventBus;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalInteractionRepositoryImpl 持久化契约")
class ExternalInteractionRepositoryImplTest {

  @Mock
  private ExternalInteractionMapper mapper;
  @Mock
  private ExternalInteractionConverter converter;
  @Mock
  private EventBus eventBus;

  private ExternalInteractionRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = new ExternalInteractionRepositoryImpl(eventBus, mapper, converter);
  }

  @Test
  @DisplayName("findByRequestNo 空白入参直接返回空且不查库")
  void findByRequestNo_blankReturnsEmpty() {
    assertThat(repository.findByRequestNo(null)).isEmpty();
    assertThat(repository.findByRequestNo("  ")).isEmpty();
    verifyNoInteractions(mapper);
  }

  @Test
  @DisplayName("findByRequestNo 按唯一请求号查询并转换为领域对象")
  void findByRequestNo_queriesAndConverts() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    ExternalInteraction domain = pendingInteraction();
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(aDo);
    when(converter.toDomain(aDo)).thenReturn(domain);

    assertThat(repository.findByRequestNo("SE-9001")).contains(domain);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectOneByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("request_no");
  }

  @Test
  @DisplayName("findByExternalTaskId 按交互类型与外部任务 ID 联合查询")
  void findByExternalTaskId_queriesByTypeAndTaskId() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    ExternalInteraction domain = pendingInteraction();
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(aDo);
    when(converter.toDomain(aDo)).thenReturn(domain);

    assertThat(repository.findByExternalTaskId(InteractionType.FILE_PARSE, "FT-1")).contains(domain);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectOneByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("interaction_type", "external_task_id");
  }

  @Test
  @DisplayName("findPendingRetry 按状态与下次重试时间扫描")
  void findPendingRetry_filtersByStatusAndRetryTime() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(aDo));
    when(converter.toDomain(aDo)).thenReturn(pendingInteraction());

    assertThat(repository.findPendingRetry(LocalDateTime.of(2026, 11, 12, 10, 0))).hasSize(1);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectListByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("status", "next_retry_time");
  }

  @Test
  @DisplayName("findByApplication 按申请单列出全部外部协作")
  void findByApplication_returnsAllInteractions() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(aDo));
    when(converter.toDomain(aDo)).thenReturn(pendingInteraction());

    assertThat(repository.findByApplication(new ApplicationId("APP-001"))).hasSize(1);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectListByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("application_id");
  }

  @Test
  @DisplayName("findByStatus 按状态扫描待派发/未知协作")
  void findByStatus_queriesByStatus() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(aDo));
    when(converter.toDomain(aDo)).thenReturn(pendingInteraction());

    assertThat(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH)).hasSize(1);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectListByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("status", "PENDING_DISPATCH");
  }

  @Test
  @DisplayName("findStaleDispatching 按状态与更新时间扫描滞留派发")
  void findStaleDispatching_filtersByStatusAndUpdateTime() {
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(aDo));
    when(converter.toDomain(aDo)).thenReturn(pendingInteraction());

    assertThat(repository.findStaleDispatching(LocalDateTime.of(2026, 11, 12, 9, 30))).hasSize(1);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectListByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("status", "DISPATCHING", "update_time");
  }

  @Test
  @DisplayName("save 新增时委托 insert 并发布协作创建事件")
  void save_insertsAndPublishesRequestedEvent() {
    ExternalInteraction interaction = pendingInteraction();
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    aDo.setId(7001L);
    when(converter.toDO(interaction)).thenReturn(aDo);
    when(mapper.selectOneById(7001L)).thenReturn(null);

    repository.save(interaction);

    verify(mapper).insert(aDo);
    verify(eventBus).publish(any(DomainEvent.class));
    assertThat(interaction.domainEvents()).isEmpty();
  }

  private ExternalInteraction pendingInteraction() {
    return ExternalInteraction.create(
      ExternalInteractionId.of(7001L),
      new ApplicationId("APP-001"),
      StepExecutionId.of(9001L),
      InteractionType.FILE_PARSE,
      "U-CREATOR");
  }
}
