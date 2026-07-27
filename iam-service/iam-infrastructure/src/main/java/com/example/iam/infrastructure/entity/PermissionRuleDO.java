package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限规则 DO。
 *
 * <p>对应表 {@code t_iam_permission_rule},授权域的核心配置单元。
 * {@code allowedActions} 以 JSON 数组字符串存储(如 ["HANDLE","QUERY"])。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_permission_rule")
public class PermissionRuleDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 规则编码(全局唯一) */
    private String ruleCode;

    /** 规则名称(展示用) */
    private String ruleName;

    /** 主体维度:CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER */
    private String subjectType;

    /** 主体标识(对应维度的具体值) */
    private String subjectId;

    /** 业务编码(关联 BusinessDefinition) */
    private String businessCode;

    /** 授权动作集合(JSON 数组字符串,如 ["HANDLE","QUERY"]) */
    private String allowedActions;

    /** 是否继承给下属企业(仅 CUSTOMER 级有意义) */
    private Boolean inheritToChildren;

    /** 覆盖模式:ADD 扩展 / REMOVE 收紧 */
    private String overrideMode;

    /** 优先级(可空,空则使用 SubjectType.priority) */
    private Integer priority;

    /** 状态:ACTIVE/DISABLED */
    private String status;

    /** 生效时间 */
    private LocalDateTime effectiveAt;

    /** 失效时间(可空,表示永久) */
    private LocalDateTime expireAt;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
