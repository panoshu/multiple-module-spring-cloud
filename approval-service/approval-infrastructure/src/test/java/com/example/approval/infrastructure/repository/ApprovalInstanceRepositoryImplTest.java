package com.example.approval.infrastructure.repository;

import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.infrastructure.converter.ApprovalInstanceConverter;
import com.example.approval.infrastructure.mapper.ApprovalInstanceMapper;
import com.example.approval.infrastructure.mapper.ApprovalNodeExecutionMapper;
import com.example.approval.infrastructure.mapper.ApprovalRecordMapper;
import com.example.approval.types.enums.InstanceStatus;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 55（P3-1 能力 C）：{@code findByStatuses} 的 STATUS IN 条件与排序。
 */
@DisplayName("审批实例按状态查询（批次 55）")
class ApprovalInstanceRepositoryImplTest {

  private ApprovalInstanceMapper instanceMapper;
  private ApprovalInstanceRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    instanceMapper = mock(ApprovalInstanceMapper.class);
    repository = new ApprovalInstanceRepositoryImpl(
      instanceMapper,
      mock(ApprovalNodeExecutionMapper.class),
      mock(ApprovalRecordMapper.class),
      mock(ApprovalInstanceConverter.class),
      mock(ApplicationEventPublisher.class));
  }

  @Test
  @DisplayName("状态集合为空时短路，不查询数据库")
  void findByStatuses_emptyStatusesSkipsQuery() {
    List<ApprovalInstance> result = repository.findByStatuses(List.of());

    assertThat(result).isEmpty();
    verify(instanceMapper, never()).selectListByQuery(any());
  }

  @Test
  @DisplayName("STATUS IN 条件与 create_time 降序落到 QueryWrapper")
  void findByStatuses_appliesStatusInAndOrder() {
    when(instanceMapper.selectListByQuery(any())).thenReturn(List.of());

    assertThat(repository.findByStatuses(List.of(InstanceStatus.PENDING, InstanceStatus.APPROVING)))
      .isEmpty();

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(instanceMapper).selectListByQuery(captor.capture());
    String sql = captor.getValue().toSQL().toLowerCase();
    assertThat(sql).contains("status", "in", "create_time");
  }
}
