package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApplicationSource 申请来源")
class ApplicationSourceTest {

  @Test
  @DisplayName("表单来源必填")
  void requiresFormId() {
    assertThatThrownBy(() -> new ApplicationSource(null, BatchId.of("B-1")))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("batchMode=NONE 的流程允许申请不关联批次")
  void allowsAbsentBatch() {
    ApplicationSource source = new ApplicationSource(new FormId("F-1"), null);

    assertThat(source.formId()).isEqualTo(new FormId("F-1"));
    assertThat(source.batchId()).isNull();
  }

  @Test
  @DisplayName("批次驱动流程保留批次引用")
  void keepsBatchWhenPresent() {
    ApplicationSource source = new ApplicationSource(new FormId("F-1"), BatchId.of("B-1"));

    assertThat(source.batchId()).isEqualTo(BatchId.of("B-1"));
  }
}
