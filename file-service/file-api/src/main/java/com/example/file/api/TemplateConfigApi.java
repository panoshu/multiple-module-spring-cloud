package com.example.file.api;

import com.example.file.api.request.ActivateTemplateConfigRequest;
import com.example.file.api.request.GetTemplateConfigRequest;
import com.example.file.api.request.SaveTemplateConfigRequest;
import com.example.file.api.response.TemplateConfigDTO;
import com.example.file.api.response.TemplateConfigIdResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/file/template-configs")
public interface TemplateConfigApi {

  @PostExchange("/save")
  ApiResult<TemplateConfigIdResponse> save(@RequestBody @Valid SaveTemplateConfigRequest request);

  @PostExchange("/get")
  ApiResult<TemplateConfigDTO> get(@RequestBody @Valid GetTemplateConfigRequest request);

  @PostExchange("/activate")
  ApiResult<Void> activate(@RequestBody @Valid ActivateTemplateConfigRequest request);
}
