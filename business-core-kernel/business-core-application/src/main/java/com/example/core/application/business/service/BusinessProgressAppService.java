package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.primitives.identity.BatchId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务进度查询应用服务
 *
 * <p>聚合批次维度的进度数据,供 {@code BusinessProgressApi} 查询。
 *
 * <p>后续新增 AppService 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,管理事务边界</li>
 *   <li>通过 ProgressConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessProgressAppService {

    private final BatchRepository batchRepository;

    /**
     * 查询批次整体进度。
     *
     * @param batchId 批次 ID
     * @return 批次聚合根(含统计计数)
     */
    @Transactional(readOnly = true)
    public BusinessBatch getBatchProgress(BatchId batchId) {
        return batchRepository.loadOrThrow(batchId);
    }
}
