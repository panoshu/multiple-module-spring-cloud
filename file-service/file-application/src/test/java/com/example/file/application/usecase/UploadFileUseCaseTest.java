package com.example.file.application.usecase;

import com.example.file.application.command.UploadFileCommand;
import com.example.file.application.result.UploadFileResult;
import com.example.file.domain.model.parse.aggregate.ParseTask;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.domain.repository.TemplateConfigRepository;
import com.example.file.domain.service.TemplateRoutingService;
import com.example.file.types.FileTaskId;
import com.example.shared.identifier.contract.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 上传解析任务的 requestNo 幂等行为（spec §11.4 / §21.2）。 */
@DisplayName("上传解析任务的 requestNo 幂等")
class UploadFileUseCaseTest {

  private IdService idService;
  private ParseTaskRepository parseTaskRepository;
  private TemplateConfigRepository configRepository;
  private ParseFileUseCase parseFileUseCase;
  private TemplateRoutingService routingService;
  private UploadFileUseCase useCase;

  private static UploadFileCommand command(String category, String clientRequestNo) {
    return new UploadFileCommand("import_declare", category, Map.of(), "TPL-1",
      "data.xlsx", null, "U-1", clientRequestNo);
  }

  @BeforeEach
  void setUp() {
    idService = mock(IdService.class);
    parseTaskRepository = mock(ParseTaskRepository.class);
    configRepository = mock(TemplateConfigRepository.class);
    parseFileUseCase = mock(ParseFileUseCase.class);
    routingService = mock(TemplateRoutingService.class);
    useCase = new UploadFileUseCase(
      idService, parseTaskRepository, configRepository, parseFileUseCase, routingService);
  }

  @Test
  @DisplayName("同一 clientRequestNo 重复上传直接返回原任务 ID，不重复创建")
  void execute_sameRequestNo_returnsExistingWithoutCreating() {
    ParseTask existing = mock(ParseTask.class);
    when(existing.id()).thenReturn(FileTaskId.of("tsk-existing"));
    when(existing.status()).thenReturn(com.example.file.domain.model.enums.TaskStatus.PENDING);
    when(parseTaskRepository.findByRequestNo("SE-9")).thenReturn(Optional.of(existing));

    UploadFileResult result = useCase.execute(command("FORM", "SE-9"));

    assertThat(result.fileTaskId()).isEqualTo("tsk-existing");
    verify(parseTaskRepository).findByRequestNo("SE-9");
    verify(parseTaskRepository, never()).save(any());
    verifyNoInteractions(idService);
  }

  @Test
  @DisplayName("clientRequestNo 为空时不查重（过渡期旧调用方走原创建路径）")
  void execute_blankRequestNo_doesNotQueryByRequestNo() {
    when(idService.nextId(FileTaskId.class)).thenReturn(FileTaskId.of("tsk-new"));
    when(configRepository.findActive(any())).thenReturn(Optional.empty());

    useCase.execute(command("MATERIAL", "   "));

    verify(parseTaskRepository, never()).findByRequestNo(any());
  }
}
