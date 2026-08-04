package com.pension.permission.domain.user.enumeration;

import com.example.shared.enumeration.CodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * UserType
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 15:47
 */
@Getter
@AllArgsConstructor
public enum UserType implements CodeEnum<String> {

  AGENT("01", "经办人（网上渠道）"),
  OPERATOR("02", "运营人员（总部渠道）"),
  TELLER("03", "柜员（网点渠道）");

  private final String code;
  private final String description;
}
