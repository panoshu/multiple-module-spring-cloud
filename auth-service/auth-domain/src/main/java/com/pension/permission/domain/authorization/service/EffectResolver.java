package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;

import java.util.List;

/**
 * 同一层内命中多条规则时的合并语义：DENY 无条件优先；
 * 没有DENY时，只要有ALLOW就通过；都没命中 = 默认拒绝(白名单语义)。
 * <p>
 * 选择"DENY优先"而不是"按规则粒度比大小"，是为了保证判定结果简单、可审计——
 * 一旦引入"哪条规则更具体"的比较，AND组合的多维度规则之间就很难有无歧义的统一算法。
 * 如果以后确实需要"大范围DENY、局部特批ALLOW"这种反向覆盖，再引入显式的priority字段，
 * 属于纯增量扩展，不影响现有数据。
 */
public final class EffectResolver {

  public boolean resolve(List<Grant> matchedGrants) {
    boolean anyDeny = matchedGrants.stream().anyMatch(g -> g.effect() == Effect.DENY);
    if (anyDeny) {
      return false;
    }
    return matchedGrants.stream().anyMatch(g -> g.effect() == Effect.ALLOW);
  }
}
