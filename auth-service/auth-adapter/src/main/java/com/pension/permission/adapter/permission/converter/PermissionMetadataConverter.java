package com.pension.permission.adapter.permission.converter;

import com.pension.permission.api.dto.PermissionGroupResponse;
import com.pension.permission.api.dto.PermissionItemResponse;
import com.pension.permission.api.dto.PermissionResponse;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
      item.source().name(),
      item.controller(), item.method(), item.httpMethod(), item.path(),
      item.displayName(), item.description(), item.categoryGroup(), item.sortOrder());
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
