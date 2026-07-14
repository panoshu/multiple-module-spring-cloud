package com.example.file.infrastructure.entity;

import com.example.shared.file.types.constant.FileStatus;
import com.example.shared.file.types.constant.StorageType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Table(value = "file_record")
public class FileRecord implements Serializable {

  @Id(keyType = KeyType.None) // ID 由 idService 生成，此处不自增
  private String id;

  private String originalName;
  private String extension;
  private Long size;
  private String mimeType;

  // 哈希值
  private String hash;

  // 存储信息
  private StorageType storageType; // 枚举会自动转 String
  private String bucket;
  private String storageKey;

  // 状态与权限
  private FileStatus status;
  private String bizType;
  private String ownerId;
  private String acl;

  // 【关键】JSON 类型映射
  // 自动将 Map 转为 DB JSON 字符串，读取时自动转回 Map
  @Column(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> metadata;

  // 审计与控制
  @Column(onInsertValue = "now()")
  private LocalDateTime createTime;

  @Column(onInsertValue = "now()", onUpdateValue = "now()")
  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean isDeleted;

  @Column(version = true)
  private Integer revision;
}
