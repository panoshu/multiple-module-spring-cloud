package com.example.auth.adapter.converter;

import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.dto.PermissionResponse;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限元数据 Adapter 层转换器.
 *
 * <p>负责领域对象 {@link PermissionItem}、{@link Permission} 与 API 响应 DTO 之间的转换。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PermissionMetadataConverter {

  public PermissionItemResponse toResponse(PermissionItem item) {
    if (item == null) {
      return null;
    }
    return new PermissionItemResponse(
      item.businessCode().value(),
      item.actionCode() != null ? item.actionCode().value() : null,
      item.category().name(),
      item.displayName(),
      item.description(),
      item.categoryGroup(),
      item.sortOrder());
  }

  public List<PermissionItemResponse> toResponseList(List<PermissionItem> items) {
    return items.stream().map(this::toResponse).toList();
  }

  public List<PermissionGroupResponse> toGroupedResponse(Map<String, List<PermissionItem>> grouped) {
    return grouped.entrySet().stream()
      .map(e -> new PermissionGroupResponse(e.getKey(), toResponseList(e.getValue())))
      .toList();
  }

  public PermissionResponse toPermissionResponse(Permission perm) {
    if (perm == null) {
      return null;
    }
    return new PermissionResponse(perm.businessCode().value(),
      perm.actionCode() != null ? perm.actionCode().value() : null);
  }

  public Set<PermissionResponse> toPermissionResponseSet(Set<Permission> permissions) {
    return permissions.stream().map(this::toPermissionResponse).collect(Collectors.toSet());
  }
}
