package com.example.core.domain.engine.repository;

import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 步骤执行仓储契约（spec §7.2 / §17）。
 * <p>
 * 除通用 {@link Repository} 契约外，提供唯一性约束校验所需的查询：
 * 按 requestNo 幂等查找、按「申请 + 步骤」列出全部尝试、查找运行中执行
 * （保证同一流程同一时刻最多一个运行中的步骤执行）。
 */
public interface StepExecutionRepository extends Repository<StepExecution, StepExecutionId> {

  Optional<StepExecution> findByRequestNo(String requestNo);

  List<StepExecution> findByApplicationAndStep(ApplicationId applicationId, StepCode stepCode);

  Optional<StepExecution> findRunningByApplication(ApplicationId applicationId);
}
