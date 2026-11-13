package com.example.file.domain.repository;

import com.example.file.domain.model.parse.aggregate.ParseTask;
import com.example.file.types.FileTaskId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

public interface ParseTaskRepository extends Repository<ParseTask, FileTaskId> {
  Optional<ParseTask> findById(FileTaskId id);

  void save(ParseTask task);

  /** 按外部协作幂等键查询（响应丢失对账，spec §11.4）。 */
  Optional<ParseTask> findByRequestNo(String requestNo);
}
