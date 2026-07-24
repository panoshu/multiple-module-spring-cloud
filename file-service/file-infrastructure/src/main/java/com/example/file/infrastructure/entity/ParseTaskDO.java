package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_parse_task")
public class ParseTaskDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String bizType;
    private String templateCode;
    private String sourceFileName;
    private String sourceFileId;
    private String status;
    private String errorPolicy;
    private String splitKeys;
    private Integer totalRows;
    private Integer subTaskCount;
    private Integer validCount;
    private Integer invalidCount;
    private String subTaskSummaries;
    private String errors;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

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
