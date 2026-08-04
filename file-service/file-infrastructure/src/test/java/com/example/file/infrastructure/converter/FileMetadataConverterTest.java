package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetadataConverterTest {

  private final FileMetadataConverter converter = new FileMetadataConverter() {
  };

  @Test
  @DisplayName("toDO 应将 FileId 转换为 String")
  void toDO_should_convert_FileId_to_String() {
    FileMetadata file = FileMetadata.create(
      new FileId("01H8FILEID001"), "test.xlsx", 1024, "application/octet-stream",
      FileUsage.SOURCE, "annuity", "business-core", BatchId.of("BATCH_001"),
      "oss-source", StorageType.OSS, UserNo.of("u1"), null
    );

    FileMetadataDO aDo = converter.toDO(file);

    assertThat(aDo.getId()).isEqualTo("01H8FILEID001");
    assertThat(aDo.getUsage()).isEqualTo("SOURCE");
    assertThat(aDo.getStorageType()).isEqualTo("OSS");
    assertThat(aDo.getStatus()).isEqualTo("PENDING_UPLOAD");
    assertThat(aDo.getBusinessBatchId()).isEqualTo("BATCH_001");
  }

  @Test
  @DisplayName("toDomain 应将 String 转换为 FileId")
  void toDomain_should_convert_String_to_FileId() {
    FileMetadataDO aDo = buildUploadedDO();

    FileMetadata file = converter.toDomain(aDo);

    assertThat(file.id()).isEqualTo(new FileId("01H8FILEID001"));
    assertThat(file.usage()).isEqualTo(FileUsage.SOURCE);
    assertThat(file.storageType()).isEqualTo(StorageType.OSS);
    assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
    assertThat(file.businessBatchId()).isEqualTo(BatchId.of("BATCH_001"));
  }

  private FileMetadataDO buildUploadedDO() {
    FileMetadataDO aDo = new FileMetadataDO();
    aDo.setId("01H8FILEID001");
    aDo.setOriginalName("test.xlsx");
    aDo.setSize(1024L);
    aDo.setContentType("application/octet-stream");
    aDo.setMd5("md5hash");
    aDo.setTargetId("oss-source");
    aDo.setStorageType("OSS");
    aDo.setStorageKey("storage/key");
    aDo.setUsage("SOURCE");
    aDo.setBizType("annuity");
    aDo.setSourceApp("business-core");
    aDo.setBusinessBatchId("BATCH_001");
    aDo.setStatus("UPLOADED");
    aDo.setUploadedBy("u1");
    aDo.setUploadedAt(LocalDateTime.now());
    aDo.setExpiresAt(null);
    aDo.setCreatedBy("u1");
    aDo.setUpdatedBy("u1");
    aDo.setCreateTime(LocalDateTime.now());
    aDo.setUpdateTime(LocalDateTime.now());
    aDo.setDeleted(false);
    aDo.setVersion(0);
    return aDo;
  }
}
