package com.example.core.adapter.context;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessMetaContextAssembler 单元测试
 *
 * @author panoshu
 */
class BusinessMetaContextAssemblerTest {

  private final BusinessMetaContextAssembler assembler = new BusinessMetaContextAssembler();

  @Test
  void should_assemble_meta_context_from_command_and_session() {
    SessionContext session = new SessionContext(
      "U001", "USER", "alice", "Alice",
      "INTERNET", "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      false, null, null, false, null, null,
      Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
    );
    String businessType = "ANNUITY_OPEN";
    String planNo = "P001";

    BusinessMetaContext meta = assembler.assemble(businessType, planNo, session);

    assertThat(meta.businessType()).isEqualTo("ANNUITY_OPEN");
    assertThat(meta.planNo()).isEqualTo("P001");
    assertThat(meta.customerNo()).isEqualTo("C001");
    assertThat(meta.customerName()).isEqualTo("Customer A");
    assertThat(meta.productNo()).isEqualTo("PRD001");
    assertThat(meta.productName()).isEqualTo("Product A");
    assertThat(meta.planName()).isEqualTo("Plan A");
    assertThat(meta.operationModel()).isEqualTo("MODEL_A");
    assertThat(meta.accountManager()).isEqualTo("CJP");
  }

  @Test
  void should_throw_when_plan_no_mismatch() {
    SessionContext session = new SessionContext(
      "U001", "USER", "alice", "Alice",
      "INTERNET", "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      false, null, null, false, null, null,
      Set.of(), Set.of()
    );

    assertThatThrownBy(() -> assembler.assemble("ANNUITY_OPEN", "P002", session))
      .isInstanceOf(BusinessException.class)
      .extracting(throwable -> ((BusinessException) throwable).displayMessage())
      .asString()
      .contains("所选计划与会话中的计划不一致");
  }
}
