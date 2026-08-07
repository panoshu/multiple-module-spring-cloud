package com.example.auth.api.command;

import com.example.auth.api.dto.PermissionItemDescriptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 权限点上报请求.
 *
 * @param sourceService 来源服务名（如 annuity-service）
 * @param items          权限点描述符列表
 * @author auth-api
 */
public record PermissionRegistrationRequest(
    @NotBlank String sourceService,
    @NotEmpty @Valid List<PermissionItemDescriptor> items) {}
