package com.example.integration.adapter.trade.mapper;

import com.example.integration.api.core.trade.dto.FileSubmissionRequestDTO;
import com.example.integration.api.core.trade.dto.FileSubmissionResponseDTO;
import com.example.integration.domain.trade.model.FileSubmissionResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 文件提交适配层转换器
 */
@Mapper(componentModel = "spring")
public interface FileSubmissionConverter {

  @Mapping(target = "status", expression = "java(result.status().name())")
  FileSubmissionResponseDTO toResponse(FileSubmissionResult result);
}
