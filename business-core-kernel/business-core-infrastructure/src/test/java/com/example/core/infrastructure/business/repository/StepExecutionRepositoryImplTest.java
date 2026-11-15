package com.example.core.infrastructure.business.repository;

import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.infrastructure.business.converter.StepExecutionConverter;
import com.example.core.infrastructure.business.entity.StepExecutionDO;
import com.example.core.infrastructure.business.mapper.StepExecutionMapper;
import com.example.core.types.ApplicationId;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepExecutionRepositoryImpl 持久化契约")
class StepExecutionRepositoryImplTest {

  @Mock
  private StepExecutionMapper mapper;
  @Mock
  private StepExecutionConverter converter;
  @Mock
  private EventBus eventBus;

  private StepExecutionRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = new StepExecutionRepositoryImpl(eventBus, mapper, converter);
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
    StepExecutionDO aDo = new StepExecutionDO();
    StepExecution domain = pendingExecution();
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(aDo);
    when(converter.toDomain(aDo)).thenReturn(domain);

    assertThat(repository.findByRequestNo("REQ-001")).contains(domain);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectOneByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("request_no");
  }

  @Test
  @DisplayName("findByApplicationAndStep 返回该步骤的全部尝试")
  void findByApplicationAndStep_returnsAllAttempts() {
    StepExecutionDO first = new StepExecutionDO();
    StepExecutionDO second = new StepExecutionDO();
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(first, second));
    when(converter.toDomain(first)).thenReturn(pendingExecution());
    when(converter.toDomain(second)).thenReturn(pendingExecution());

    assertThat(repository.findByApplicationAndStep(
      new ApplicationId("APP-001"), StepCode.of("DATA_VERIFICATION"))).hasSize(2);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectListByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("application_id", "step_code");
  }

  @Test
  @DisplayName("findRunningByApplication 按申请单与状态过滤")
  void findRunningByApplication_filtersRunningState() {
    StepExecutionDO aDo = new StepExecutionDO();
    StepExecution domain = pendingExecution();
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(aDo);
    when(converter.toDomain(aDo)).thenReturn(domain);

    assertThat(repository.findRunningByApplication(new ApplicationId("APP-001"))).contains(domain);

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectOneByQuery(captor.capture());
    assertThat(captor.getValue().toSQL()).contains("application_id", "status");
  }

  @Test
  @DisplayName("save 新增时委托 insert 且不发布领域事件")
  void save_insertsWithoutPublishingEvents() {
    StepExecution execution = pendingExecution();
    StepExecutionDO aDo = new StepExecutionDO();
    aDo.setId(9001L);
    when(converter.toDO(execution)).thenReturn(aDo);
    when(mapper.selectOneById(9001L)).thenReturn(null);

    repository.save(execution);

    verify(mapper).insert(aDo);
    verifyNoInteractions(eventBus);
  }

  private StepExecution pendingExecution() {
    return StepExecution.create(
      StepExecutionId.of(9001L),
      new ApplicationId("APP-001"),
      DefinitionVersion.of("v2"),
      StepCode.of("DATA_VERIFICATION"),
      1, "REQ-001", "U-CREATOR");
  }
}
