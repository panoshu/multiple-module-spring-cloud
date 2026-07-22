package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用步骤处理器：调用 file-service 触发异步文件解析
 * <p>
 * <b>【职责边界】</b>
 * <p>本处理器属于核心编排域 (kernel)，是一个开箱即用的标准化组件。
 * 通过 {@link FileTaskApi} 向 file-service 派发异步解析任务，并将流程挂起等待回调唤醒。
 *
 * <b>【配置中心 JSON 配置】</b>
 * <p>将本步骤的 {@code mainProcessor} 配置为 {@code "fileServiceParseHandler"} 即可启用。
 *
 * <b>【异步唤醒机制】</b>
 * <p>本处理器返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 后，引擎会挂起并提交事务。
 * file-service 完成解析后通过集成事件回调，由监听器驱动引擎进入下一节点。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceParseHandler implements StepActionHandler {

  private static final String BIZ_TYPE_FORM_DETAIL = "FORM_DETAIL";

  private final FileTaskApi fileTaskApi;

  @Override
  public String handlerName() {
    return "fileServiceParseHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始派发文件解析任务, applicationId={}", app.id());

    // 1. 领域行为：校验申请单已挂载待解析文件
    app.requireFileForParsing();

    // 2. 构造文件服务上传请求 (仅元数据，实际文件流由 BFF 层直传)
    UploadFileRequest request = new UploadFileRequest(
      BIZ_TYPE_FORM_DETAIL,
      resolveTemplateCode(context),
      resolveFileName(context),
      0L,
      app.createdBy().value(),
      app.id().value()
    );

    // 3. 远程调用 file-service 派发异步解析任务
    ApiResult<FileTaskIdResponse> result;
    try {
      result = fileTaskApi.upload(request);
    } catch (Exception e) {
      log.error("调用 file-service 派发解析任务异常, applicationId={}", app.id(), e);
      return StepExecutionStatus.FAILED;
    }

    if (result == null || !result.isSuccess() || result.data() == null) {
      log.error("file-service 派发解析任务失败, applicationId={}, result={}", app.id(), result);
      return StepExecutionStatus.FAILED;
    }

    log.info("已成功派发文件解析任务, applicationId={}, fileTaskId={}",
      app.id(), result.data().fileTaskId());

    // 4. 挂起引擎，等待 file-service 解析完成后通过事件回调唤醒
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }

  /**
   * 根据业务类型推导解析模板编码
   */
  private String resolveTemplateCode(BusinessMetaContext context) {
    return context.businessType().name() + "_TEMPLATE";
  }

  /**
   * 使用计划编号作为文件名标识，便于在文件服务侧追溯业务来源
   */
  private String resolveFileName(BusinessMetaContext context) {
    return context.planNo() != null ? context.planNo().value() + ".json" : "form-detail.json";
  }
}
