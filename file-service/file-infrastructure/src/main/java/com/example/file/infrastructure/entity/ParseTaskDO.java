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
    private String sourceFileRef;
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

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
