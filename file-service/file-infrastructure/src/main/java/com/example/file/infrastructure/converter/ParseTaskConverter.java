package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.TaskStatus;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.infrastructure.entity.ParseTaskDO;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.TemplateCode;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ParseTaskConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mapping(target = "id", expression = "java(task.id().value())")
  @Mapping(target = "bizType", expression = "java(bizTypeToString(task.bizType()))")
  @Mapping(target = "templateCode", expression = "java(templateCodeToString(task.templateCode()))")
  @Mapping(target = "sourceFileName", expression = "java(task.sourceFileName())")
  @Mapping(target = "sourceFileId", expression = "java(fileIdToString(task.sourceFileId()))")
  @Mapping(target = "status", expression = "java(taskStatusToString(task.status()))")
  @Mapping(target = "errorPolicy", expression = "java(errorPolicyToString(task.errorPolicy()))")
  @Mapping(target = "splitKeys", expression = "java(stringListToJson(task.splitKeys()))")
  @Mapping(target = "totalRows", expression = "java(task.totalRows())")
  @Mapping(target = "subTaskCount", expression = "java(task.subTaskCount())")
  @Mapping(target = "validCount", expression = "java(task.validCount())")
  @Mapping(target = "invalidCount", expression = "java(task.invalidCount())")
  @Mapping(target = "subTaskSummaries", expression = "java(subTaskSummaryListToJson(task.subTaskSummaries()))")
  @Mapping(target = "errors", expression = "java(taskErrorListToJson(task.errors()))")
  @Mapping(target = "startedAt", expression = "java(task.startedAt())")
  @Mapping(target = "finishedAt", expression = "java(task.finishedAt())")
  @Mapping(target = "createdBy", expression = "java(task.createdBy().value())")
  @Mapping(target = "updatedBy", expression = "java(task.updatedBy().value())")
  @Mapping(target = "createTime", expression = "java(task.createdAt())")
  @Mapping(target = "updateTime", expression = "java(task.updatedAt())")
  @Mapping(target = "version", expression = "java((int) task.version().value())")
  @Mapping(target = "deleted", constant = "false")
  ParseTaskDO toDO(ParseTask task);

  @Mapping(target = "id", source = "id", qualifiedByName = "toFileTaskId")
  @Mapping(target = "bizType", source = "bizType", qualifiedByName = "stringToBizType")
  @Mapping(target = "templateCode", source = "templateCode", qualifiedByName = "stringToTemplateCode")
  @Mapping(target = "sourceFileId", source = "sourceFileId", qualifiedByName = "toFileId")
  @Mapping(target = "status", source = "status", qualifiedByName = "stringToTaskStatus")
  @Mapping(target = "errorPolicy", source = "errorPolicy", qualifiedByName = "stringToErrorPolicy")
  @Mapping(target = "splitKeys", source = "splitKeys", qualifiedByName = "jsonToStringList")
  @Mapping(target = "subTaskSummaries", source = "subTaskSummaries", qualifiedByName = "jsonToSubTaskSummaryList")
  @Mapping(target = "errors", source = "errors", qualifiedByName = "jsonToTaskErrorList")
  @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
  @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
  @Mapping(target = "createdAt", source = "createTime")
  @Mapping(target = "updatedAt", source = "updateTime")
  @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
  @Mapping(target = "domainEvents", ignore = true)
  ParseTask toDomain(ParseTaskDO aDo);

  @Named("toFileTaskId")
  default FileTaskId toFileTaskId(String id) {
    return id != null ? FileTaskId.of(id) : null;
  }

  default String fileIdToString(FileId fileId) {
    return fileId != null ? fileId.value() : null;
  }

  @Named("toFileId")
  default FileId toFileId(String fileId) {
    return fileId != null ? new FileId(fileId) : null;
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

  default String templateCodeToString(TemplateCode templateCode) {
    return templateCode != null ? templateCode.value() : null;
  }

  @Named("stringToTemplateCode")
  default TemplateCode stringToTemplateCode(String templateCode) {
    return templateCode != null ? TemplateCode.of(templateCode) : null;
  }

  default String taskStatusToString(TaskStatus status) {
    return status != null ? status.name() : null;
  }

  @Named("stringToTaskStatus")
  default TaskStatus stringToTaskStatus(String status) {
    return status != null ? TaskStatus.valueOf(status) : null;
  }

  default String errorPolicyToString(ErrorPolicy errorPolicy) {
    return errorPolicy != null ? errorPolicy.name() : null;
  }

  @Named("stringToErrorPolicy")
  default ErrorPolicy stringToErrorPolicy(String errorPolicy) {
    return errorPolicy != null ? ErrorPolicy.valueOf(errorPolicy) : null;
  }

  default String stringListToJson(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化字符串列表失败", e);
    }
  }

  @Named("jsonToStringList")
  default List<String> jsonToStringList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化字符串列表失败", e);
    }
  }

  default String subTaskSummaryListToJson(List<SubTaskSummary> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化子任务摘要列表失败", e);
    }
  }

  @Named("jsonToSubTaskSummaryList")
  default List<SubTaskSummary> jsonToSubTaskSummaryList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<SubTaskSummary>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化子任务摘要列表失败", e);
    }
  }

  default String taskErrorListToJson(List<TaskError> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化任务错误列表失败", e);
    }
  }

  @Named("jsonToTaskErrorList")
  default List<TaskError> jsonToTaskErrorList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<List<TaskError>>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化任务错误列表失败", e);
    }
  }
}
