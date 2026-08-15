package com.example.auth.api.dto;

import java.util.List;

/**
 * 可选计划列表响应.
 *
 * @param plans 可选计划编号列表
 */
public record SelectablePlansResponse(
  List<String> plans
) {
}
