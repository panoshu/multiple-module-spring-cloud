package com.example.outbound.dto.logistics;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * SmartRouteCommand
 *
 * @author YourName
 * @since 2025/12/14 22:17
 */
public record SmartRouteCommand(

  @NotBlank(message = "运单号不能为空")
  String trackingNo,

  @NotNull(message = "货物重量不能为空")
  @DecimalMin(value = "0.01", message = "重量必须大于0")
  Double weight,

  @NotBlank(message = "货物类型不能为空")
  String type, // 对应 CargoType 的字符串 (如 "FRESH", "STD")

  boolean needIntercept // 是否需要拦截 (可选参数)

) implements Serializable {}
