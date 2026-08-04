package com.example.core.api.form;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.command.ConfirmUploadCommand;
import com.example.core.api.form.command.DeleteFormCommand;
import com.example.core.api.form.query.GetFormStatusQuery;
import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 业务表单管理 API
 *
 * <p>提供表单的上传 token 申请、上传确认、删除、状态查询等公共接口。
 * 路径前缀 {@code /core/form}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 FormConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/form")
public interface BusinessFormApi {

  /**
   * 申请文件上传临时凭证。
   */
  @PostExchange("/upload-token")
  ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command);

  /**
   * 确认文件已上传(前端直传文件服务后回调)。
   */
  @PostExchange("/confirm-upload")
  ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command);

  /**
   * 删除已上传的表单。
   */
  @PostExchange("/delete")
  ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command);

  /**
   * 查询表单状态。
   */
  @PostExchange("/status")
  ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query);
}
