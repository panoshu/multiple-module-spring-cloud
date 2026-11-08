package com.example.core.adapter.form.converter;

import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import org.mapstruct.Mapper;

/**
 * 表单 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换。
 * 使用 default 方法,因 {@link BusinessForm} 继承泛型基类,
 * MapStruct @Mapping 无法解析继承的访问器(同 BatchConverter 模式)。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法</li>
 *   <li>复杂字段(如嵌套 List)可添加 default 方法辅助</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface FormConverter {

  /**
   * 上传 token 字符串 → 响应 DTO
   *
   * <p>{@code expireTime}/{@code uploadUrl} 在当前 {@code FileIntegrationGateway}
   * 仅返回 String token 的情况下设为 null,字段保留供后续扩展。
   */
  default UploadTokenResponse toUploadTokenResponse(String token) {
    if (token == null) {
      return null;
    }
    return new UploadTokenResponse(token, null, null);
  }

  /**
   * 表单聚合根 → 状态响应 DTO
   *
   * <p>{@code parseProgress}/{@code applicationCount}/{@code errorMsg} 在当前聚合根中
   * 不可得(需查询解析流水),本次返回 0/null,字段保留供后续扩展。
   */
  default FormStatusResponse toStatusResponse(BusinessForm form) {
    if (form == null) {
      return null;
    }
    return new FormStatusResponse(
      form.id().value(),
      form.formStatus() != null ? form.formStatus().name() : null,
      0,
      0,
      null
    );
  }
}
