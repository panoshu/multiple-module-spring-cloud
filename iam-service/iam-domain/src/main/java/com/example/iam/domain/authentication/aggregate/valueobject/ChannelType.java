package com.example.iam.domain.authentication.aggregate.valueobject;

/**
 * 业务办理渠道类型
 *
 * <p>不同渠道对应独立的用户账号体系与 sa-token StpLogic</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
public enum ChannelType {
  /** 网上渠道（经办人，互联网访问） */
  INTERNET,
  /** 总部渠道（运营人员，内网访问） */
  HQ,
  /** 网点渠道（银行柜员，专线网络访问） */
  BRANCH
}
