package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_sub_task_data")
public class SubTaskDataDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String fileTaskId;
    private String bizType;
    private String splitKeyValue;
    private String context;
    private String properties;
    private String tables;
    private Integer rowCount;
    private String status;
    private String validationErrors;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;

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
