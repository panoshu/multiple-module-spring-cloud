package com.example.file.adapter.controllers;

import com.example.file.adapter.converter.OutboundConverter;
import com.example.file.api.OutboundApi;
import com.example.file.api.request.GenerateOutboundFileRequest;
import com.example.file.api.response.OutboundFileIdResponse;
import com.example.file.application.usecase.GenerateOutboundFileUseCase;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/file/outbound")
@RequiredArgsConstructor
public class OutboundController implements OutboundApi {

  private final GenerateOutboundFileUseCase generateUseCase;
  private final com.example.shared.domain.event.EventBus eventBus;
  private final OutboundConverter converter;

  @Override
  public ApiResult<OutboundFileIdResponse> generate(GenerateOutboundFileRequest request) {
    log.info("触发出站文件生成: fileTaskId={}, targetTemplateId={}",
      request.fileTaskId(), request.targetTemplateId());
    var event = generateUseCase.execute(converter.toCommand(request));
    // C1（批次4 导出闭环）：生成事件经 event-starter 发布——消费方 integration
    // 监听后流式拉取文件做协议适配提交（事务已提交，SUPPORTS 语义下独立持久化投递日志）
    eventBus.publish(event);
    return ApiResult.success(converter.toResponse(event));
  }
}
