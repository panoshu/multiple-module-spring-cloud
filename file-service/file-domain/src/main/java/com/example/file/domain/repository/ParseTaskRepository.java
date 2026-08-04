package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.types.FileTaskId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

public interface ParseTaskRepository extends Repository<ParseTask, FileTaskId> {
  Optional<ParseTask> findById(FileTaskId id);

  void save(ParseTask task);
}
