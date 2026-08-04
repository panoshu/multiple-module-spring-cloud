package com.example.core.domain.business.aggregate.valueobject.reference;

import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.shared.identifier.id.ApplicationId;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 09:15
 */
public record PlanBizApplicationRef(
  ApplicationId applicationId,
  ApplicationStatus applicationStatus
) {
}
