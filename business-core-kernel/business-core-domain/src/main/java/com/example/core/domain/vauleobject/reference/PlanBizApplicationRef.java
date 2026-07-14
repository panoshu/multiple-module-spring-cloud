package com.example.core.domain.vauleobject.reference;

import com.example.core.primitives.business.status.ApplicationStatus;
import com.example.shared.primitives.identity.ApplicationId;

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
