package com.example.file.adapter.controllers;

import com.example.file.api.ParsedDataApi;
import com.example.file.api.request.FetchRowsRequest;
import com.example.file.api.response.ParsedRowDTO;
import com.example.file.application.usecase.FetchRowsUseCase;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析数据分页拉取端点（2026-11-08 批次2：数据消费闭环）.
 *
 * <p>设计契约：FileParsedEvent 的消费方凭 subTaskId 分页拉取 canonical 行数据
 * ——数据留在 file 域，消费方低内存消费、游标续传.</p>
 *
 * @author file-service
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ParsedDataAdapter implements ParsedDataApi {

  private final FetchRowsUseCase fetchRowsUseCase;

  @Override
  public ApiResult<PageData<ParsedRowDTO>> fetchRows(FetchRowsRequest request) {
    var paged = fetchRowsUseCase.execute(request);
    var info = paged.pageInfo();

    List<ParsedRowDTO> items = new ArrayList<>(paged.rows().size());
    int rowIndex = info.startPos();
    for (var data : paged.rows()) {
      items.add(new ParsedRowDTO(String.valueOf(rowIndex), rowIndex, data, true, null));
      rowIndex++;
    }
    PageData<ParsedRowDTO> page = new PageData<>(
      info.totalCount(), info.startPos(), info.returnedCount(), info.hasMore(), items);
    return ApiResult.success(page);
  }
}
