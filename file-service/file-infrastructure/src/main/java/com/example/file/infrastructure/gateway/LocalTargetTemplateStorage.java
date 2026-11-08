package com.example.file.infrastructure.gateway;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.TargetTemplateStorage;
import com.example.file.infrastructure.entity.TargetTemplateDO;
import com.example.file.infrastructure.mapper.TargetTemplateMapper;
import com.example.shared.exception.SystemException;
import com.example.file.types.FileId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

import static com.example.file.infrastructure.entity.table.TargetTemplateDOTableDef.TARGET_TEMPLATE_DO;

@Slf4j
@Component
public class LocalTargetTemplateStorage implements TargetTemplateStorage {

  private static final String CLASSPATH_TEMPLATE_PREFIX = "templates/outbound/";

  private final TargetTemplateMapper targetTemplateMapper;
  private final FileStorageGateway fileStorageGateway;

  public LocalTargetTemplateStorage(TargetTemplateMapper targetTemplateMapper,
                                    FileStorageGateway fileStorageGateway) {
    this.targetTemplateMapper = targetTemplateMapper;
    this.fileStorageGateway = fileStorageGateway;
  }

  @Override
  public InputStream load(String targetTemplateId) {
    // 1. 优先从数据库查找
    TargetTemplateDO templateDO = targetTemplateMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(TARGET_TEMPLATE_DO.TEMPLATE_ID.eq(targetTemplateId))
        .and(TARGET_TEMPLATE_DO.DELETED.eq(0))
    );

    if (templateDO != null && templateDO.getFileId() != null) {
      log.debug("从数据库加载目标模板: targetTemplateId={}, fileId={}", targetTemplateId, templateDO.getFileId());
      return fileStorageGateway.open(new FileId(templateDO.getFileId()));
    }

    // 2. 数据库未命中，从 classpath 加载
    String classpathLocation = CLASSPATH_TEMPLATE_PREFIX + targetTemplateId + ".xlsx";
    ClassPathResource resource = new ClassPathResource(classpathLocation);
    if (resource.exists()) {
      log.debug("从 classpath 加载目标模板: {}", classpathLocation);
      try {
        return resource.getInputStream();
      } catch (IOException e) {
        throw new SystemException(FileErrorCodes.OUTBOUND_TEMPLATE_NOT_FOUND, e)
          .withLogDetail("classpath读取失败: " + classpathLocation);
      }
    }

    // 3. 均未找到
    throw new SystemException(FileErrorCodes.OUTBOUND_TEMPLATE_NOT_FOUND)
      .withLogDetail("targetTemplateId=" + targetTemplateId);
  }
}
