package com.example.file.application.usecase;

import com.example.file.application.command.ParseFileCommand;
import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.entity.SourceTemplateDef;
import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.aggregate.root.SubTaskData;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.valueobject.BusinessContext;
import com.example.file.domain.model.valueobject.CanonicalData;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.service.ParseContext;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.domain.repository.TemplateConfigRepository;
import com.example.file.domain.service.CanonicalModelBuilder;
import com.example.file.domain.service.DataDeriver;
import com.example.file.domain.service.DataValidator;
import com.example.file.domain.service.RegionStateMachine;
import com.example.file.domain.service.SourceTemplateIdentifier;
import com.example.file.domain.service.TaskSplitter;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ParseFileUseCase {

  private final ParseTaskRepository parseTaskRepository;
  private final SubTaskDataRepository subTaskRepository;
  private final TemplateConfigRepository configRepository;
  private final FileStorageGateway fileStorage;
  private final ExcelParser excelParser;
  private final ExpressionEvaluator evaluator;
  private final SourceTemplateIdentifier identifier;
  private final RegionStateMachine stateMachine;
  private final CanonicalModelBuilder modelBuilder;
  private final DataDeriver deriver;
  private final DataValidator validator;
  private final TaskSplitter splitter;

  public ParseFileUseCase(ParseTaskRepository parseTaskRepository,
                          SubTaskDataRepository subTaskRepository,
                          TemplateConfigRepository configRepository,
                          FileStorageGateway fileStorage,
                          ExcelParser excelParser,
                          ExpressionEvaluator evaluator,
                          SourceTemplateIdentifier identifier,
                          RegionStateMachine stateMachine,
                          CanonicalModelBuilder modelBuilder,
                          DataDeriver deriver,
                          DataValidator validator,
                          TaskSplitter splitter) {
    this.parseTaskRepository = parseTaskRepository;
    this.subTaskRepository = subTaskRepository;
    this.configRepository = configRepository;
    this.fileStorage = fileStorage;
    this.excelParser = excelParser;
    this.evaluator = evaluator;
    this.identifier = identifier;
    this.stateMachine = stateMachine;
    this.modelBuilder = modelBuilder;
    this.deriver = deriver;
    this.validator = validator;
    this.splitter = splitter;
  }

  @Transactional
  public void execute(ParseFileCommand cmd) {
    FileTaskId taskId = FileTaskId.of(cmd.fileTaskId());
    ParseTask task = parseTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Task not found: " + cmd.fileTaskId()));

    UserNo operator = UserNo.of(cmd.operator());
    task.markParsing();
    parseTaskRepository.save(task);

    TemplateConfig config = configRepository.findActive(task.bizType())
        .orElseThrow(() -> new IllegalStateException("No active template config for bizType: " + task.bizType().value()));

    try (InputStream inputStream = fileStorage.open(task.sourceFileId())) {
      RawRowStream identifyStream = excelParser.openStream(inputStream);
      Optional<SourceTemplateDef> matched = identifier.identify(config, identifyStream);

      if (matched.isEmpty()) {
        task.markFailed(new TaskError(
            "TEMPLATE_NOT_MATCHED",
            "未匹配到任何源模板",
            "bizType: " + task.bizType().value()
        ));
        parseTaskRepository.save(task);
        return;
      }

      task.markSplitting();
      parseTaskRepository.save(task);

      try (InputStream parseStream = fileStorage.open(task.sourceFileId())) {
        RawRowStream stream = excelParser.openStream(parseStream);

        List<RegionParseResult> regions = stateMachine.drive(stream, matched.get().regions(), new ParseContext(matched.get().regions()));

        CanonicalData canonical = modelBuilder.build(regions, matched.get().regions());

        Map<String, Object> derivedProps = deriver.derive(canonical.properties(), config.derivationRules(), evaluator);
        CanonicalData derivedData = CanonicalData.of(derivedProps, canonical.tables());

        List<SplitUnit> units = splitter.split(
            toFlatMap(derivedData),
            config.splitConfig()
        );

        task.markValidating();
        parseTaskRepository.save(task);

        for (int i = 0; i < units.size(); i++) {
          SplitUnit unit = units.get(i);
          SubTaskId subTaskId = SubTaskId.generate();

          CanonicalData unitData = fromFlatMap(unit.data());

          ValidationResult vr = validator.validate(
              unitData.properties(),
              config.validationRules(),
              config.errorPolicy(),
              evaluator
          );

          int rowCount = unitData.tables().values().stream()
              .mapToInt(List::size)
              .sum();

          SubTaskData subTask = SubTaskData.create(
              subTaskId,
              taskId,
              task.bizType(),
              unit.splitKey(),
              BusinessContext.empty(),
              unitData.properties(),
              unitData.tables(),
              rowCount,
              operator,
              null
          );
          subTask.applyValidationResult(vr);
          subTaskRepository.save(subTask);

          task.recordSubTask(subTask.toSummary());
        }

        if (task.invalidCount() == 0) {
          task.markSuccess();
        } else {
          task.markPartialSuccess(task.invalidCount());
        }
        parseTaskRepository.save(task);
      }
    } catch (Exception ex) {
      task.markFailed(new TaskError(
          "PARSE_ERROR",
          "解析失败: " + ex.getMessage(),
          "stack: " + ex
      ));
      parseTaskRepository.save(task);
      throw new RuntimeException("Parse failed for task: " + cmd.fileTaskId(), ex);
    }
  }

  private java.util.Map<String, Object> toFlatMap(CanonicalData data) {
    var flat = new java.util.LinkedHashMap<>(data.properties());
    data.tables().forEach(flat::put);
    return flat;
  }

  @SuppressWarnings("unchecked")
  private CanonicalData fromFlatMap(Map<String, Object> flat) {
    var props = new java.util.LinkedHashMap<String, Object>();
    var tables = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
    for (var e : flat.entrySet()) {
      if (e.getValue() instanceof List<?> list && !list.isEmpty()
          && list.get(0) instanceof Map) {
        tables.put(e.getKey(), (List<Map<String, Object>>) list);
      } else {
        props.put(e.getKey(), e.getValue());
      }
    }
    return CanonicalData.of(props, tables);
  }
}
