package com.example.annuity.infrastructure.gateway;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.shared.primitives.identity.CustomerNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock 客户网关实现（演示环境）
 * <p>
 * 返回固定客户画像数据用于演示。生产环境替换为真实客户接口。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component
@Primary
public class MockAnnuityCustomerGateway implements AnnuityCustomerGateway {

  @Override
  public CustomerProfile queryCustomer(CustomerNo customerNo) {
    log.info("[Mock] 查询客户画像, customerNo={}", customerNo.value());
    return new CustomerProfile(
        customerNo,
        "LOW",
        List.of("CJ-PENSION-LTD")
    );
  }
}
