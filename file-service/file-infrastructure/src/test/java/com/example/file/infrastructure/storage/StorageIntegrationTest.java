package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.CopyResult;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 存储引擎端到端集成测试。
 *
 * <p>验证 Store → Open → ComputeMd5 → Exists → Copy 全流程，使用 LOCAL 后端。
 * 使用真实 Spring 上下文加载 StorageAutoConfiguration + LocalFileStorage + FileStorageRouter。
 * FileMetadataRepository 使用 Mockito mock（测试焦点在存储引擎，不在数据库）。
 *
 * <p>实现说明（与 brief 偏差）：
 * brief 假设 {@code FileStorageRouter.store()} 内部会调用 {@code file.markUploaded()}
 * 修改 FileMetadata 状态。但实际生产代码 {@code store()} 仅写入物理文件，不修改
 * FileMetadata 状态。因此本测试在 {@code store()} 后显式调用
 * {@code file.markUploaded(predictedStorageKey, null)} 来过渡状态，使后续
 * {@code open()} / {@code computeMd5()} / {@code copy()} 能取到 storageKey。
 * storageKey 通过复制 {@link FileStorageRouter} 的生成逻辑在测试中预测。
 */
@SpringBootTest(classes = StorageTestConfiguration.class)
@TestPropertySource(properties = {
    "file.storage.enabled=true",
    "file.storage.targets[0].id=local-default",
    "file.storage.targets[0].type=LOCAL",
    "file.storage.targets[0].base-path=${java.io.tmpdir}/file-service-integration-test",
    "file.storage.routing.source=local-default",
    "file.storage.routing.export=local-default",
    "file.storage.routing.parsed=local-default",
    "file.storage.routing.archive=local-default"
})
class StorageIntegrationTest {

    @Autowired
    private FileStorageGateway storageGateway;

    @Autowired
    private StorageTargetResolver targetResolver;

    @Autowired
    private FileMetadataRepository metadataRepository;

    @BeforeEach
    void setUp() {
        reset(metadataRepository);
    }

    @Test
    @DisplayName("Local 后端: store → open → computeMd5 完整流程")
    void local_backend_store_open_md5_flow() throws Exception {
        FileId fileId = new FileId("01H8INTEGRATION01");
        byte[] content = "hello-storage-integration".getBytes();

        // store() 需要 PENDING_UPLOAD 状态的 FileMetadata
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", content.length, "text/plain",
            FileUsage.SOURCE, "annuity", "biz-app", BatchId.of("BATCH_TEST"),
            "local-default", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        storageGateway.store(fileId, new ByteArrayInputStream(content), content.length);

        // 偏差说明: 实际 store() 不会修改 FileMetadata 状态，需手动过渡到 UPLOADED
        // 使后续 open()/computeMd5() 能从 file.storageKey() 取到正确的 key
        String predictedStorageKey = predictStorageKey(file);
        file.markUploaded(predictedStorageKey, null);

        try (InputStream opened = storageGateway.open(fileId)) {
            String md5 = storageGateway.computeMd5(fileId);
            assertThat(md5).isNotBlank();
            assertThat(opened.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("Local 后端: copy 操作应返回 CopyResult (newFileId + newStorageKey)")
    void local_backend_copy_should_return_copy_result() {
        FileId srcFileId = new FileId("01H8INTEGRATION02");
        byte[] content = "copy-source-content".getBytes();

        // 先 store 源文件
        FileMetadata srcFile = FileMetadata.create(
            srcFileId, "source.txt", content.length, "text/plain",
            FileUsage.SOURCE, "annuity", "biz-app", BatchId.of("BATCH_TEST"),
            "local-default", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(srcFileId)).thenReturn(srcFile);
        storageGateway.store(srcFileId, new ByteArrayInputStream(content), content.length);

        // 偏差说明: 手动过渡到 UPLOADED，使 copy() 能取到 srcFile.storageKey()
        String predictedStorageKey = predictStorageKey(srcFile);
        srcFile.markUploaded(predictedStorageKey, null);

        // copy: srcFile 已有 storageKey (store 后 markUploaded 设置)
        CopyResult copyResult = storageGateway.copy(
            srcFileId, FileUsage.EXPORT, BatchId.of("BATCH_TEST")
        );

        assertThat(copyResult).isNotNull();
        assertThat(copyResult.newFileId()).isNotNull();
        assertThat(copyResult.newStorageKey()).isNotBlank();
        assertThat(copyResult.newFileId()).isNotEqualTo(srcFileId);
    }

    @Test
    @DisplayName("Local 后端: exists 应正确判断文件存在性")
    void local_backend_exists_should_reflect_storage_state() {
        FileId fileId = new FileId("01H8INTEGRATION03");
        byte[] content = "exists-test".getBytes();

        FileMetadata file = FileMetadata.create(
            fileId, "exists.txt", content.length, "text/plain",
            FileUsage.SOURCE, "annuity", "biz-app", BatchId.of("BATCH_TEST"),
            "local-default", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        // store 前：load 返回空 → exists 返回 false
        when(metadataRepository.load(fileId)).thenReturn(Optional.empty());
        assertThat(storageGateway.exists(fileId)).isFalse();

        // store
        storageGateway.store(fileId, new ByteArrayInputStream(content), content.length);

        // 偏差说明: 手动过渡到 UPLOADED，使 exists() 能取到 file.storageKey() 检查物理文件
        String predictedStorageKey = predictStorageKey(file);
        file.markUploaded(predictedStorageKey, null);

        // store 后：load 返回 file → exists 通过文件系统判断返回 true
        when(metadataRepository.load(fileId)).thenReturn(Optional.of(file));
        assertThat(storageGateway.exists(fileId)).isTrue();
    }

    @Test
    @DisplayName("StorageTargetResolver 应根据 usage 返回正确的 StorageTarget")
    void resolver_should_return_correct_target_by_usage() {
        StorageTarget sourceTarget = targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity");
        assertThat(sourceTarget).isNotNull();
        assertThat(sourceTarget.type()).isEqualTo(StorageType.LOCAL);
        assertThat(sourceTarget.targetId()).isEqualTo("local-default");

        StorageTarget exportTarget = targetResolver.resolveByUsage(FileUsage.EXPORT, "annuity");
        assertThat(exportTarget).isNotNull();
        assertThat(exportTarget.type()).isEqualTo(StorageType.LOCAL);
        assertThat(exportTarget.targetId()).isEqualTo("local-default");
    }

    /**
     * 复制 FileStorageRouter.generateStorageKey() 的逻辑，预测 storageKey。
     * 格式: {bizType}/{date}/{batchId}/{fileId}/{originalName}
     */
    private static String predictStorageKey(FileMetadata file) {
        String datePartition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.join("/",
            file.bizType() != null ? file.bizType() : "default",
            datePartition,
            file.businessBatchId() != null ? file.businessBatchId().value() : "no-batch",
            file.id().value(),
            file.originalName()
        );
    }
}
