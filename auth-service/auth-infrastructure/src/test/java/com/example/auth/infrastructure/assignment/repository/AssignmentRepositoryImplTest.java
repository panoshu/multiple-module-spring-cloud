package com.example.auth.infrastructure.assignment.repository;

import com.example.auth.infrastructure.assignment.converter.AssignmentConverter;
import com.example.auth.infrastructure.assignment.mapper.AssignmentMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 批次 59：按角色 + PLAN 精确 scope 查询生效分配的仓储契约。 */
@DisplayName("身份分配按角色查询（批次 59）")
class AssignmentRepositoryImplTest {

  private AssignmentMapper assignmentMapper;
  private AssignmentRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    assignmentMapper = mock(AssignmentMapper.class);
    repository = new AssignmentRepositoryImpl(assignmentMapper, mock(AssignmentConverter.class));
  }

  @Test
  @DisplayName("角色列表为空时短路，不查询数据库")
  void findActiveByRoleCodesAndPlan_emptyRoleCodesSkipsQuery() {
    assertThat(repository.findActiveByRoleCodesAndPlan(List.of(), "P-1")).isEmpty();
    verify(assignmentMapper, never()).selectListByQuery(any());
  }

  @Test
  @DisplayName("role_code IN + PLAN scope + ACTIVE + 未删除条件落到 QueryWrapper")
  void findActiveByRoleCodesAndPlan_filtersByRolePlanAndActive() {
    when(assignmentMapper.selectListByQuery(any())).thenReturn(List.of());

    assertThat(repository.findActiveByRoleCodesAndPlan(List.of("R1", "R2"), "P-1")).isEmpty();

    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(assignmentMapper).selectListByQuery(captor.capture());
    String sql = captor.getValue().toSQL().toLowerCase();
    assertThat(sql).contains("role_code", "scope_dimension", "scope_value", "status", "deleted");
  }
}
