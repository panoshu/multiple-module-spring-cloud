package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路由权限规则 DO。
 *
 * <p>对应表 {@code t_iam_route_rule},网关层动态鉴权的配置单元。
 * demo-gateway 启动时加载所有 RouteRule,按 priority 倒序匹配请求路径,
 * 命中后按 checkType 执行对应校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_route_rule")
public class RouteRuleDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 路由匹配模式(Ant 风格,如 /internet/**) */
    private String routePattern;

    /** 校验类型:LOGIN/PERMISSION/ROLE/CHANNEL/SKIP */
    private String checkType;

    /** 校验值(权限码/角色名/渠道名,SKIP 时为空) */
    private String checkValue;

    /** 规则描述 */
    private String description;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级(数值越大优先级越高,匹配时按优先级倒序) */
    private Integer priority;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
