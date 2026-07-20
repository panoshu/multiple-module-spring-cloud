package com.example.file.infrastructure.mapper;

import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件访问流水 Mapper
 */
@Mapper
public interface FileAccessLogMapper extends BaseMapper<FileAccessLogDO> {
}
