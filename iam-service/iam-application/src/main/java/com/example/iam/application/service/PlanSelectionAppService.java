package com.example.iam.application.service;

import com.example.iam.api.command.ClearCurrentPlanCommand;
import com.example.iam.api.command.SelectPlanCommand;
import com.example.iam.api.dto.PlanPermissionDTO;
import com.example.iam.api.dto.SelectablePlanDTO;
import com.example.iam.api.query.GetCurrentPlanQuery;
import com.example.iam.api.query.ListSelectablePlansQuery;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.gateway.PlanMetadataGateway;
import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.types.UserId;
import com.example.shared.exception.BusinessException;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计划选择应用服务。
 *
 * <p>负责用户登录后选择当前办理的业务计划,以及查询当前已选计划与权限。
 * 选择计划后通过 {@link PermissionResolver} 计算权限快照,通过 {@link ChannelSessionPort}
 * 同步到会话,供后续请求鉴权使用。
 *
 * <p>可选计划来源:
 * <ul>
 *   <li>当前用户所属客户下的计划(通过 {@link PlanMetadataGateway} 查询)</li>
 *   <li>代办关系授权的计划(暂未在此处实现,待 Task 22 补充)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanSelectionAppService {

  private final ChannelSessionPort channelSessionPort;
  private final PermissionResolver permissionResolver;
  private final PlanMetadataGateway planMetadataGateway;

  /**
   * 列出当前用户可选计划。
   *
   * <p>简化实现:从当前会话获取用户 ID 与客户编号,通过 {@link PlanMetadataGateway} 查询可选计划。
   * 实际客户编号来源由子类或上下文补充(本服务暂以当前会话 userNo 作为客户编号占位)。
   *
   * @param query 列表查询
   * @return 可选计划分页列表
   */
  @Transactional(readOnly = true)
  public PageData<SelectablePlanDTO> listSelectablePlans(ListSelectablePlansQuery query) {
    Long userId = channelSessionPort.currentUserId();
    String userNo = channelSessionPort.currentUserNo();

    List<PlanMetadata> plans = planMetadataGateway.findSelectablePlansByCustomer(userNo);
    List<SelectablePlanDTO> filtered = plans.stream()
        .filter(p -> matchesKeyword(p, query.keyword()))
        .map(p -> toSelectablePlanDTO(p, false, null))
        .toList();
    PageQuery pageQuery = query.pageQuery();
    if (pageQuery == null) {
      return new PageData<>(filtered.size(), 0, filtered.size(), false, filtered);
    }
    return paginate(filtered, pageQuery);
  }

  /**
   * 选择当前办理计划。
   *
   * <p>流程:
   * <ol>
   *   <li>从渠道上下文获取当前用户 ID</li>
   *   <li>调用 {@link PermissionResolver} 计算权限快照</li>
   *   <li>通过 {@link ChannelSessionPort} 设置当前计划与权限</li>
   * </ol>
   *
   * @param command 选择计划命令
   * @return 计划权限 DTO
   */
  @Transactional
  public PlanPermissionDTO selectPlan(SelectPlanCommand command) {
    Long userId = channelSessionPort.currentUserId();
    PermissionSnapshot snapshot = permissionResolver.resolve(UserId.of(userId), command.planId());

    Set<String> permissionStrings = snapshot.permissions().stream()
        .map(pc -> pc.value())
        .collect(Collectors.toSet());
    channelSessionPort.setCurrentPlan(command.planId(), permissionStrings);

    log.info("计划选择成功: userId={}, planId={}, permissionCount={}",
        userId, command.planId(), permissionStrings.size());
    return new PlanPermissionDTO(
        command.planId(),
        null,
        command.customerNo(),
        permissionStrings,
        LocalDateTime.now(),
        null,
        null);
  }

  /**
   * 获取当前已选计划。
   *
   * @param query 查询对象
   * @return 计划权限 DTO(未选计划时返回空 DTO)
   */
  @Transactional(readOnly = true)
  public PlanPermissionDTO getCurrentPlan(GetCurrentPlanQuery query) {
    String planId = channelSessionPort.getCurrentPlanId();
    if (planId == null || planId.isBlank()) {
      return new PlanPermissionDTO(null, null, null, Set.of(), null, null, null);
    }
    Set<String> permissions = channelSessionPort.getCurrentPermissions();
    return new PlanPermissionDTO(
        planId, null, null, permissions, null, null, null);
  }

  /**
   * 清除当前已选计划。
   *
   * @param command 清除命令
   */
  @Transactional
  public void clearCurrentPlan(ClearCurrentPlanCommand command) {
    channelSessionPort.clearCurrentPlan();
    log.info("当前计划已清除");
  }

  /**
   * 加载计划元数据或抛出业务异常。
   */
  private PlanMetadata loadPlanOrThrow(String planId) {
    return planMetadataGateway.findByPlanNo(planId)
        .orElseThrow(() -> new BusinessException(IamAuthzErrorCode.PLAN_NOT_FOUND)
            .withUserDetail("计划不存在")
            .withContext("planId", planId));
  }

  /**
   * 列表分页切片。
   */
  private PageData<SelectablePlanDTO> paginate(List<SelectablePlanDTO> plans, PageQuery pageQuery) {
    int total = plans.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<SelectablePlanDTO> items = plans.subList(from, to);
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesKeyword(PlanMetadata plan, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return true;
    }
    return plan.planNo() != null && plan.planNo().contains(keyword);
  }

  /**
   * 计划元数据转可选计划 DTO。
   *
   * @param metadata        计划元数据
   * @param isDelegated     是否为代办计划
   * @param delegatorPlanNo 代办来源计划编号(非代办时为 null)
   */
  private SelectablePlanDTO toSelectablePlanDTO(PlanMetadata metadata,
                                                boolean isDelegated,
                                                String delegatorPlanNo) {
    return new SelectablePlanDTO(
        metadata.planNo(),
        null,
        metadata.customerNo(),
        null,
        metadata.operationMode() != null ? metadata.operationMode().name() : null,
        isDelegated,
        delegatorPlanNo);
  }
}
