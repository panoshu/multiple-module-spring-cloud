package com.example.core.api.form;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.command.ConfirmUploadCommand;
import com.example.core.api.form.command.DeleteFormCommand;
import com.example.core.api.form.query.GetFormStatusQuery;
import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;

/**
 * 业务表单管理 API（纯契约接口）
 *
 * <p>定义表单的上传 token 申请、上传确认、删除、状态查询等公共方法签名，不含传输层注解。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在本接口新增方法签名</li>
 *   <li>在各 xxx-api 的子接口中重新声明并标注 {@code @PostExchange}</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller，通过 FormConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
public interface BusinessFormApi {

  /**
   * 申请文件上传临时凭证。
   */
  ApiResult<UploadTokenResponse> applyUploadToken(@Valid ApplyUploadTokenCommand command);

  /**
   * 确认文件已上传(前端直传文件服务后回调)。
   */
  ApiResult<Void> confirmUpload(@Valid ConfirmUploadCommand command);

  /**
   * 删除已上传的表单。
   */
  ApiResult<Void> delete(@Valid DeleteFormCommand command);

  /**
   * 查询表单状态。
   */
  ApiResult<FormStatusResponse> status(@Valid GetFormStatusQuery query);
}
