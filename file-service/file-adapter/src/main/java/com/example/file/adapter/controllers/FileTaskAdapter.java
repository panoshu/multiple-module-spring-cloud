package com.example.file.adapter.controllers;

import com.example.file.adapter.converter.FileTaskConverter;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.CancelFileTaskRequest;
import com.example.file.api.request.GetFileTaskRequest;
import com.example.file.api.request.ListSubTasksRequest;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskDTO;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.file.api.response.SubTaskDTO;
import com.example.file.application.result.FileTaskDetailResult;
import com.example.file.application.usecase.CancelFileTaskUseCase;
import com.example.file.application.usecase.GetFileTaskUseCase;
import com.example.file.application.usecase.UploadFileUseCase;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/file/tasks")
@RequiredArgsConstructor
public class FileTaskAdapter implements FileTaskApi {

  private final UploadFileUseCase uploadUseCase;
  private final GetFileTaskUseCase getUseCase;
  private final CancelFileTaskUseCase cancelUseCase;
  private final FileTaskConverter converter;

  @Override
  public ApiResult<FileTaskIdResponse> upload(UploadFileRequest request) {
    log.info("上传文件: bizType={}, fileName={}", request.bizType(), request.fileName());
    var result = uploadUseCase.execute(converter.toCommand(request));
    return ApiResult.success(converter.toIdResponse(result));
  }

  @Override
  public ApiResult<FileTaskDTO> get(GetFileTaskRequest request) {
    var result = getUseCase.execute(request.fileTaskId());
    return ApiResult.success(converter.toDTO(result));
  }

  @Override
  public ApiResult<PageData<SubTaskDTO>> listSubTasks(ListSubTasksRequest request) {
    var result = getUseCase.execute(request.fileTaskId());
    List<FileTaskDetailResult.SubTaskSummaryItem> allItems = result.subTasks();

    // 内存分页
    int total = allItems.size();
    int start = (request.page() - 1) * request.size();
    int end = Math.min(start + request.size(), total);
    List<SubTaskDTO> pageItems = allItems.subList(Math.max(0, start), end).stream()
      .map(converter::toSubTaskDTO)
      .toList();

    boolean hasMore = end < total;
    PageData<SubTaskDTO> pageData = new PageData<>(total, start, pageItems.size(), hasMore, pageItems);
    return ApiResult.success(pageData);
  }

  @Override
  public ApiResult<Void> cancel(CancelFileTaskRequest request) {
    cancelUseCase.execute(request.fileTaskId(), request.operator());
    return ApiResult.success();
  }
}
