package com.example.bff.shared.registry;

import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.form.BusinessFormApi;
import com.example.core.api.material.MaterialAppApi;
import com.example.core.api.progress.BusinessProgressApi;

/**
 * 每个服务的 5 个 kernel API 代理打包
 *
 * @param batchApi       批次管理 API
 * @param formApi        表单管理 API
 * @param applicationApi 申请单管理 API
 * @param materialApi    材料管理 API
 * @param progressApi    进度查询 API
 * @author bff
 */
record ApiProxies(
  BusinessBatchApi batchApi,
  BusinessFormApi formApi,
  BusinessApplicationApi applicationApi,
  MaterialAppApi materialApi,
  BusinessProgressApi progressApi
) {
}
