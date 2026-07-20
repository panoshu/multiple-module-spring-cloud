package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileAccessLogId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件访问流水聚合根
 * <p>
 * 记录 token 申请 (APPLY) 和实际访问 (ACCESS) 的双记录，用于审计。
 * 流水记录不可修改、不可删除，仅在写入失败时可调用 {@link #markFail(String)} 标记失败。
 */
public class FileAccessLog extends AggregateRoot<FileAccessLogId> {

    private final FileId fileId;
    private final FileAccessAction action;
    private final FileUsage usage;
    private final CustomerNo customerNo;
    private final ProductNo productNo;
    private final UserNo operator;
    private final String sourceApp;
    private final String sourceIp;
    private final String tokenHash;
    private FileAccessResult result;
    private String failReason;
    private final LocalDateTime occurAt;

    // 业务创建
    private FileAccessLog(FileAccessLogId id, FileId fileId, FileAccessAction action, FileUsage usage,
                          CustomerNo customerNo, ProductNo productNo, UserNo operator,
                          String sourceApp, String sourceIp, String tokenHash,
                          FileAccessResult result, String failReason, LocalDateTime occurAt) {
        super(id, operator);
        this.fileId = fileId;
        this.action = action;
        this.usage = usage;
        this.customerNo = customerNo;
        this.productNo = productNo;
        this.operator = operator;
        this.sourceApp = sourceApp;
        this.sourceIp = sourceIp;
        this.tokenHash = tokenHash;
        this.result = result;
        this.failReason = failReason;
        this.occurAt = occurAt;
    }

    // 数据库重建
    public FileAccessLog(FileAccessLogId id, FileId fileId, FileAccessAction action, FileUsage usage,
                         CustomerNo customerNo, ProductNo productNo, UserNo operator,
                         String sourceApp, String sourceIp, String tokenHash,
                         FileAccessResult result, String failReason, LocalDateTime occurAt,
                         UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.fileId = fileId;
        this.action = action;
        this.usage = usage;
        this.customerNo = customerNo;
        this.productNo = productNo;
        this.operator = operator;
        this.sourceApp = sourceApp;
        this.sourceIp = sourceIp;
        this.tokenHash = tokenHash;
        this.result = result;
        this.failReason = failReason;
        this.occurAt = occurAt;
    }

    /**
     * 申请 token 时记录
     */
    public static FileAccessLog apply(FileId fileId, FileUsage usage, FileAccessScope scope,
                                       UserNo operator, String sourceApp, String tokenHash) {
        validateCommon(fileId, usage, scope, operator, tokenHash);
        return new FileAccessLog(
            FileAccessLogId.of(UUID.randomUUID().toString()), fileId, FileAccessAction.APPLY, usage,
            scope.customerNo(), scope.productNo(), operator, sourceApp, null, tokenHash,
            FileAccessResult.SUCCESS, null, LocalDateTime.now()
        );
    }

    /**
     * 实际访问时记录（成功或失败）
     */
    public static FileAccessLog access(FileId fileId, FileUsage usage, FileAccessScope scope,
                                        UserNo operator, String sourceApp, String sourceIp,
                                        String tokenHash, FileAccessResult result, String failReason) {
        validateCommon(fileId, usage, scope, operator, tokenHash);
        if (result == null) throw new IllegalArgumentException("result 不能为空");
        return new FileAccessLog(
            FileAccessLogId.of(UUID.randomUUID().toString()), fileId, FileAccessAction.ACCESS, usage,
            scope.customerNo(), scope.productNo(), operator, sourceApp, sourceIp, tokenHash,
            result, failReason, LocalDateTime.now()
        );
    }

    public static FileAccessLog reconstitute(FileAccessLogId id, FileId fileId, FileAccessAction action, FileUsage usage,
                                             CustomerNo customerNo, ProductNo productNo, UserNo operator,
                                             String sourceApp, String sourceIp, String tokenHash,
                                             FileAccessResult result, String failReason, LocalDateTime occurAt,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new FileAccessLog(id, fileId, action, usage, customerNo, productNo, operator,
            sourceApp, sourceIp, tokenHash, result, failReason, occurAt,
            createdBy, updatedBy, createdAt, updatedAt, version);
    }

    private static void validateCommon(FileId fileId, FileUsage usage, FileAccessScope scope,
                                        UserNo operator, String tokenHash) {
        if (fileId == null) throw new IllegalArgumentException("fileId 不能为空");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
        if (operator == null) throw new IllegalArgumentException("operator 不能为空");
        if (tokenHash == null || tokenHash.isBlank()) throw new IllegalArgumentException("tokenHash 不能为空");
    }

    public void markFail(String failReason) {
        this.result = FileAccessResult.FAIL;
        this.failReason = failReason;
        markUpdated(operator);
    }

    @Override
    protected void validateInvariants() {
        if (action == null) throw new IllegalStateException("action 不能为空");
        if (result == null) throw new IllegalStateException("result 不能为空");
        if (occurAt == null) throw new IllegalStateException("occurAt 不能为空");
    }

    // Getters
    public FileId fileId() { return fileId; }
    public FileAccessAction action() { return action; }
    public FileUsage usage() { return usage; }
    public CustomerNo customerNo() { return customerNo; }
    public ProductNo productNo() { return productNo; }
    public UserNo operator() { return operator; }
    public String sourceApp() { return sourceApp; }
    public String sourceIp() { return sourceIp; }
    public String tokenHash() { return tokenHash; }
    public FileAccessResult result() { return result; }
    public String failReason() { return failReason; }
    public LocalDateTime occurAt() { return occurAt; }
}
