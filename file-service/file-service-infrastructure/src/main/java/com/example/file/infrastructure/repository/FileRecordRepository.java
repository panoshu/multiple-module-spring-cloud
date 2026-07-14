package com.example.file.infrastructure.repository;


import com.example.file.infrastructure.entity.FileRecord;
import com.example.file.infrastructure.mapper.FileRecordMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.example.file.infrastructure.entity.table.FileRecordTableDef.FILE_RECORD;

@Repository
@RequiredArgsConstructor
public class FileRecordRepository {

  private final FileRecordMapper mapper;

  public void save(FileRecord record) {
    mapper.insert(record);
  }

  public void update(FileRecord record) {
    mapper.update(record);
  }

  public Optional<FileRecord> findById(String id) {
    return Optional.ofNullable(mapper.selectOneById(id));
  }

  public FileRecord findByStorageKey(String storageKey) {
    return mapper.selectOneByQuery(QueryWrapper.create()
      .where(FILE_RECORD.STORAGE_KEY.eq(storageKey)));
  }
}
