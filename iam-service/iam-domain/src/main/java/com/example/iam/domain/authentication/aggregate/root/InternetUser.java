package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 网上渠道经办人账号聚合根
 *
 * <p>客户企业的 HR，通过互联网访问系统办理业务</p>
 */
public class InternetUser extends AggregateRoot<InternetUserId> {

    private final CustomerNo customerNo;
    private final String loginName;
    private final String displayName;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private InternetUser(InternetUserId id, CustomerNo customerNo, String loginName,
                         String displayName, UserStatus status,
                         LocalDateTime lastLoginTime, String lastLoginIp,
                         UserNo createdBy, UserNo updatedBy,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.customerNo = customerNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static InternetUser create(InternetUserId id, CustomerNo customerNo,
                                      String loginName, String displayName, UserNo creator) {
        return new InternetUser(id, customerNo, loginName, displayName, UserStatus.ACTIVE,
            null, null, creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static InternetUser reconstitute(InternetUserId id, CustomerNo customerNo,
                                            String loginName, String displayName, UserStatus status,
                                            LocalDateTime lastLoginTime, String lastLoginIp,
                                            UserNo createdBy, UserNo updatedBy,
                                            LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new InternetUser(id, customerNo, loginName, displayName, status,
            lastLoginTime, lastLoginIp, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    public void enable(UserNo operator) {
        this.status = UserStatus.ACTIVE;
        markUpdated(operator);
    }

    public void lock(UserNo operator) {
        this.status = UserStatus.LOCKED;
        markUpdated(operator);
    }

    public void recordLogin(LocalDateTime loginTime, String ip) {
        this.lastLoginTime = loginTime;
        this.lastLoginIp = ip;
        markUpdated(this.updatedBy() != null ? this.updatedBy() : this.createdBy());
    }

    @Override
    protected void validateInvariants() {
        if (customerNo == null) throw new IllegalArgumentException("customerNo cannot be null");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public CustomerNo customerNo() { return customerNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
