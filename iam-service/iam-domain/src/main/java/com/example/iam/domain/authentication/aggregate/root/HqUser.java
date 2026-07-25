package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.HqUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 总部渠道运营人员账号聚合根
 *
 * <p>本公司运营人员，通过内网访问系统办理业务</p>
 */
public class HqUser extends AggregateRoot<HqUserId> {

    private final String staffNo;
    private final String loginName;
    private final String displayName;
    private final String department;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private HqUser(HqUserId id, String staffNo, String loginName, String displayName, String department,
                   UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                   UserNo createdBy, UserNo updatedBy,
                   LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.staffNo = staffNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.department = department;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static HqUser create(HqUserId id, String staffNo, String loginName,
                                String displayName, String department, UserNo creator) {
        return new HqUser(id, staffNo, loginName, displayName, department, UserStatus.ACTIVE,
            null, null, creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static HqUser reconstitute(HqUserId id, String staffNo, String loginName, String displayName,
                                       String department, UserStatus status,
                                       LocalDateTime lastLoginTime, String lastLoginIp,
                                       UserNo createdBy, UserNo updatedBy,
                                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new HqUser(id, staffNo, loginName, displayName, department, status,
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

    public void recordLogin(LocalDateTime loginTime, String ip) {
        this.lastLoginTime = loginTime;
        this.lastLoginIp = ip;
        markUpdated(this.updatedBy() != null ? this.updatedBy() : this.createdBy());
    }

    @Override
    protected void validateInvariants() {
        if (staffNo == null || staffNo.isBlank()) throw new IllegalArgumentException("staffNo cannot be blank");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String staffNo() { return staffNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public String department() { return department; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
