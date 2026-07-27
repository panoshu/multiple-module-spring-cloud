package com.example.iam.application.service;

import com.example.iam.api.command.DisableUserCommand;
import com.example.iam.api.command.EnableUserCommand;
import com.example.iam.api.command.LockUserCommand;
import com.example.iam.api.command.UpdateUserProfileCommand;
import com.example.iam.api.command.CreateUserCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.UserDTO;
import com.example.iam.api.dto.UserProfileDTO;
import com.example.iam.api.query.GetUserDetailQuery;
import com.example.iam.api.query.ListUsersQuery;
import com.example.iam.domain.authentication.aggregate.entity.UserProfile;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户管理应用服务。
 *
 * <p>负责三渠道(网上/总部/网点)用户的创建、状态变更、档案维护与查询编排。
 * 通过 {@link UserRepository} 持久化聚合根,通过 {@link EventBus} 发布领域事件,
 * 通过 {@link ChannelSessionPort}(在认证服务中注入)同步会话状态。
 *
 * <p>本服务仅编排业务流程,业务规则(状态机校验、唯一性校验)由 User 聚合根负责。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAppService {

  private final UserRepository userRepository;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建用户。
   *
   * <p>流程:
   * <ol>
   *   <li>校验 loginName 在指定渠道内唯一</li>
   *   <li>通过 IdService 生成 UserId</li>
   *   <li>调用 User.create 工厂方法创建聚合根(可选附带档案)</li>
   *   <li>保存聚合根并发布领域事件</li>
   * </ol>
   *
   * @param command 创建用户命令
   * @return 新建用户 ID
   */
  @Transactional
  public IdResponseDTO create(CreateUserCommand command) {
    ChannelType channelType = parseChannelType(command.channelType());
    if (userRepository.existsByLoginName(command.loginName(), channelType)) {
      throw new BusinessException(IamAuthErrorCode.LOGIN_NAME_DUPLICATE)
          .withUserDetail("登录名在指定渠道内已存在")
          .withContext("loginName", command.loginName())
          .withContext("channelType", channelType);
    }

    UserId userId = idService.nextLongId(UserId.class, "IAM_USER");
    UserNo operator = UserNo.of(command.operator());
    User user = buildUser(userId, channelType, command, operator);

    userRepository.save(user);
    publishEvents(user);

    log.info("用户创建成功: userId={}, loginName={}, channelType={}",
        userId.value(), command.loginName(), channelType);
    return new IdResponseDTO(userId.value());
  }

  /**
   * 禁用用户。
   *
   * <p>禁用后需踢用户下线,避免已建立的会话继续访问。
   *
   * @param command 禁用用户命令
   */
  @Transactional
  public void disable(DisableUserCommand command) {
    User user = loadUserOrThrow(command.userId());
    UserNo operator = UserNo.of(command.operator());
    user.disable(operator, command.reason());
    userRepository.save(user);
    publishEvents(user);
    log.info("用户禁用成功: userId={}, reason={}", command.userId(), command.reason());
  }

  /**
   * 启用用户。
   *
   * @param command 启用用户命令
   */
  @Transactional
  public void enable(EnableUserCommand command) {
    User user = loadUserOrThrow(command.userId());
    UserNo operator = UserNo.of(command.operator());
    user.enable(operator);
    userRepository.save(user);
    publishEvents(user);
    log.info("用户启用成功: userId={}", command.userId());
  }

  /**
   * 锁定用户(系统触发,如登录失败次数超限)。
   *
   * @param command 锁定用户命令
   */
  @Transactional
  public void lock(LockUserCommand command) {
    User user = loadUserOrThrow(command.userId());
    UserNo operator = UserNo.of(command.operator());
    user.lock(operator, command.reason());
    userRepository.save(user);
    publishEvents(user);
    log.info("用户锁定成功: userId={}, reason={}", command.userId(), command.reason());
  }

  /**
   * 更新用户档案。
   *
   * @param command 更新档案命令
   */
  @Transactional
  public void updateProfile(UpdateUserProfileCommand command) {
    User user = loadUserOrThrow(command.userId());
    UserNo operator = UserNo.of(command.operator());
    user.updateProfile(
        command.email(), command.phone(),
        command.organization(), command.position(),
        command.branchId(), command.employeeNo(),
        command.extraAttributes(), operator);
    userRepository.save(user);
    publishEvents(user);
    log.info("用户档案更新成功: userId={}", command.userId());
  }

  /**
   * 用户列表分页查询。
   *
   * <p>简化实现:从仓储加载全部用户后在内存中过滤分页。
   * 后续 Repository 提供分页方法后可优化。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<UserDTO> list(ListUsersQuery query) {
    List<User> allUsers = userRepository.loadAll();
    List<User> filtered = allUsers.stream()
        .filter(u -> matchesChannel(u, query.channelType()))
        .filter(u -> matchesLoginName(u, query.loginName()))
        .filter(u -> matchesStatus(u, query.status()))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 用户详情查询。
   *
   * @param query 详情查询
   * @return 用户 DTO
   */
  @Transactional(readOnly = true)
  public UserDTO getDetail(GetUserDetailQuery query) {
    User user = loadUserOrThrow(query.userId());
    return toDTO(user);
  }

  /**
   * 加载用户或抛出业务异常。
   */
  private User loadUserOrThrow(Long userId) {
    return userRepository.load(UserId.of(userId))
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.USER_NOT_FOUND)
            .withUserDetail("用户不存在")
            .withContext("userId", userId));
  }

  /**
   * 构建用户聚合根(根据是否携带档案字段选择工厂方法)。
   */
  private User buildUser(UserId userId, ChannelType channelType,
                         CreateUserCommand command, UserNo operator) {
    boolean hasProfile = hasAnyProfileField(command);
    if (!hasProfile) {
      return User.create(userId, channelType,
          command.loginName(), command.displayName(), operator);
    }
    UserProfile profile = UserProfile.create(
        userId, channelType,
        command.email(), command.phone(),
        command.organization(), command.position(),
        command.branchId(), command.employeeNo(),
        command.extraAttributes(), operator);
    return User.create(userId, channelType,
        command.loginName(), command.displayName(), profile, operator);
  }

  /**
   * 判断命令是否携带任意档案字段。
   */
  private boolean hasAnyProfileField(CreateUserCommand command) {
    return isNonBlank(command.email()) || isNonBlank(command.phone())
        || isNonBlank(command.organization()) || isNonBlank(command.position())
        || isNonBlank(command.branchId()) || isNonBlank(command.employeeNo())
        || (command.extraAttributes() != null && !command.extraAttributes().isEmpty());
  }

  private boolean isNonBlank(String value) {
    return value != null && !value.isBlank();
  }

  /**
   * 解析渠道类型字符串为枚举,无效时抛业务异常。
   */
  private ChannelType parseChannelType(String channelType) {
    try {
      return ChannelType.valueOf(Objects.requireNonNull(channelType, "channelType cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthErrorCode.CHANNEL_TYPE_INVALID)
          .withUserDetail("渠道类型无效: " + channelType)
          .withContext("channelType", channelType);
    }
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(User user) {
    user.getDomainEvents().forEach(eventBus::publish);
    user.clearDomainEvents();
  }

  /**
   * 列表分页切片(简化实现)。
   */
  private PageData<UserDTO> paginate(List<User> users, PageQuery pageQuery) {
    int total = users.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<UserDTO> items = users.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesChannel(User user, String channelType) {
    if (channelType == null || channelType.isBlank()) {
      return true;
    }
    return user.channelType().name().equals(channelType);
  }

  private boolean matchesLoginName(User user, String loginName) {
    if (loginName == null || loginName.isBlank()) {
      return true;
    }
    return user.loginName() != null && user.loginName().contains(loginName);
  }

  private boolean matchesStatus(User user, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    return user.status() != null && user.status().name().equals(status);
  }

  /**
   * 领域对象转 DTO。
   */
  private UserDTO toDTO(User user) {
    return new UserDTO(
        user.id().value(),
        user.channelType().name(),
        user.loginName(),
        user.displayName(),
        user.status() != null ? user.status().name() : null,
        user.lastLoginTime(),
        user.lastLoginIp(),
        toProfileDTO(user.profile()),
        user.createdAt(),
        user.updatedAt(),
        user.version() != null ? user.version().value() : null
    );
  }

  private UserProfileDTO toProfileDTO(UserProfile profile) {
    if (profile == null) {
      return null;
    }
    return new UserProfileDTO(
        profile.channelType().name(),
        profile.email(),
        profile.phone(),
        profile.organization(),
        profile.position(),
        profile.branchId(),
        profile.employeeNo(),
        profile.extraAttributes()
    );
  }
}
