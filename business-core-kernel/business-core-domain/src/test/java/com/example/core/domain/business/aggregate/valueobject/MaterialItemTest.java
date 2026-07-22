package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.enums.material.RequirementType;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessLevel;
import com.example.shared.primitives.identity.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaterialItem 材料项测试")
class MaterialItemTest {

    @Test
    @DisplayName("removeUpload 应根据 fileId 移除对应文件")
    void removeUpload_shouldRemoveFileByFileId() {
        // given
        FileId fileId1 = new FileId("file-id-1");
        FileId fileId2 = new FileId("file-id-2");
        BusinessFile file1 = new BusinessFile(fileId1, "file1.pdf", "pdf", 1024L);
        BusinessFile file2 = new BusinessFile(fileId2, "file2.pdf", "pdf", 2048L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            Optional.empty()
        );
        item = item.withUpload(file1).withUpload(file2);

        // when
        MaterialItem result = item.removeUpload(fileId1);

        // then
        assertEquals(1, result.uploadInfo().get().files().size());
        assertFalse(result.uploadInfo().get().files().contains(file1));
        assertTrue(result.uploadInfo().get().files().contains(file2));
    }

    @Test
    @DisplayName("removeUpload 移除最后一个文件后 uploadInfo 应为空")
    void removeUpload_lastFileRemoved_uploadInfoShouldBeEmpty() {
        // given
        FileId fileId = new FileId("file-id-1");
        BusinessFile file = new BusinessFile(fileId, "file.pdf", "pdf", 1024L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            Optional.empty()
        );
        item = item.withUpload(file);

        // when
        MaterialItem result = item.removeUpload(fileId);

        // then
        assertTrue(result.uploadInfo().isEmpty());
    }

    @Test
    @DisplayName("removeUpload 传入不存在的 fileId 应保持原样")
    void removeUpload_nonExistentFileId_shouldReturnUnchanged() {
        // given
        FileId fileId1 = new FileId("file-id-1");
        FileId fileIdNotExist = new FileId("file-id-999");
        BusinessFile file1 = new BusinessFile(fileId1, "file1.pdf", "pdf", 1024L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            Optional.empty()
        );
        item = item.withUpload(file1);

        // when
        MaterialItem result = item.removeUpload(fileIdNotExist);

        // then
        assertEquals(1, result.uploadInfo().get().files().size());
    }
}
