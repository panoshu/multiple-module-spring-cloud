package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.channel.aggregate.CustomerChannelEntitlement;
import com.pension.permission.infrastructure.channel.entity.CustomerChannelEntitlementDO;
import com.pension.permission.types.CustomerChannelEntitlementId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;

/**
 * 客户渠道开通记录 Converter.
 *
 * <p>负责 {@link CustomerChannelEntitlement} 领域聚合根与
 * {@link CustomerChannelEntitlementDO} 持久化对象之间的转换。</p>
 *
 * <p>{@code enabledChannels} 字段以 JSON 数组存储 {@link AnnuityChannel#name()} 字符串列表
 * （如 {@code ["NETAPP","BANK_BRANCH"]}）。由于 domain 层禁止依赖 Jackson，
 * 序列化逻辑在本 Converter 中手工管理。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CustomerChannelEntitlementConverter {

  @Autowired
  protected ObjectMapper objectMapper;

  /**
   * 领域聚合根 → DO.
   */
  public CustomerChannelEntitlementDO toDO(CustomerChannelEntitlement entitlement) {
    if (entitlement == null) {
      return null;
    }
    CustomerChannelEntitlementDO doObj = new CustomerChannelEntitlementDO();
    doObj.setId(entitlement.id().value());
    doObj.setCustomerNo(entitlement.customerNo().value());
    doObj.setEnabledChannels(toChannelsJson(entitlement.enabledChannels()));
    doObj.setCreatedBy(entitlement.createdBy() != null ? entitlement.createdBy().value() : null);
    doObj.setUpdatedBy(entitlement.updatedBy() != null ? entitlement.updatedBy().value() : null);
    doObj.setCreateTime(entitlement.createdAt());
    doObj.setUpdateTime(entitlement.updatedAt());
    doObj.setVersion(entitlement.version() != null ? (int) entitlement.version().value() : null);
    doObj.setDeleted(false);
    return doObj;
  }

  /**
   * DO → 领域聚合根（reconstitute，不注册领域事件）.
   */
  public CustomerChannelEntitlement toDomain(CustomerChannelEntitlementDO doObj) {
    if (doObj == null) {
      return null;
    }
    return CustomerChannelEntitlement.reconstitute(
      new CustomerChannelEntitlement.ReconstituteSnapshot(
        new CustomerChannelEntitlementId(doObj.getId()),
        toUserNo(doObj.getCreatedBy()),
        toUserNo(doObj.getUpdatedBy()),
        doObj.getCreateTime(),
        doObj.getUpdateTime(),
        toVersion(doObj.getVersion()),
        CustomerNo.of(doObj.getCustomerNo()),
        toChannelSet(doObj.getEnabledChannels())
      )
    );
  }

  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  protected Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  /**
   * 序列化渠道集合为 JSON 字符串.
   */
  protected String toChannelsJson(Set<AnnuityChannel> channels) {
    if (channels == null || channels.isEmpty()) {
      return "[]";
    }
    try {
      return objectMapper.writeValueAsString(channels.stream()
        .map(AnnuityChannel::name)
        .toList());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化 AnnuityChannel 集合失败", e);
    }
  }

  /**
   * 反序列化 JSON 字符串为渠道集合.
   */
  protected Set<AnnuityChannel> toChannelSet(String json) {
    if (json == null || json.isBlank()) {
      return new HashSet<>();
    }
    try {
      Set<String> names = objectMapper.readValue(json, new TypeReference<>() {});
      Set<AnnuityChannel> channels = new HashSet<>();
      for (String name : names) {
        channels.add(AnnuityChannel.valueOf(name));
      }
      return channels;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化 AnnuityChannel 集合失败: " + json, e);
    }
  }
}
