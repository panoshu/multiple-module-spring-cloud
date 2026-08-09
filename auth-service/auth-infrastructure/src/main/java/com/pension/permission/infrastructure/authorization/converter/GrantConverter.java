package com.pension.permission.infrastructure.authorization.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.*;
import com.pension.permission.infrastructure.authorization.entity.GrantDO;
import com.pension.permission.types.GrantId;
import com.pension.permission.types.RoleCode;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权策略主记录转换器.
 *
 * <p>负责 {@link Grant} 领域聚合根与 {@link GrantDO} 持久化对象之间的转换。
 * 需要处理三个复杂值对象集合的 JSON 序列化：</p>
 * <ol>
 *   <li>{@link GrantSubject}：sealed interface，4 个实现类，采用 "@type" 字段区分多态</li>
 *   <li>{@link ScopeRule} 列表</li>
 *   <li>{@link Permission} 集合</li>
 * </ol>
 *
 * <p>注意：domain 层禁止依赖 Jackson，所以多态序列化逻辑在本 Converter 中手工管理，
 * 通过 {@code SubjectWrapper(type, data)} 结构表达。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class GrantConverter {

  @Autowired
  protected ObjectMapper objectMapper;

  // ===============================
  // toDO: 领域对象 → DO
  // ===============================

  public GrantDO toDO(Grant grant) {
    if (grant == null) {
      return null;
    }

    GrantDO doObj = new GrantDO();
    doObj.setId(toIdString(grant.id()));
    doObj.setSubject(toSubjectJson(grant.subject()));
    doObj.setScopeRules(toScopeRulesJson(grant.scopeRules()));
    doObj.setPermissions(toPermissionsJson(grant.permissions()));
    doObj.setGrantType(grant.grantType() != null ? grant.grantType().name() : null);
    doObj.setOrigin(grant.origin() != null ? grant.origin().name() : null);
    doObj.setEffect(grant.effect() != null ? grant.effect().name() : null);
    doObj.setStatus(grant.status() != null ? grant.status().name() : null);

    // 有效期映射
    ValidityPeriod validity = grant.validityPeriod();
    if (validity != null) {
      doObj.setValidityStart(validity.start());
      doObj.setValidityEnd(validity.end());
    }

    // 代办场景下的计划编号
    doObj.setSourcePlanNo(grant.sourcePlanNo() != null ? grant.sourcePlanNo().value() : null);
    doObj.setTargetPlanNo(grant.targetPlanNo() != null ? grant.targetPlanNo().value() : null);

    // 基类字段
    doObj.setCreatedBy(grant.createdBy() != null ? grant.createdBy().value() : null);
    doObj.setUpdatedBy(grant.updatedBy() != null ? grant.updatedBy().value() : null);
    doObj.setCreateTime(grant.createdAt());
    doObj.setUpdateTime(grant.updatedAt());
    doObj.setVersion(grant.version() != null ? (int) grant.version().value() : null);
    doObj.setDeleted(false);

    return doObj;
  }

  protected String toIdString(GrantId id) {
    return id != null ? id.value() : null;
  }

  // ===============================
  // toDomain: DO → 领域对象
  // ===============================

  public Grant toDomain(GrantDO doObj) {
    if (doObj == null) {
      return null;
    }

    return Grant.reconstitute(
      toGrantId(doObj.getId()),
      toUserNo(doObj.getCreatedBy()),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getCreateTime(),
      doObj.getUpdateTime(),
      toVersion(doObj.getVersion()),
      toSubject(doObj.getSubject()),
      toScopeRules(doObj.getScopeRules()),
      toPermissionSet(doObj.getPermissions()),
      toGrantType(doObj.getGrantType()),
      toGrantOrigin(doObj.getOrigin()),
      toEffect(doObj.getEffect()),
      toGrantStatus(doObj.getStatus()),
      toValidityPeriod(doObj.getValidityStart(), doObj.getValidityEnd()),
      toPlanNo(doObj.getSourcePlanNo()),
      toPlanNo(doObj.getTargetPlanNo())
    );
  }

  // ===============================
  // 基础类型转换
  // ===============================

  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  protected Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  protected GrantId toGrantId(String value) {
    return value != null ? new GrantId(value) : null;
  }

  protected PlanNo toPlanNo(String value) {
    return value != null ? PlanNo.of(value) : null;
  }

  protected GrantType toGrantType(String name) {
    return name != null ? GrantType.valueOf(name) : null;
  }

  protected GrantOrigin toGrantOrigin(String name) {
    return name != null ? GrantOrigin.valueOf(name) : null;
  }

  protected Effect toEffect(String name) {
    return name != null ? Effect.valueOf(name) : null;
  }

  protected GrantStatus toGrantStatus(String name) {
    return name != null ? GrantStatus.valueOf(name) : null;
  }

  protected ValidityPeriod toValidityPeriod(LocalDateTime start, LocalDateTime end) {
    if (start == null && end == null) {
      return ValidityPeriod.infinite();
    }
    if (end == null) {
      return ValidityPeriod.since(start);
    }
    if (start == null) {
      // 仅指定结束时间，使用 between 允许 start 为 null
      return ValidityPeriod.between(null, end);
    }
    return ValidityPeriod.between(start, end);
  }

  // ===============================
  // GrantSubject 多态序列化
  // ===============================

  /**
   * 序列化 GrantSubject 为 JSON 字符串，包含 "@type" 字段区分具体子类型.
   *
   * <p>JSON 格式示例（UserListSubject）：</p>
   * <pre>{@code
   * {"@type":"UserList","accountIds":["U001","U002"]}
   * }</pre>
   */
  protected String toSubjectJson(GrantSubject subject) {
    if (subject == null) {
      return null;
    }
    try {
      SubjectWrapper wrapper = wrap(subject);
      return objectMapper.writeValueAsString(wrapper);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化 GrantSubject 失败", e);
    }
  }

  protected GrantSubject toSubject(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      SubjectWrapper wrapper = objectMapper.readValue(json, SubjectWrapper.class);
      return unwrap(wrapper);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化 GrantSubject 失败: " + json, e);
    }
  }

  private SubjectWrapper wrap(GrantSubject subject) {
    if (subject instanceof CapabilitySubject) {
      return new SubjectWrapper("Capability", null);
    }
    if (subject instanceof UserListSubject u) {
      return new SubjectWrapper("UserList", objectMapper.valueToTree(u.accountIds()));
    }
    if (subject instanceof PlanAllMembersSubject p) {
      return new SubjectWrapper("PlanAllMembers", objectMapper.valueToTree(p.planNo()));
    }
    if (subject instanceof PlanRoleSubject p) {
      PlanRolePayload payload = new PlanRolePayload(p.planNo(), p.roleCode());
      return new SubjectWrapper("PlanRole", objectMapper.valueToTree(payload));
    }
    throw new IllegalArgumentException("未知的 GrantSubject 类型: " + subject.getClass());
  }

  private GrantSubject unwrap(SubjectWrapper wrapper) {
    if (wrapper == null || wrapper.type() == null) {
      return null;
    }
    return switch (wrapper.type()) {
      case "Capability" -> new CapabilitySubject();
      case "UserList" -> {
        Set<UserNo> accountIds = objectMapper.convertValue(wrapper.data(), new TypeReference<>() {
        });
        yield new UserListSubject(accountIds);
      }
      case "PlanAllMembers" -> {
        PlanNo planNo = objectMapper.convertValue(wrapper.data(), new TypeReference<>() {
        });
        yield new PlanAllMembersSubject(planNo);
      }
      case "PlanRole" -> {
        PlanRolePayload payload = objectMapper.convertValue(wrapper.data(), new TypeReference<>() {
        });
        yield new PlanRoleSubject(payload.planNo(), payload.roleCode());
      }
      default -> throw new IllegalArgumentException("未知的 GrantSubject 类型标识: " + wrapper.type());
    };
  }

  protected String toScopeRulesJson(List<ScopeRule> scopeRules) {
    if (scopeRules == null || scopeRules.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(scopeRules);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化 ScopeRule 集合失败", e);
    }
  }

  protected List<ScopeRule> toScopeRules(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<ScopeRule> rules = objectMapper.readValue(json, new TypeReference<>() {
      });
      return rules != null ? rules : List.of();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化 ScopeRule 集合失败: " + json, e);
    }
  }

  // ===============================
  // ScopeRule 列表 JSON 序列化
  // ===============================

  protected String toPermissionsJson(Set<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(permissions);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化 Permission 集合失败", e);
    }
  }

  protected Set<Permission> toPermissionSet(String json) {
    if (json == null || json.isBlank()) {
      return new HashSet<>();
    }
    try {
      Set<Permission> permissions = objectMapper.readValue(json, new TypeReference<>() {
      });
      return permissions != null ? permissions : new HashSet<>();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化 Permission 集合失败: " + json, e);
    }
  }

  // ===============================
  // Permission 集合 JSON 序列化
  // ===============================

  /**
   * Subject 包装类，承载多态类型标识.
   */
  private record SubjectWrapper(String type, Object data) {
  }

  /**
   * PlanRoleSubject 的可序列化负载.
   */
  private record PlanRolePayload(PlanNo planNo, RoleCode roleCode) {
  }
}
