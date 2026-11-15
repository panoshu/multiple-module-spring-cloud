package com.example.auth.application.assignment;

import com.example.auth.domain.assignment.aggregate.AgentIdentityAssignment;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 「角色→用户」查询应用服务（批次 59）。
 * <p>
 * 只做编排与分组，不含权限判断；PLAN 精确 scope 过滤由仓储完成。
 * 返回按角色分组、去重且保序的用户编号，供 Controller 包装为 API 响应。
 *
 * @author auth-application
 */
@Service
@RequiredArgsConstructor
public class RoleUserQueryService {

  private final AssignmentRepository assignmentRepository;

  /**
   * 按角色批量查询指定计划内的生效用户。
   *
   * @param roleCodes 角色编码列表
   * @param planNo    计划号
   * @return 角色编码 → 用户编号列表（无匹配时返回空 Map）
   */
  public Map<String, List<String>> listByRoles(List<String> roleCodes, String planNo) {
    if (roleCodes == null || roleCodes.isEmpty()) {
      return Map.of();
    }

    Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
    for (AgentIdentityAssignment assignment :
      assignmentRepository.findActiveByRoleCodesAndPlan(roleCodes, planNo)) {
      grouped.computeIfAbsent(assignment.roleCode().value(), key -> new LinkedHashSet<>())
        .add(assignment.userNo().value());
    }

    return grouped.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey,
        entry -> List.copyOf(entry.getValue()),
        (left, right) -> left,
        LinkedHashMap::new));
  }
}
