package com.example.core.api.material.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 材料项响应
 *
 * @author panoshu
 */
public record MaterialItemResponse(
    String materialCode,
    String materialName,
    String level,
    String requirement,
    String conditionRule,
    LocalDateTime uploadedAt,
    List<String> fileIds
) {
}
