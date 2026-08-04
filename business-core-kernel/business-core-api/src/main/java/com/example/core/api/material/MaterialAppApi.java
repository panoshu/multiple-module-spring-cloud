package com.example.core.api.material;

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
 * 业务材料管理 API
 *
 * <p>提供材料的逐个绑定、打包绑定、解绑、列表查询、完整性校验等公共接口。
 * 路径前缀 {@code /core/material}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 MaterialConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/material")
public interface MaterialAppApi {

  /**
   * 逐个绑定材料文件。
   */
  @PostExchange("/bind-individual")
  ApiResult<Void> bindIndividual(@Valid @RequestBody BindIndividualMaterialCommand command);

  /**
   * 打包绑定材料文件。
   */
  @PostExchange("/bind-package")
  ApiResult<Void> bindPackage(@Valid @RequestBody BindPackageMaterialCommand command);

  /**
   * 解绑材料文件。
   */
  @PostExchange("/unbind")
  ApiResult<Void> unbind(@Valid @RequestBody UnbindMaterialCommand command);

  /**
   * 查询材料列表。
   */
  @PostExchange("/list")
  ApiResult<List<MaterialItemResponse>> list(@Valid @RequestBody ListMaterialsQuery query);

  /**
   * 校验材料完整性。
   */
  @PostExchange("/check-completeness")
  ApiResult<CheckCompletenessResponse> checkCompleteness(@Valid @RequestBody CheckCompletenessQuery query);
}
