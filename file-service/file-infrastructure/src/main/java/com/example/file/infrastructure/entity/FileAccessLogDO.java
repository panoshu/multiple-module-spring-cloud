package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件访问流水 DO
 * <p>
 * 对应表 t_file_access_log，记录 APPLY（申请 token）与 ACCESS（实际访问）双记录用于审计。
 */
@Data
@Table("t_file_access_log")
public class FileAccessLogDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String fileId;
    private String action;
    private String usage;
    private String customerNo;
    private String productNo;
    private String operator;
    private String sourceApp;
    private String sourceIp;
    private String tokenHash;
    private String result;
    private String failReason;
    private LocalDateTime occurAt;

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
