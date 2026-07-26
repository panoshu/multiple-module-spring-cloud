package com.example.core.application.engine.step.service;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.MaterialConditionContext;
import com.example.core.domain.business.aggregate.valueobject.MaterialItem;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 业务材料应用服务
 *
 * <p>编排材料的绑定、解绑、列表查询、完整性校验等业务流程。
 *
 * <p>后续新增 AppService 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,管理事务边界</li>
 *   <li>通过 MaterialConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialAppService {

  private final ApplicationRepository applicationRepository;

  /**
   * 绑定单个材料文件
   */
  @Transactional
  public void bindIndividualMaterial(ApplicationId appId, String materialCode, BusinessFile file) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.uploadIndividualPlanMaterial(materialCode, file);
    applicationRepository.save(app);
  }

  /**
   * 绑定打包上传的材料文件映射
   */
  @Transactional
  public void bindPackageMaterials(ApplicationId appId, BusinessFile zipFile) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.uploadPackage(zipFile);
    applicationRepository.save(app);
  }

  /**
   * 解绑单个材料文件
   *
   * @param appId        申请单 ID
   * @param materialCode 材料编码
   * @param fileId       文件 ID
   */
  @Transactional
  public void unbindIndividualMaterial(ApplicationId appId, String materialCode, FileId fileId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.removeIndividualPlanMaterial(materialCode, fileId);
    applicationRepository.save(app);
    log.info("解绑材料: appId={}, materialCode={}, fileId={}", appId.value(), materialCode, fileId.value());
  }

  /**
   * 查询申请单下的材料列表
   *
   * @param appId 申请单 ID
   * @return 材料项列表
   */
  @Transactional(readOnly = true)
  public List<MaterialItem> listMaterials(ApplicationId appId) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    return app.getPlanMaterials();
  }

  /**
   * 校验材料完整性
   *
   * <p>使用一个简单的 {@link MaterialConditionContext} 实现,
   * 基于 {@code conditionContext} Map 中的 key-value 进行规则评估。
   * 业务服务可覆盖本方法以提供更复杂的规则引擎(如 QLExpress)。
   *
   * @param appId            申请单 ID
   * @param conditionContext 条件上下文 Map
   * @return 材料项列表(供 Controller 计算未满足项)
   */
  @Transactional(readOnly = true)
  public List<MaterialItem> checkCompleteness(ApplicationId appId, Map<String, Object> conditionContext) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    // 返回材料列表,由 Converter 结合 conditionContext 计算未满足项
    return app.getPlanMaterials();
  }

  /**
   * 判断材料项是否满足(供 Converter 使用)
   *
   * @param item             材料项
   * @param conditionContext 条件上下文
   * @return true 表示满足
   */
  public boolean isSatisfied(MaterialItem item, Map<String, Object> conditionContext) {
    return item.isSatisfied(rule -> evaluateCondition(rule, conditionContext));
  }

  /**
   * 简单的条件评估:支持 {@code key=value} 格式的规则
   *
   * <p>业务服务可覆盖本方法以支持复杂表达式(如 {@code age>60})。
   *
   * @param rule            规则字符串
   * @param conditionContext 条件上下文
   * @return true 表示条件命中(材料变为必传)
   */
  private boolean evaluateCondition(String rule, Map<String, Object> conditionContext) {
    if (rule == null || rule.isBlank() || conditionContext == null) {
      return false;
    }
    int eqIdx = rule.indexOf('=');
    if (eqIdx > 0) {
      String key = rule.substring(0, eqIdx).trim();
      String value = rule.substring(eqIdx + 1).trim();
      Object actual = conditionContext.get(key);
      return actual != null && value.equals(String.valueOf(actual));
    }
    return conditionContext.containsKey(rule.trim());
  }
}
