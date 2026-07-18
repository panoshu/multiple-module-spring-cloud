package com.example.file.api;

import com.example.file.api.request.FetchRowsRequest;
import com.example.file.api.response.ParsedRowDTO;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/file/parsed-data")
public interface ParsedDataApi {

  @PostExchange("/rows")
  ApiResult<PageData<ParsedRowDTO>> fetchRows(@RequestBody @Valid FetchRowsRequest request);
}
