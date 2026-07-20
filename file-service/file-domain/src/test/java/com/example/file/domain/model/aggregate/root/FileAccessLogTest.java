package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
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
}
