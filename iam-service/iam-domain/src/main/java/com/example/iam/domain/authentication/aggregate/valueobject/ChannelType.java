package com.example.iam.domain.authentication.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 用户登录渠道类型(三套 sa-token StpLogic 对应)。
 *
 * <ul>
 *   <li>{@link #INTERNET} - 网上渠道(企业经办人)</li>
 *   <li>{@link #HQ} - 总部渠道(运营人员)</li>
 *   <li>{@link #BRANCH} - 网点渠道(银行柜员,需二次授权)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum ChannelType implements ValueObject {
  INTERNET,
  HQ,
  BRANCH
}
