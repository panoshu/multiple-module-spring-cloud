package com.example.core.application.business.service;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 业务申请单应用服务
 *
 * <p>编排申请单的列表查询、详情查询、推进、提交等业务流程,
 * 复用 {@link FlowOrchestrationService} 完成流程编排。
 *
 * <p>后续新增 AppService 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,管理事务边界</li>
 *   <li>通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessApplicationAppService {

    private final FlowOrchestrationService flowOrchestrationService;
    private final ApplicationRepository applicationRepository;

    /**
     * 查询批次下的申请单列表。
     *
     * @param batchId 批次 ID
     * @return 申请单列表
     */
    @Transactional(readOnly = true)
    public List<BusinessApplication> findByBatchId(BatchId batchId) {
        return applicationRepository.findByBatchId(batchId);
    }

    /**
     * 加载申请单或抛出异常。
     *
     * @param applicationId 申请单 ID
     * @return 申请单聚合根
     */
    @Transactional(readOnly = true)
    public BusinessApplication loadOrThrow(ApplicationId applicationId) {
        return applicationRepository.loadOrThrow(applicationId);
    }

    /**
     * 推进申请单到下一节点。
     *
     * <p>委托给 {@link FlowOrchestrationService#advanceStep(ApplicationId)},
     * 由管道 preValidation 中的 handler 完成业务数据校验,
     * 校验失败则抛出业务异常,事务回滚。
     *
     * @param applicationId 申请单 ID
     */
    @Transactional
    public void advanceStep(ApplicationId applicationId) {
        flowOrchestrationService.advanceStep(applicationId);
        log.info("推进申请单: applicationId={}", applicationId.value());
    }

    /**
     * 提交申请单。
     *
     * <p>当前实现直接复用 {@link FlowOrchestrationService#advanceStep(ApplicationId)},
     * 审批判断由管道 preValidation 中的 handler 完成(如配置了审批判断 handler)。
     * 若需要审批,handler 内部会触发审批流创建。
     *
     * @param applicationId 申请单 ID
     */
    @Transactional
    public void submit(ApplicationId applicationId) {
        flowOrchestrationService.advanceStep(applicationId);
        log.info("提交申请单: applicationId={}", applicationId.value());
    }
}
