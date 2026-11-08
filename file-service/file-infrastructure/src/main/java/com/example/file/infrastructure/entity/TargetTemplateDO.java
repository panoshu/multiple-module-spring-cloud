package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_target_template")
public class TargetTemplateDO {

  @Id(keyType = KeyType.None)
  private String id;

  private String templateId;
  private String templateName;
  private String fileId;
  private String description;

  private String createdBy;
  private String updatedBy;

  // createTime/updateTime 由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Integer deleted;

  @Column(version = true)
  private Integer version;
}
