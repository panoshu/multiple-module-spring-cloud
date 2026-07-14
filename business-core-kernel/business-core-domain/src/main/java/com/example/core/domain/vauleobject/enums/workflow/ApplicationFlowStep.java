package com.example.core.domain.vauleobject.enums.workflow;

/**
 * 业务申请全量流程
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 19:26
 */
public enum ApplicationFlowStep {

  // ==========================================
  // 1. 发起阶段
  // ==========================================
  DRAFT,                        // 草稿
  FORM_DETAIL_INGESTION,

  // ==========================================
  // 2. 数据核查阶段
  // ==========================================
  DATA_VERIFICATION,            // 数据/表单校验
  CONFLICT_VERIFICATION,        // 冲突校验
  CHANNEL_PRE_VERIFICATION,     // 渠道前置校验
  DATA_TRANSFORMATION,          // 数据转换
  RULE_PLATFORM_VERIFICATION,   // 合规/规则校验
  CHANNEL_POST_VERIFICATION,    // 渠道后置校验

  // ==========================================
  // 3. 客户确认与材料准备阶段
  // ==========================================
  APPLICANT_CONFIRMATION,       // 申请人确认
  MATERIAL_PREPARATION,         // 材料准备/清单生成
  MATERIAL_SUBMISSION,          // 材料提交/上传

  // ==========================================
  // 4. 机构审核与提交阶段
  // ==========================================
  APPLICATION_REVIEW,          // 申请预览
  TELLER_REVIEW,               // 柜外清复核 TODO 是否可以和 APPLICATION_REVIEW 合并一下
  APPROVAL,                    // 审批
  SUBMISSION,                  // 正式提交

  // ==========================================
  // 5. 受理与结案阶段
  // ==========================================
  ACCEPTANCE_INITIATION,       // 生成受理单
  ACCEPTANCE_DOCUMENTATION,    // 受理材料归档
  COMPLETED,                   // 已完成
  FINAL_RELEASE                // 后处理/回调解锁 (原 CALLBACK_UNLOCK)
}
