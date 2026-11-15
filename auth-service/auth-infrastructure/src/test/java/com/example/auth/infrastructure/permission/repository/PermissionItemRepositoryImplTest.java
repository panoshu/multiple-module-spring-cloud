package com.example.auth.infrastructure.permission.repository;

import com.example.auth.domain.authorization.enumeration.PermissionCategory;
import com.example.auth.domain.permission.aggregate.PermissionItem;
import com.example.auth.domain.permission.enumeration.PermissionItemSource;
import com.example.auth.infrastructure.permission.converter.PermissionItemConverter;
import com.example.auth.infrastructure.permission.entity.PermissionItemDO;
import com.example.auth.infrastructure.permission.mapper.PermissionItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 73：权限点元数据仓储的 upsert 分支契约。
 * <p>
 * 启动期 {@code PermissionScanner}/{@code upsertAll} 传入的实体来自
 * {@code PermissionItem.reconstitute}（恒无 version），update 分支必须以
 * 数据库现有 version 更新，不得对内存对象的 null version 调用 {@code next()}。
 */
@DisplayName("权限点元数据仓储 upsert 契约（批次 73）")
class PermissionItemRepositoryImplTest {

  private PermissionItemMapper mapper;
  private PermissionItemConverter converter;
  private PermissionItemRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    mapper = mock(PermissionItemMapper.class);
    converter = mock(PermissionItemConverter.class);
    repository = new PermissionItemRepositoryImpl(mapper, converter);
  }

  /** reconstitute 恒为无 version 对象，模拟启动期 upsert 的真实入参。 */
  private PermissionItem reconstituted() {
    return PermissionItem.reconstitute(
      "P-1", "USER_MANAGE", "FREEZE", PermissionCategory.PLATFORM,
      PermissionItemSource.API, "UserController", "freezeUser",
      "POST", "/api/users/freeze", "冻结用户", "用户管理", 0, true,
      "admin-1", "admin-2",
      LocalDateTime.now(), LocalDateTime.now());
  }

  private PermissionItemDO doWith(String id, Integer version) {
    PermissionItemDO doObj = new PermissionItemDO();
    doObj.setId(id);
    doObj.setVersion(version);
    return doObj;
  }

  @Test
  @DisplayName("update 分支以数据库现有 version 更新，无 version 实体不触发 NPE")
  void save_updateBranch_usesExistingVersionWithoutNpe() {
    when(converter.toDO(any())).thenReturn(doWith("P-1", null));
    when(mapper.selectOneById("P-1")).thenReturn(doWith("P-1", 3));
    when(mapper.update(any())).thenReturn(1);

    assertThatCode(() -> repository.save(reconstituted())).doesNotThrowAnyException();

    ArgumentCaptor<PermissionItemDO> captor = ArgumentCaptor.forClass(PermissionItemDO.class);
    verify(mapper).update(captor.capture());
    assertThat(captor.getValue().getVersion()).isEqualTo(3);
  }

  @Test
  @DisplayName("insert 分支：数据库无记录时插入且不触碰 version")
  void save_insertBranch_inserts() {
    when(converter.toDO(any())).thenReturn(doWith("P-NEW", null));
    when(mapper.selectOneById("P-NEW")).thenReturn(null);

    assertThatCode(() -> repository.save(reconstituted())).doesNotThrowAnyException();

    verify(mapper).insert(any(PermissionItemDO.class));
  }
}
