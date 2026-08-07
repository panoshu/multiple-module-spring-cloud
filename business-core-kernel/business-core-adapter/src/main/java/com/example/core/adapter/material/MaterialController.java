package com.example.core.adapter.material;

import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.material.converter.MaterialConverter;
import com.example.core.api.context.SessionContext;
import com.example.core.api.material.MaterialAppApi;
import com.example.core.api.material.command.BindIndividualMaterialCommand;
import com.example.core.api.material.command.BindPackageMaterialCommand;
import com.example.core.api.material.command.UnbindMaterialCommand;
import com.example.core.api.material.query.CheckCompletenessQuery;
import com.example.core.api.material.query.ListMaterialsQuery;
import com.example.core.api.material.response.CheckCompletenessResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.core.application.engine.step.service.MaterialAppService;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.MaterialItem;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.FileId;
import com.example.shared.web.core.api.ApiResult;
import com.example.auth.api.annotation.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务材料管理 Controller
 *
 * <p>实现 {@link MaterialAppApi},入口完成会话解析与功能权限校验,
 * 调用 {@link MaterialAppService} 进行材料处理。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequirePermission(功能权限码)</li>
 *   <li>通过 MaterialConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MaterialController implements MaterialAppApi {

  private final MaterialAppService materialAppService;
  private final MaterialConverter converter;
  private final SessionContextResolver sessionResolver;

  @Override
  @RequirePermission(business = "MATERIAL", action = "BIND")
  public ApiResult<Void> bindIndividual(@Valid @RequestBody BindIndividualMaterialCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("逐个绑定材料: applicationId={}, materialCode={}, userNo={}",
      command.applicationId(), command.materialCode(), session.userNo());

    BusinessFile file = new BusinessFile(
      new FileId(command.fileId()),
      command.fileName(),
      extractExtension(command.fileName()),
      null
    );
    materialAppService.bindIndividualMaterial(
      new ApplicationId(command.applicationId()),
      command.materialCode(),
      file
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "MATERIAL", action = "BIND")
  public ApiResult<Void> bindPackage(@Valid @RequestBody BindPackageMaterialCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("打包绑定材料: applicationId={}, fileId={}, userNo={}",
      command.applicationId(), command.fileId(), session.userNo());

    BusinessFile zipFile = new BusinessFile(
      new FileId(command.fileId()),
      command.fileName(),
      extractExtension(command.fileName()),
      null
    );
    materialAppService.bindPackageMaterials(
      new ApplicationId(command.applicationId()),
      zipFile
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "MATERIAL", action = "UNBIND")
  public ApiResult<Void> unbind(@Valid @RequestBody UnbindMaterialCommand command) {
    SessionContext session = sessionResolver.require();
    log.info("解绑材料: applicationId={}, materialCode={}, fileId={}, userNo={}",
      command.applicationId(), command.materialCode(), command.fileId(), session.userNo());

    materialAppService.unbindIndividualMaterial(
      new ApplicationId(command.applicationId()),
      command.materialCode(),
      new FileId(command.fileId())
    );
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "MATERIAL", action = "VIEW")
  public ApiResult<List<MaterialItemResponse>> list(@Valid @RequestBody ListMaterialsQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("查询材料列表: applicationId={}, userNo={}", query.applicationId(), session.userNo());

    List<MaterialItem> items = materialAppService.listMaterials(
      new ApplicationId(query.applicationId())
    );
    return ApiResult.success(converter.toResponseList(items));
  }

  @Override
  @RequirePermission(business = "MATERIAL", action = "VIEW")
  public ApiResult<CheckCompletenessResponse> checkCompleteness(@Valid @RequestBody CheckCompletenessQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("校验材料完整性: applicationId={}, userNo={}", query.applicationId(), session.userNo());

    Map<String, Object> conditionContext = query.conditionContext() != null
      ? query.conditionContext() : Map.of();
    List<MaterialItem> items = materialAppService.checkCompleteness(
      new ApplicationId(query.applicationId()),
      conditionContext
    );
    List<String> unsatisfiedCodes = items.stream()
      .filter(item -> !materialAppService.isSatisfied(item, conditionContext))
      .map(MaterialItem::materialCode)
      .toList();
    return ApiResult.success(new CheckCompletenessResponse(
      unsatisfiedCodes.isEmpty(),
      unsatisfiedCodes
    ));
  }

  /**
   * 从文件名提取扩展名(不含点号)。
   */
  private String extractExtension(String fileName) {
    if (fileName == null || !fileName.contains(".")) {
      return "";
    }
    return fileName.substring(fileName.lastIndexOf('.') + 1);
  }
}
