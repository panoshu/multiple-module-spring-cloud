package com.example.file.interfaces.mapper;

import com.example.file.api.dto.BatchReadResponse;
import com.example.file.api.dto.FileProcessSummaryDto;
import com.example.file.api.dto.ReadResponse;
import com.example.file.application.dto.BatchReadResult;
import com.example.file.application.dto.FileProcessSummary;
import com.example.file.application.dto.ReadResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * Excel 网关 DTO 转换器
 * componentModel = SPRING: 让 MapStruct 自动生成带有 @Component 的实现类
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExcelGatewayMapper {

  /**
   * 单文件处理结果转换
   */
  ReadResponse toReadResponse(ReadResult appResult);

  /**
   * 批量处理结果转换
   * (MapStruct 会非常聪明地自动发现并调用下面的 toFileProcessSummaryDto 来转换 List 里的元素)
   */
  BatchReadResponse toBatchReadResponse(BatchReadResult appResult);

  /**
   * 内部嵌套列表元素转换
   */
  FileProcessSummaryDto toFileProcessSummaryDto(FileProcessSummary summary);
}
