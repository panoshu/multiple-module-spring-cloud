package com.example.bff.internet.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * BFF 路由配置 DO
 *
 * @author bff
 */
@Data
@Table("t_bff_route_config")
public class BffRouteConfigDO {

    @Id(keyType = KeyType.Generator, value = "snowFlakeId")
    private Long id;

    @Column("business_type")
    private String businessType;

    @Column("service_name")
    private String serviceName;

    @Column("channel_scope")
    private String channelScope;

    @Column("enabled")
    private Boolean enabled;

    @Column("description")
    private String description;

    @Column("created_by")
    private String createdBy;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("updated_by")
    private String updatedBy;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column("deleted")
    private Boolean deleted;

    @Column("version")
    private Integer version;
}
