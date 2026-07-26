package com.example.core.api.material.response;

import java.util.List;

/**
 * 材料完整性校验响应
 *
 * @author panoshu
 */
public record CheckCompletenessResponse(
    boolean satisfied,
    List<String> unsatisfiedMaterialCodes
) {
}
