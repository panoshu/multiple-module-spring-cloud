package com.example.file.api;

import com.example.file.api.request.CancelFileTaskRequest;
import com.example.file.api.request.GetByRequestNoRequest;
import com.example.file.api.request.GetFileTaskRequest;
import com.example.file.api.request.ListSubTasksRequest;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskDTO;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.file.api.response.SubTaskDTO;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/file/tasks")
public interface FileTaskApi {

  @PostExchange("/upload")
  ApiResult<FileTaskIdResponse> upload(@RequestBody @Valid UploadFileRequest request);

  @PostExchange("/get")
  ApiResult<FileTaskDTO> get(@RequestBody @Valid GetFileTaskRequest request);

  /** 按外部协作幂等键查询任务 ID（响应丢失对账，spec §11.4）。 */
  @PostExchange("/get-by-request-no")
  ApiResult<FileTaskIdResponse> getByRequestNo(@RequestBody @Valid GetByRequestNoRequest request);

  @PostExchange("/sub-tasks")
  ApiResult<PageData<SubTaskDTO>> listSubTasks(@RequestBody @Valid ListSubTasksRequest request);

  @PostExchange("/cancel")
  ApiResult<Void> cancel(@RequestBody @Valid CancelFileTaskRequest request);
}
