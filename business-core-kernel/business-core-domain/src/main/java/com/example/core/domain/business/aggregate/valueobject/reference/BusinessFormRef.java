package com.example.core.domain.business.aggregate.valueobject.reference;

import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.shared.primitives.identity.FormId;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 09:15
 */
public record BusinessFormRef(
  FormId formId,
  FormStatus formStatus
) {
}
