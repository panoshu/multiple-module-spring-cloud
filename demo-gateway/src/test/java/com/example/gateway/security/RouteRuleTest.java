package com.example.gateway.security;

import com.example.iam.api.dto.RouteRuleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RouteRule} 路由规则值对象单元测试。
 *
 * <p>覆盖 {@link RouteRule#from(RouteRuleDTO)} 转换逻辑与 record 的 equals/hashCode 契约。
 */
@DisplayName("RouteRule 路由规则值对象测试")
class RouteRuleTest {

  @Nested
  @DisplayName("from(RouteRuleDTO) DTO 转换")
  class FromDto {

    @Test
    @DisplayName("正确映射 routePattern/checkType/checkValue/priority 四个字段")
    void mapsAllGatewayFields() {
      RouteRuleDTO dto = new RouteRuleDTO(
          1L, "/internet/**", "PERMISSION", "business:handle",
          "测试规则", 100, true,
          LocalDateTime.of(2026, 1, 1, 0, 0),
          LocalDateTime.of(2026, 1, 2, 0, 0),
          1L
      );

      RouteRule rule = RouteRule.from(dto);

      assertThat(rule.routePattern()).isEqualTo("/internet/**");
      assertThat(rule.checkType()).isEqualTo("PERMISSION");
      assertThat(rule.checkValue()).isEqualTo("business:handle");
      assertThat(rule.priority()).isEqualTo(100);
    }

    @Test
    @DisplayName("仅保留网关校验所需字段,屏蔽 ruleId/description/enabled/createdAt/updatedAt/version")
    void onlyMapsGatewayFieldsIgnoringOthers() {
      RouteRuleDTO dto = new RouteRuleDTO(
          999L, "/hq/**", "ROLE", "admin",
          "描述", 50, true,
          LocalDateTime.of(2026, 1, 1, 0, 0),
          LocalDateTime.of(2026, 1, 2, 0, 0),
          5L
      );

      RouteRule rule = RouteRule.from(dto);

      assertThat(rule.routePattern()).isEqualTo("/hq/**");
      assertThat(rule.checkType()).isEqualTo("ROLE");
      assertThat(rule.checkValue()).isEqualTo("admin");
      assertThat(rule.priority()).isEqualTo(50);
    }

    @Test
    @DisplayName("SKIP 类型规则 checkValue 为空字符串也能正确映射")
    void mapsSkipRuleWithEmptyCheckValue() {
      RouteRuleDTO dto = new RouteRuleDTO(
          10L, "/public/**", "SKIP", "",
          null, 200, true, null, null, 1L
      );

      RouteRule rule = RouteRule.from(dto);

      assertThat(rule.checkType()).isEqualTo("SKIP");
      assertThat(rule.checkValue()).isEmpty();
      assertThat(rule.priority()).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("record equals/hashCode 契约")
  class EqualsHashCodeContract {

    @Test
    @DisplayName("相同字段值的两个 RouteRule 相等且 hashCode 一致")
    void equalWhenSameFields() {
      RouteRule rule1 = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      RouteRule rule2 = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);

      assertThat(rule1).isEqualTo(rule2);
      assertThat(rule1.hashCode()).isEqualTo(rule2.hashCode());
    }

    @Test
    @DisplayName("routePattern 不同则不相等")
    void notEqualWhenRoutePatternDiffers() {
      RouteRule base = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(base).isNotEqualTo(new RouteRule("/hq/**", "PERMISSION", "biz:handle", 100));
    }

    @Test
    @DisplayName("checkType 不同则不相等")
    void notEqualWhenCheckTypeDiffers() {
      RouteRule base = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(base).isNotEqualTo(new RouteRule("/internet/**", "ROLE", "biz:handle", 100));
    }

    @Test
    @DisplayName("checkValue 不同则不相等")
    void notEqualWhenCheckValueDiffers() {
      RouteRule base = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(base).isNotEqualTo(new RouteRule("/internet/**", "PERMISSION", "other:perm", 100));
    }

    @Test
    @DisplayName("priority 不同则不相等")
    void notEqualWhenPriorityDiffers() {
      RouteRule base = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(base).isNotEqualTo(new RouteRule("/internet/**", "PERMISSION", "biz:handle", 99));
    }

    @Test
    @DisplayName("与 null 比较不相等")
    void notEqualToNull() {
      RouteRule rule = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(rule).isNotEqualTo(null);
    }

    @Test
    @DisplayName("与自身引用相等")
    void equalToSelf() {
      RouteRule rule = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      assertThat(rule).isEqualTo(rule);
    }
  }
}
