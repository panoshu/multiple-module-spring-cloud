package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 网点渠道柜员账号聚合根
 *
 * <p>合作银行的柜员，通过专线网络访问系统办理业务</p>
 */
public class BranchUser extends AggregateRoot<BranchUserId> {

    private final String bankCode;
    private final String branchCode;
    private final String tellerNo;
    private final String loginName;
    private final String displayName;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private BranchUser(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                       String loginName, String displayName,
                       UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                       UserNo createdBy, UserNo updatedBy,
                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.bankCode = bankCode;
        this.branchCode = branchCode;
        this.tellerNo = tellerNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static BranchUser create(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                                    String loginName, String displayName, UserNo creator) {
        return new BranchUser(id, bankCode, branchCode, tellerNo, loginName, displayName,
            UserStatus.ACTIVE, null, null, creator, creator,
            LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static BranchUser reconstitute(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                                          String loginName, String displayName,
                                          UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                                          UserNo createdBy, UserNo updatedBy,
                                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new BranchUser(id, bankCode, branchCode, tellerNo, loginName, displayName,
            status, lastLoginTime, lastLoginIp, createdBy, updatedBy, createdAt, updatedAt, version);
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
        if (bankCode == null || bankCode.isBlank()) throw new IllegalArgumentException("bankCode cannot be blank");
        if (tellerNo == null || tellerNo.isBlank()) throw new IllegalArgumentException("tellerNo cannot be blank");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String bankCode() { return bankCode; }
    public String branchCode() { return branchCode; }
    public String tellerNo() { return tellerNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
