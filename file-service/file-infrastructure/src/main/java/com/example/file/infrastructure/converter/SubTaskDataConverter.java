package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.SubTaskData;
import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.domain.model.valueobject.BusinessContext;
import com.example.file.domain.model.valueobject.ValidationError;
import com.example.file.infrastructure.entity.SubTaskDataDO;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface SubTaskDataConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mapping(target = "id", expression = "java(subTask.id().value())")
  @Mapping(target = "fileTaskId", expression = "java(subTask.fileTaskId().value())")
  @Mapping(target = "bizType", expression = "java(bizTypeToString(subTask.bizType()))")
  @Mapping(target = "splitKeyValue", expression = "java(subTask.splitKeyValue())")
  @Mapping(target = "context", expression = "java(businessContextToJson(subTask.context()))")
  @Mapping(target = "properties", expression = "java(mapToJson(subTask.properties()))")
  @Mapping(target = "tables", expression = "java(tablesToJson(subTask.tables()))")
  @Mapping(target = "rowCount", expression = "java(subTask.rowCount())")
  @Mapping(target = "status", expression = "java(subTaskStatusToString(subTask.status()))")
  @Mapping(target = "validationErrors", expression = "java(validationErrorListToJson(subTask.validationErrors()))")
  @Mapping(target = "expiresAt", expression = "java(subTask.expiresAt())")
  @Mapping(target = "consumedAt", expression = "java(subTask.consumedAt())")
  @Mapping(target = "createdBy", expression = "java(subTask.createdBy().value())")
  @Mapping(target = "updatedBy", expression = "java(subTask.updatedBy().value())")
  @Mapping(target = "createTime", expression = "java(subTask.createdAt())")
  @Mapping(target = "updateTime", expression = "java(subTask.updatedAt())")
  @Mapping(target = "version", expression = "java((int) subTask.version().value())")
  @Mapping(target = "deleted", constant = "false")
  SubTaskDataDO toDO(SubTaskData subTask);

  @Mapping(target = "id", source = "id", qualifiedByName = "toSubTaskId")
  @Mapping(target = "fileTaskId", source = "fileTaskId", qualifiedByName = "toFileTaskId")
  @Mapping(target = "bizType", source = "bizType", qualifiedByName = "stringToBizType")
  @Mapping(target = "context", source = "context", qualifiedByName = "jsonToBusinessContext")
  @Mapping(target = "properties", source = "properties", qualifiedByName = "jsonToMap")
  @Mapping(target = "tables", source = "tables", qualifiedByName = "jsonToTables")
  @Mapping(target = "status", source = "status", qualifiedByName = "stringToSubTaskStatus")
  @Mapping(target = "validationErrors", source = "validationErrors", qualifiedByName = "jsonToValidationErrorList")
  @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
  @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
  @Mapping(target = "createdAt", source = "createTime")
  @Mapping(target = "updatedAt", source = "updateTime")
  @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
  SubTaskData toDomain(SubTaskDataDO aDo);

  @Named("toSubTaskId")
  default SubTaskId toSubTaskId(String id) {
    return id != null ? SubTaskId.of(id) : null;
  }

  @Named("toFileTaskId")
  default FileTaskId toFileTaskId(String id) {
    return id != null ? FileTaskId.of(id) : null;
  }

  @Named("toUserNo")
  default UserNo toUserNo(String userNo) {
    return userNo != null ? UserNo.of(userNo) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer version) {
    return version != null ? Version.of(version.longValue()) : null;
  }

  default String bizTypeToString(BizType bizType) {
    return bizType != null ? bizType.value() : null;
  }

  @Named("stringToBizType")
  default BizType stringToBizType(String bizType) {
    return bizType != null ? BizType.of(bizType) : null;
  }

  default String subTaskStatusToString(SubTaskStatus status) {
    return status != null ? status.name() : null;
  }

  @Named("stringToSubTaskStatus")
  default SubTaskStatus stringToSubTaskStatus(String status) {
    return status != null ? SubTaskStatus.valueOf(status) : null;
  }

  default String businessContextToJson(BusinessContext context) {
    if (context == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(context.variables());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化业务上下文失败", e);
    }
  }

  @Named("jsonToBusinessContext")
  default BusinessContext jsonToBusinessContext(String json) {
    if (json == null || json.isBlank()) {
      return BusinessContext.empty();
    }
    try {
      Map<String, Object> variables = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
      });
      return new BusinessContext(variables);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化业务上下文失败", e);
    }
  }

  default String mapToJson(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化Map失败", e);
    }
  }

  @Named("jsonToMap")
  default Map<String, Object> jsonToMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化Map失败", e);
    }
  }

  default String tablesToJson(Map<String, List<Map<String, Object>>> tables) {
    if (tables == null || tables.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(tables);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化表格数据失败", e);
    }
  }

  @Named("jsonToTables")
  default Map<String, List<Map<String, Object>>> jsonToTables(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, List<Map<String, Object>>>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化表格数据失败", e);
    }
  }

  default String validationErrorListToJson(List<ValidationError> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化校验错误列表失败", e);
    }
  }

  @Named("jsonToValidationErrorList")
  default List<ValidationError> jsonToValidationErrorList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<ValidationError>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化校验错误列表失败", e);
    }
  }
}
