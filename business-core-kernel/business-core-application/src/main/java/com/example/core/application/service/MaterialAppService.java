package com.example.core.application.service;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.vauleobject.BusinessFile;
import com.example.core.domain.repository.ApplicationRepository;
import com.example.shared.primitives.identity.ApplicationId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 19:18
 */
@Service
@RequiredArgsConstructor
public class MaterialAppService {

  private final ApplicationRepository applicationRepository;

  /**
   * 绑定单个材料文件
   */
  @Transactional
  public void bindIndividualMaterial(ApplicationId appId, String materialCode, BusinessFile file) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    // 聚合根内校验：当前步骤是否允许上传、该材料是否必传等
    app.uploadIndividualPlanMaterial(materialCode, file);
    applicationRepository.save(app);
  }

  /**
   * 绑定打包上传的材料文件映射
   */
  @Transactional
  public void bindPackageMaterials(ApplicationId appId, BusinessFile zipFile) {
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    app.uploadPackage(zipFile);
    applicationRepository.save(app);
  }
}
