package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.infrastructure.FileInfrastructureTestConfiguration;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileAccessLogId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileAccessLogRepositoryImpl 集成测试。
 *
 * <p>使用 H2 内存数据库 + MyBatis-Flex，通过 {@link FileInfrastructureTestConfiguration}
 * 限定 Spring 上下文只加载 repository/converter/mapper 包 Bean，避免级联触发
 * shared-id-starter / shared-cache-starter / redisson 等无关自动配置。
 *
 * <p>每个测试方法通过 @Sql 重建 t_file_access_log 表，保证用例间隔离。
 */
@SpringBootTest(classes = FileInfrastructureTestConfiguration.class)
@Sql(scripts = "/schema-file-access-log.sql")
@DisplayName("FileAccessLogRepositoryImpl 集成测试")
class FileAccessLogRepositoryImplTest {

    @Autowired
    private FileAccessLogRepository repository;

    @Test
    @DisplayName("save(APPLY) 后 load(logId) 应能取回完整字段")
    void should_save_and_load_by_id() {
        FileId fileId = new FileId("f-001");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        FileAccessLog log = FileAccessLog.apply(
            fileId, FileUsage.SOURCE, scope, UserNo.of("u1"),
            "test-app", "hash-001"
        );

        repository.save(log);

        Optional<FileAccessLog> loaded = repository.load(log.id());
        assertThat(loaded).isPresent();
        FileAccessLog got = loaded.get();
        assertThat(got.id()).isEqualTo(log.id());
        assertThat(got.fileId()).isEqualTo(fileId);
        assertThat(got.action()).isEqualTo(FileAccessAction.APPLY);
        assertThat(got.usage()).isEqualTo(FileUsage.SOURCE);
        assertThat(got.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(got.productNo()).isEqualTo(ProductNo.of("P001"));
        assertThat(got.operator()).isEqualTo(UserNo.of("u1"));
        assertThat(got.sourceApp()).isEqualTo("test-app");
        assertThat(got.sourceIp()).isNull();
        assertThat(got.tokenHash()).isEqualTo("hash-001");
        assertThat(got.result()).isEqualTo(FileAccessResult.SUCCESS);
        assertThat(got.failReason()).isNull();
        assertThat(got.occurAt()).isNotNull();
        assertThat(got.createdBy()).isEqualTo(UserNo.of("u1"));
    }

    @Test
    @DisplayName("findByFileId 返回该文件所有流水，按 occurAt desc 排序")
    void should_find_by_file_id_desc_by_occur_at() throws Exception {
        FileId fileId = new FileId("f-002");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C002"), ProductNo.of("P002"));

        // 两条同一文件的 APPLY + ACCESS 流水
        FileAccessLog applyLog = FileAccessLog.apply(
            fileId, FileUsage.SOURCE, scope, UserNo.of("u1"),
            "app-a", "hash-002"
        );
        repository.save(applyLog);

        // 确保 occurAt 不同（findByFileId 按 desc 排序）
        Thread.sleep(10);

        FileAccessLog accessLog = FileAccessLog.access(
            fileId, FileUsage.SOURCE, scope, UserNo.of("u1"),
            "app-a", "127.0.0.1", "hash-002",
            FileAccessResult.SUCCESS, null
        );
        repository.save(accessLog);

        List<FileAccessLog> found = repository.findByFileId(fileId);
        assertThat(found).hasSize(2);
        // desc: accessLog.occurAt > applyLog.occurAt，accessLog 在前
        assertThat(found.get(0).action()).isEqualTo(FileAccessAction.ACCESS);
        assertThat(found.get(1).action()).isEqualTo(FileAccessAction.APPLY);
        assertThat(found).allSatisfy(log -> assertThat(log.fileId()).isEqualTo(fileId));
    }

    @Test
    @DisplayName("findByTokenHash 返回同 token 的 APPLY + ACCESS 两条流水，按 occurAt asc 排序")
    void should_find_by_token_hash_asc_by_occur_at() throws Exception {
        FileId fileId = new FileId("f-003");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C003"), ProductNo.of("P003"));
        String tokenHash = "hash-shared-003";

        FileAccessLog applyLog = FileAccessLog.apply(
            fileId, FileUsage.PARSED, scope, UserNo.of("u2"),
            "app-b", tokenHash
        );
        repository.save(applyLog);

        Thread.sleep(10);

        FileAccessLog accessLog = FileAccessLog.access(
            fileId, FileUsage.PARSED, scope, UserNo.of("u2"),
            "app-b", "10.0.0.1", tokenHash,
            FileAccessResult.EXPIRED, "token expired"
        );
        repository.save(accessLog);

        List<FileAccessLog> found = repository.findByTokenHash(tokenHash);
        assertThat(found).hasSize(2);
        // asc: applyLog.occurAt < accessLog.occurAt，applyLog 在前
        assertThat(found.get(0).action()).isEqualTo(FileAccessAction.APPLY);
        assertThat(found.get(1).action()).isEqualTo(FileAccessAction.ACCESS);
        assertThat(found.get(1).result()).isEqualTo(FileAccessResult.EXPIRED);
        assertThat(found.get(1).failReason()).isEqualTo("token expired");
    }

    @Test
    @DisplayName("countByActionAndTimeRange 应统计指定动作在时间窗口内的记录数")
    void should_count_by_action_and_time_range() throws Exception {
        FileId fileId = new FileId("f-004");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C004"), ProductNo.of("P004"));

        // 在 [now-1h, now+1h] 窗口内插入 3 条 ACCESS 流水
        repository.save(FileAccessLog.access(
            fileId, FileUsage.EXPORT, scope, UserNo.of("u3"),
            "app-c", "1.1.1.1", "hash-004a",
            FileAccessResult.SUCCESS, null
        ));
        repository.save(FileAccessLog.access(
            fileId, FileUsage.EXPORT, scope, UserNo.of("u3"),
            "app-c", "1.1.1.2", "hash-004b",
            FileAccessResult.FAIL, "forbidden"
        ));
        repository.save(FileAccessLog.access(
            fileId, FileUsage.EXPORT, scope, UserNo.of("u3"),
            "app-c", "1.1.1.3", "hash-004c",
            FileAccessResult.REJECTED, "ip mismatch"
        ));
        // 1 条 APPLY 流水（不计入 ACCESS 统计）
        repository.save(FileAccessLog.apply(
            fileId, FileUsage.EXPORT, scope, UserNo.of("u3"),
            "app-c", "hash-004d"
        ));

        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now().plusHours(1);

        long accessCount = repository.countByActionAndTimeRange(FileAccessAction.ACCESS, from, to);
        assertThat(accessCount).isEqualTo(3L);

        long applyCount = repository.countByActionAndTimeRange(FileAccessAction.APPLY, from, to);
        assertThat(applyCount).isEqualTo(1L);

        // 超出时间窗口
        long outOfRange = repository.countByActionAndTimeRange(
            FileAccessAction.ACCESS,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1)
        );
        assertThat(outOfRange).isZero();
    }

