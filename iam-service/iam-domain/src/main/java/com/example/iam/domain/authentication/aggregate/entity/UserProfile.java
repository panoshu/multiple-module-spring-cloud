package com.example.iam.domain.authentication.aggregate.entity;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 用户渠道专属档案实体(聚合内实体,1:1 与 {@code User} 关联)。
 *
 * <p>承载渠道差异化字段:网上渠道记录 email/phone/employeeNo,总部渠道记录 organization/position,
 * 网点渠道记录 branchId/position。{@link #extraAttributes} 用于承载渠道特有的扩展字段(如网点柜员的 clearance)。
 *
 * <p>该实体 ID 与所属 {@code User} 聚合根 ID 相同(共享主键),不独立持久化。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class UserProfile extends Entity<UserId> {

  private final ChannelType channelType;
  private String email;
  private String phone;
  private String organization;
  private String position;
  private String branchId;
  private String employeeNo;
  private Map<String, String> extraAttributes;

  private UserProfile(UserId id, ChannelType channelType,
                       String email, String phone,
                       String organization, String position,
                       String branchId, String employeeNo,
                       Map<String, String> extraAttributes,
                       UserNo createdBy) {
    super(id, createdBy);
    this.channelType = Objects.requireNonNull(channelType, "channelType cannot be null");
    this.email = email;
    this.phone = phone;
    this.organization = organization;
    this.position = position;
    this.branchId = branchId;
    this.employeeNo = employeeNo;
    this.extraAttributes = copyExtra(extraAttributes);
    this.validateInvariants();
  }

  private UserProfile(UserId id, ChannelType channelType,
                       String email, String phone,
                       String organization, String position,
                       String branchId, String employeeNo,
                       Map<String, String> extraAttributes,
                       UserNo createdBy, UserNo updatedBy,
                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.channelType = channelType;
    this.email = email;
    this.phone = phone;
    this.organization = organization;
    this.position = position;
    this.branchId = branchId;
    this.employeeNo = employeeNo;
    this.extraAttributes = copyExtra(extraAttributes);
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新档案。
   */
  public static UserProfile create(UserId id, ChannelType channelType,
                                    String email, String phone,
                                    String organization, String position,
                                    String branchId, String employeeNo,
                                    Map<String, String> extraAttributes,
                                    UserNo createdBy) {
    return new UserProfile(id, channelType, email, phone, organization, position,
        branchId, employeeNo, extraAttributes, createdBy);
  }

  /**
   * 工厂方法:从数据库重建。
   */
  public static UserProfile reconstitute(UserId id, ChannelType channelType,
                                          String email, String phone,
                                          String organization, String position,
                                          String branchId, String employeeNo,
                                          Map<String, String> extraAttributes,
                                          UserNo createdBy, UserNo updatedBy,
                                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new UserProfile(id, channelType, email, phone, organization, position,
        branchId, employeeNo, extraAttributes, createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 更新档案字段。
   */
  public void update(String email, String phone,
                     String organization, String position,
                     String branchId, String employeeNo,
                     Map<String, String> extraAttributes,
                     UserNo operator) {
    this.email = email;
    this.phone = phone;
    this.organization = organization;
    this.position = position;
    this.branchId = branchId;
    this.employeeNo = employeeNo;
    this.extraAttributes = copyExtra(extraAttributes);
    markUpdated(operator);
  }

  public ChannelType channelType() { return channelType; }
  public String email() { return email; }
  public String phone() { return phone; }
  public String organization() { return organization; }
  public String position() { return position; }
  public String branchId() { return branchId; }
  public String employeeNo() { return employeeNo; }

  /**
   * 返回扩展属性的不可变视图。
   */
  public Map<String, String> extraAttributes() {
    return Collections.unmodifiableMap(extraAttributes);
  }

  private static Map<String, String> copyExtra(Map<String, String> source) {
    return source == null ? new HashMap<>() : new HashMap<>(source);
  }

  @Override
  protected void validateInvariants() {
    if (channelType == null) {
      throw new IllegalStateException("UserProfile.channelType cannot be null");
    }
    if (channelType == ChannelType.BRANCH && (branchId == null || branchId.isBlank())) {
      throw new IllegalStateException("Branch channel profile requires branchId");
    }
  }
}
