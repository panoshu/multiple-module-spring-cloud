package com.example.auth.application.authorization;

import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.BusinessCode;

/**
 * 解析数据可见范围查询.
 *
 * @param identity 用户标识
 * @param business 业务编码
 * @author auth-application
 */
public record ResolveDataScopeQuery(UserNo identity, BusinessCode business) {
}
