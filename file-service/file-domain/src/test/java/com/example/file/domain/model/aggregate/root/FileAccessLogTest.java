package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileAccessLog 聚合根")
class FileAccessLogTest {

  @Test
  @DisplayName("apply 工厂方法创建 APPLY 记录")
  void should_create_apply_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.apply(
      new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
      "approval-service", "hash-001"
    );
    assertThat(log.action()).isEqualTo(FileAccessAction.APPLY);
    assertThat(log.result()).isEqualTo(FileAccessResult.SUCCESS);
    assertThat(log.fileId()).isEqualTo(new FileId("f001"));
    assertThat(log.tokenHash()).isEqualTo("hash-001");
    assertThat(log.occurAt()).isNotNull();
  }

  @Test
  @DisplayName("access 工厂方法创建 ACCESS 记录（成功）")
  void should_create_access_success_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.access(
      new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
      "approval-service", "192.168.1.1", "hash-001",
      FileAccessResult.SUCCESS, null
    );
    assertThat(log.action()).isEqualTo(FileAccessAction.ACCESS);
    assertThat(log.result()).isEqualTo(FileAccessResult.SUCCESS);
    assertThat(log.sourceIp()).isEqualTo("192.168.1.1");
  }

  @Test
  @DisplayName("access 工厂方法创建 ACCESS 记录（失败）")
  void should_create_access_failed_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.access(
      new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
      "approval-service", "192.168.1.1", "hash-001",
      FileAccessResult.FAIL, "token 校验失败"
    );
    assertThat(log.result()).isEqualTo(FileAccessResult.FAIL);
    assertThat(log.failReason()).isEqualTo("token 校验失败");
  }

  @Test
  @DisplayName("markFail 修改 result 和 failReason")
  void should_mark_fail() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.apply(
      new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
      "approval-service", "hash-001"
    );
    log.markFail("存储失败");
    assertThat(log.result()).isEqualTo(FileAccessResult.FAIL);
    assertThat(log.failReason()).isEqualTo("存储失败");
  }

  @Test
  @DisplayName("reconstitute 保留所有字段")
  void should_reconstitute_preserves_all_fields() {
    LocalDateTime now = LocalDateTime.now();
    FileAccessLogId logId = FileAccessLogId.of("log-001");
    UserNo creator = UserNo.of("u1");
    UserNo updater = UserNo.of("u2");
    Version version = Version.of(3L);

    FileAccessLog log = FileAccessLog.reconstitute(
      logId,
      new FileId("f001"),
      FileAccessAction.ACCESS,
      FileUsage.SOURCE,
      CustomerNo.of("C001"),
      ProductNo.of("P001"),
      UserNo.of("u1"),
      "approval-service",
      "192.168.1.1",
      "hash-001",
      FileAccessResult.FAIL,
      "token 校验失败",
      now,
      creator,
      updater,
      now.minusMinutes(5),
      now,
      version
    );

    // FileAccessLog 自身字段
    assertThat(log.id()).isEqualTo(logId);
    assertThat(log.fileId()).isEqualTo(new FileId("f001"));
    assertThat(log.action()).isEqualTo(FileAccessAction.ACCESS);
    assertThat(log.usage()).isEqualTo(FileUsage.SOURCE);
    assertThat(log.customerNo()).isEqualTo(CustomerNo.of("C001"));
    assertThat(log.productNo()).isEqualTo(ProductNo.of("P001"));
    assertThat(log.operator()).isEqualTo(UserNo.of("u1"));
    assertThat(log.sourceApp()).isEqualTo("approval-service");
    assertThat(log.sourceIp()).isEqualTo("192.168.1.1");
    assertThat(log.tokenHash()).isEqualTo("hash-001");
    assertThat(log.result()).isEqualTo(FileAccessResult.FAIL);
    assertThat(log.failReason()).isEqualTo("token 校验失败");
    assertThat(log.occurAt()).isEqualTo(now);
    // AggregateRoot 继承字段
    assertThat(log.createdBy()).isEqualTo(creator);
    assertThat(log.updatedBy()).isEqualTo(updater);
    assertThat(log.createdAt()).isEqualTo(now.minusMinutes(5));
    assertThat(log.updatedAt()).isEqualTo(now);
    assertThat(log.version()).isEqualTo(version);
  }
}
