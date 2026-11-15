package com.example.auth.domain.permission;

import com.example.shared.security.permission.PermissionItemDescriptor;

import java.util.List;

/**
 * 外部权限点上报用例端口（AU-04）。
 * <p>由 infrastructure 的扫描服务实现，adapter 仅依赖本端口。
 */
public interface PermissionRegistrationUseCase {

  PermissionRegistrationResult registerFromExternal(
    String sourceService, List<PermissionItemDescriptor> items);
}
