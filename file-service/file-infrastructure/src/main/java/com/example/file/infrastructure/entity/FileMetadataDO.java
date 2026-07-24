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

    // Token 访问机制扩展字段（Task 13）
    private String accessScope;       // JSON 字符串（PostgreSQL JSONB）
    private String digest;            // 内容摘要（SM3）
    private String digestAlgorithm;   // 摘要算法: SM3

    private String createdBy;
    private String updatedBy;

    // createTime/updateTime 由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理
    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
