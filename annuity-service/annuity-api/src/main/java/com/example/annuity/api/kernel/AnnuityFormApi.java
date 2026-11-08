package com.example.annuity.api.kernel;

import com.example.core.api.form.BusinessFormApi;
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
 * 年金服务 — 表单管理 API（服务专有 kernel API）
 *
 * <p>继承 {@link BusinessFormApi} 纯契约接口，添加服务级路径前缀
 * {@code /annuity/core/form}，供 BFF 通过 httpexchange 代理调用。
 *
 * @author panoshu
 */
@HttpExchange("/annuity/core/form")
public interface AnnuityFormApi extends BusinessFormApi {

  @PostExchange("/upload-token")
  @Override
  ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command);

  @PostExchange("/confirm-upload")
  @Override
  ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command);

  @PostExchange("/delete")
  @Override
  ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command);

  @PostExchange("/status")
  @Override
  ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query);
}
