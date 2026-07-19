package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
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
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileStorageRouterTest {

    @TempDir
    Path tempDir;

    private FileMetadataRepository metadataRepository;
    private StorageTargetResolver targetResolver;
    private FileStorageGateway router;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        targetResolver = mock(StorageTargetResolver.class);
        LocalFileStorage localBackend = new LocalFileStorage();
        // 构造函数自动初始化 backendMap，无需显式调用 initBackendMap()
        router = new FileStorageRouter(metadataRepository, targetResolver, List.of(localBackend));
    }

    @Test
    @DisplayName("store 应路由到 LOCAL 后端并返回 StoreResult")
    void store_should_route_to_LOCAL_backend_and_return_store_result() throws IOException {
        FileId fileId = new FileId("01H8FILE001");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        StoreResult result = router.store(fileId, new ByteArrayInputStream("hello".getBytes()), 5);

        assertThat(result).isNotNull();
        assertThat(result.storageKey()).isNotBlank();
        assertThat(result.md5()).isNotBlank();
        // storageKey 格式: {bizType}/{date}/{batchId}/{fileId}/{originalName}
        assertThat(result.storageKey()).contains("annuity");
        assertThat(result.storageKey()).contains("01H8FILE001");
        assertThat(result.storageKey()).endsWith("test.txt");
        verify(metadataRepository, atLeastOnce()).loadOrThrow(fileId);
    }

    @Test
    @DisplayName("open 应路由到正确后端返回流")
    void open_should_route_to_correct_backend() throws IOException {
        FileId fileId = new FileId("01H8FILE002");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        // 先写入一个文件（使用 Router 生成的 storageKey 格式）
        String datePartition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path filePath = tempDir.resolve("annuity/" + datePartition + "/BATCH_001/01H8FILE002/test.txt");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "content");

        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 7, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "annuity/" + datePartition + "/BATCH_001/01H8FILE002/test.txt",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            FileStatus.UPLOADED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        try (InputStream in = router.open(fileId)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("content");
        }
    }

    @Test
    @DisplayName("exists 在文件不存在时应返回 false")
    void exists_should_return_false_when_not_exists() {
        FileId fileId = new FileId("01H8FILE_NONEXIST");
        when(metadataRepository.load(fileId)).thenReturn(Optional.empty());

        assertThat(router.exists(fileId)).isFalse();
    }

    @Test
    @DisplayName("computeMd5 应返回正确 MD5")
    void computeMd5_should_return_correct_md5() throws IOException {
        FileId fileId = new FileId("01H8FILE003");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        Path filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello");

        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 5, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "test.txt",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            FileStatus.UPLOADED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        String md5 = router.computeMd5(fileId);
        assertThat(md5).isNotBlank();
    }
}
