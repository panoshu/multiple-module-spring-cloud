package com.example.core.adapter.validator;

import com.example.core.api.registrar.BusinessTypeRegistrar;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务类型校验器
 *
 * <p>校验请求中的业务类型是否由本服务支持,防止路由错误或恶意请求。
 *
 * @author panoshu
 */
@Component
@RequiredArgsConstructor
public class SupportedBusinessTypeValidator {

    private final BusinessTypeRegistrar registrar;

    /**
     * 校验业务类型是否由本服务支持。
     *
     * @param businessType 业务类型枚举名称
     * @throws BusinessException 当注册器未配置或业务类型不在支持列表时
     */
    public void validate(String businessType) {
        if (registrar == null) {
            throw new BusinessException(CommonError.INTERNAL_SERVER_ERROR)
                .withUserDetail("服务未配置业务类型注册器")
                .withLogDetail("BusinessTypeRegistrar bean is null");
        }
        if (!registrar.supportedBusinessTypes().contains(businessType)) {
            throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("不支持的业务类型")
                .withLogDetail("businessType=%s, supported=%s".formatted(businessType, registrar.supportedBusinessTypes()));
        }
    }
}
