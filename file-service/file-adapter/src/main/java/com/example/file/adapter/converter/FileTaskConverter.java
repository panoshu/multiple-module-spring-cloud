package com.example.file.adapter.converter;

import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskDTO;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.file.api.response.SubTaskDTO;
import com.example.file.application.command.UploadFileCommand;
import com.example.file.application.result.FileTaskDetailResult;
import com.example.file.application.result.UploadFileResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FileTaskConverter {

  @Mapping(target = "sourceFileName", source = "fileName")
  // sourceFileId 由应用层在上传完成后设置，Request 阶段无此信息
  @Mapping(target = "sourceFileId", ignore = true)
  UploadFileCommand toCommand(UploadFileRequest request);

  @Mapping(target = "fileTaskId", source = "fileTaskId")
  FileTaskIdResponse toIdResponse(UploadFileResult result);

  @Mapping(target = "fileTaskId", source = "fileTaskId")
  @Mapping(target = "bizType", source = "bizType")
  @Mapping(target = "templateCode", source = "templateCode")
  @Mapping(target = "fileName", source = "sourceFileName")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "subTaskCount", source = "subTaskCount")
  @Mapping(target = "totalRows", source = "totalRows")
  @Mapping(target = "validCount", source = "validCount")
  @Mapping(target = "invalidCount", source = "invalidCount")
  @Mapping(target = "errorMessage", source = "errorMessage")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "finishedAt", source = "finishedAt")
  FileTaskDTO toDTO(FileTaskDetailResult result);

  @Mapping(target = "subTaskId", source = "subTaskId")
  // fileTaskId 可从父级 FileTaskDTO 上下文获取，SubTaskSummaryItem 无需重复
  @Mapping(target = "fileTaskId", ignore = true)
  @Mapping(target = "splitKey", source = "splitKey")
  @Mapping(target = "totalRows", source = "totalRows")
  @Mapping(target = "validRows", source = "validRows")
  @Mapping(target = "invalidRows", source = "invalidRows")
  @Mapping(target = "status", source = "status")
  // createdAt 暂未在 SubTaskSummaryItem 中提供，后续如需可扩展
  @Mapping(target = "createdAt", ignore = true)
  SubTaskDTO toSubTaskDTO(FileTaskDetailResult.SubTaskSummaryItem item);
}
