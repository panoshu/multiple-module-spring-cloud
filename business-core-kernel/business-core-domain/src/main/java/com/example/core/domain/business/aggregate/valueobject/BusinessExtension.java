package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 业务扩展信息, 通过值对象多态支持各业务的扩展字段
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 15:19
 */
public interface BusinessExtension extends ValueObject {
  BusinessType businessType();
}
