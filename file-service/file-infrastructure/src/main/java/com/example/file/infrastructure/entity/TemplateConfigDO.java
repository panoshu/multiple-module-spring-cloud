package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_template_config")
public class TemplateConfigDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String bizType;
    private String templateVersion;
    private String errorPolicy;
    private String canonicalModel;
    private String validationRules;
    private String derivationRules;
    private String splitConfig;
    private String sourceTemplates;
    private String targetTemplateRef;
    private String targetMapping;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

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
