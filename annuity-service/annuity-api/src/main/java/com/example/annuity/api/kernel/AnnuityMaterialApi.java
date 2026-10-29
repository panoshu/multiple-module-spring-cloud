package com.example.annuity.api.kernel;

import com.example.core.api.material.MaterialAppApi;
import com.example.core.api.material.command.BindIndividualMaterialCommand;
import com.example.core.api.material.command.BindPackageMaterialCommand;
import com.example.core.api.material.command.UnbindMaterialCommand;
import com.example.core.api.material.query.CheckCompletenessQuery;
import com.example.core.api.material.query.ListMaterialsQuery;
import com.example.core.api.material.response.CheckCompletenessResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 年金服务 — 材料管理 API（服务专有 kernel API）
 *
 * <p>继承 {@link MaterialAppApi} 纯契约接口，添加服务级路径前缀
 * {@code /annuity/core/material}，供 BFF 通过 httpexchange 代理调用。
 *
 * @author panoshu
 */
@HttpExchange("/annuity/core/material")
public interface AnnuityMaterialApi extends MaterialAppApi {

  @PostExchange("/bind-individual")
  @Override
  ApiResult<Void> bindIndividual(@Valid @RequestBody BindIndividualMaterialCommand command);

  @PostExchange("/bind-package")
  @Override
  ApiResult<Void> bindPackage(@Valid @RequestBody BindPackageMaterialCommand command);

  @PostExchange("/unbind")
  @Override
  ApiResult<Void> unbind(@Valid @RequestBody UnbindMaterialCommand command);

  @PostExchange("/list")
  @Override
  ApiResult<List<MaterialItemResponse>> list(@Valid @RequestBody ListMaterialsQuery query);

  @PostExchange("/check-completeness")
  @Override
  ApiResult<CheckCompletenessResponse> checkCompleteness(@Valid @RequestBody CheckCompletenessQuery query);
}
