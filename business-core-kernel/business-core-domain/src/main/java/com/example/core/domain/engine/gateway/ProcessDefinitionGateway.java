package com.example.core.domain.engine.gateway;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;

import java.util.Optional;

/**
 * 流程定义加载网关。按业务类型与版本读取不可变流程定义，
 * 与 BusinessConfigGateway 中材料规则、表单规则等非流程配置职责分离。
 * <p>
 * 历史版本必须可按 businessType + version 读取，供在途实例继续使用启动时的版本。
 */
public interface ProcessDefinitionGateway {

  Optional<ProcessDefinition> load(BusinessTypeCode businessType, DefinitionVersion version);

  Optional<ProcessDefinition> loadLatest(BusinessTypeCode businessType);
}
