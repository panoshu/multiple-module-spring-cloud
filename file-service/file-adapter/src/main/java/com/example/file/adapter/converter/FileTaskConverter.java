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
  @Mapping(target = "splitKey", source = "splitKey")
  @Mapping(target = "totalRows", source = "totalRows")
  @Mapping(target = "validRows", source = "validRows")
  @Mapping(target = "invalidRows", source = "invalidRows")
  @Mapping(target = "status", source = "status")
  SubTaskDTO toSubTaskDTO(FileTaskDetailResult.SubTaskSummaryItem item);
}
