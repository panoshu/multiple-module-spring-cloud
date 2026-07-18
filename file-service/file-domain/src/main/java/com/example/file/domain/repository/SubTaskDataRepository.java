package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.SubTaskData;
import com.example.file.domain.model.valueobject.FetchPagination;
import com.example.file.domain.model.valueobject.PagedRows;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface SubTaskDataRepository extends Repository<SubTaskData, SubTaskId> {
  Optional<SubTaskData> findById(SubTaskId id);
  void save(SubTaskData subTask);
  PagedRows findPagedRows(SubTaskId id, FetchPagination pagination);
  List<SubTaskSummary> findSummariesByTask(FileTaskId taskId);
  void markExpiredBefore(java.time.LocalDateTime now);
}
