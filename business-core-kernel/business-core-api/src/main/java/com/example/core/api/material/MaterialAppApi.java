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

import java.util.List;

/**
 * 业务材料管理 API（纯契约接口）
 *
 * <p>定义材料的逐个绑定、打包绑定、解绑、列表查询、完整性校验等公共方法签名，不含传输层注解。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在本接口新增方法签名</li>
 *   <li>在各 xxx-api 的子接口中重新声明并标注 {@code @PostExchange}</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller，通过 MaterialConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
public interface MaterialAppApi {

  /**
   * 逐个绑定材料文件。
   */
  ApiResult<Void> bindIndividual(@Valid BindIndividualMaterialCommand command);

  /**
   * 打包绑定材料文件。
   */
  ApiResult<Void> bindPackage(@Valid BindPackageMaterialCommand command);

  /**
   * 解绑材料文件。
   */
  ApiResult<Void> unbind(@Valid UnbindMaterialCommand command);

  /**
   * 查询材料列表。
   */
  ApiResult<List<MaterialItemResponse>> list(@Valid ListMaterialsQuery query);

  /**
   * 校验材料完整性。
   */
  ApiResult<CheckCompletenessResponse> checkCompleteness(@Valid CheckCompletenessQuery query);
}