    @Test
    @DisplayName("load 不存在的 logId 应返回 empty")
    void should_return_empty_when_load_non_existent() {
        Optional<FileAccessLog> loaded = repository.load(FileAccessLogId.of("non-existent-id"));
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("findByFileId 传入 null 应返回空列表")
    void should_return_empty_list_when_file_id_is_null() {
        List<FileAccessLog> found = repository.findByFileId(null);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByTokenHash 传入空字符串应返回空列表")
    void should_return_empty_list_when_token_hash_is_blank() {
        List<FileAccessLog> found = repository.findByTokenHash("");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("delete 流水应抛 UnsupportedOperationException（审计不可删除）")
    void should_throw_when_delete_log() {
        FileId fileId = new FileId("f-005");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C005"), ProductNo.of("P005"));
        FileAccessLog log = FileAccessLog.apply(
            fileId, FileUsage.ARCHIVE, scope, UserNo.of("u4"),
            "app-d", "hash-005"
        );
        repository.save(log);

        assertThatThrownBy(() -> repository.delete(log))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("不可删除");
    }

    @Test
    @DisplayName("deleteById 流水应抛 UnsupportedOperationException（审计不可删除）")
    void should_throw_when_delete_by_id() {
        FileAccessLogId id = FileAccessLogId.of("log-id-to-delete");
        assertThatThrownBy(() -> repository.deleteById(id))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("不可删除");
    }
}
