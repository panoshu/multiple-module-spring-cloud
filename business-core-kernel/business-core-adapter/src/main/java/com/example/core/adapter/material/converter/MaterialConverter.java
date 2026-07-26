package com.example.core.adapter.material.converter;

import com.example.core.api.material.response.MaterialItemResponse;
import com.example.core.domain.business.aggregate.valueobject.MaterialItem;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 材料 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根值对象到响应 DTO 的转换。
 * 使用 default 方法,因 {@link MaterialItem} 是 record 类型,
 * 部分嵌套字段需要手动展开。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface MaterialConverter {

    /**
     * 材料项 → 响应 DTO
     */
    default MaterialItemResponse toResponse(MaterialItem item) {
        if (item == null) {
            return null;
        }
        return new MaterialItemResponse(
            item.materialCode(),
            item.materialName(),
            item.level() != null ? item.level().name() : null,
            item.requirement() != null ? item.requirement().name() : null,
            item.conditionRule(),
            item.uploadInfo().map(info -> info.uploadedAt()).orElse(null),
            item.uploadInfo()
                .map(info -> info.files().stream()
                    .map(f -> f.fileId().value())
                    .toList())
                .orElse(List.of())
        );
    }

    /**
     * 材料项列表 → 响应 DTO 列表
     */
    default List<MaterialItemResponse> toResponseList(List<MaterialItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
            .map(this::toResponse)
            .toList();
    }
}
