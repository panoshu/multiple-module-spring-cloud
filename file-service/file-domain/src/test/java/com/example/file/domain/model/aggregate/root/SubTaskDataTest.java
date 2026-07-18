package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.domain.model.valueobject.BusinessContext;
import com.example.file.domain.model.valueobject.RowError;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubTaskDataTest {

  @Test
  void should_create_pending_subtask() {
    SubTaskData sub = newSubTask();
    assertThat(sub.status()).isEqualTo(SubTaskStatus.PENDING);
    assertThat(sub.rowCount()).isEqualTo(2);
    assertThat(sub.isExpired()).isFalse();
  }

  @Test
  void applyValidationResult_should_mark_valid_when_no_errors() {
    SubTaskData sub = newSubTask();
    sub.applyValidationResult(ValidationResult.empty());
    assertThat(sub.status()).isEqualTo(SubTaskStatus.VALID);
  }

  @Test
  void applyValidationResult_should_mark_invalid_with_errors() {
    SubTaskData sub = newSubTask();
    RowError err = new RowError(0, "detailList", "amount > 0", "amount negative");
    sub.applyValidationResult(ValidationResult.of(List.of(err)));
    assertThat(sub.status()).isEqualTo(SubTaskStatus.INVALID);
    assertThat(sub.validationErrors()).hasSize(1);
  }

  @Test
  void markConsumed_should_set_status() {
    SubTaskData sub = newSubTask();
    sub.applyValidationResult(ValidationResult.empty());
    sub.markConsumed();
    assertThat(sub.status()).isEqualTo(SubTaskStatus.CONSUMED);
  }

  @Test
  void isExpired_should_be_true_after_expires_at() {
    SubTaskData sub = SubTaskData.create(SubTaskId.of("sub1"), FileTaskId.of("tsk1"),
        BizType.of("import_declare"), "RD_DEPT", BusinessContext.empty(),
        Map.of(), Map.of("detailList", List.of()), 0,
        UserNo.of("u1"), java.time.LocalDateTime.now().minusDays(31));
    assertThat(sub.isExpired()).isTrue();
  }

  private SubTaskData newSubTask() {
    return SubTaskData.create(SubTaskId.of("sub1"), FileTaskId.of("tsk1"),
        BizType.of("import_declare"), "RD_DEPT", BusinessContext.empty(),
        Map.of("enterpriseName", "ABC"),
        Map.of("detailList", List.of(Map.of("itemNo", "A1"), Map.of("itemNo", "A2"))),
        2, UserNo.of("u1"), null);
  }
}
