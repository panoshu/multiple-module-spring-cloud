package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_metadata")
public class FileMetadataDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String originalName;
    private Long size;
    private String contentType;
    private String md5;

    private String targetId;
    private String storageType;
    private String storageKey;

    private String usage;
    private String bizType;
    private String sourceApp;
    private String businessBatchId;

    private String status;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    private String createdBy;
    private String updatedBy;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
