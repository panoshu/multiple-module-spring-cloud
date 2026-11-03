package com.example.file.adapter.converter;

import com.example.file.api.request.GenerateOutboundFileRequest;
import com.example.file.api.response.OutboundFileIdResponse;
import com.example.file.application.command.GenerateOutboundFileCommand;
import com.example.file.domain.event.OutboundFileGeneratedEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OutboundConverter {

  GenerateOutboundFileCommand toCommand(GenerateOutboundFileRequest request);

  @Mapping(target = "fileTaskId", source = "fileTaskId.value")
  @Mapping(target = "generatedFileCount", source = "generatedFiles")
  OutboundFileIdResponse toResponse(OutboundFileGeneratedEvent event);

  default int mapGeneratedFileCount(java.util.List<?> generatedFiles) {
    return generatedFiles != null ? generatedFiles.size() : 0;
  }
}
