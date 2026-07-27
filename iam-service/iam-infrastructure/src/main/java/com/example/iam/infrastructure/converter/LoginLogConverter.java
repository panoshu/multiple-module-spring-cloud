package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.infrastructure.entity.LoginFailureRecordDO;
import com.example.iam.infrastructure.entity.LoginLogDO;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志聚合根转换器。
 *
 * <p>负责 {@link LoginLog}+子实体 {@link LoginFailureRecord} 与
 * {@link LoginLogDO}+{@link LoginFailureRecordDO} 之间的转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface LoginLogConverter {

    @Mapping(target = "id", expression = "java(log.id() != null ? log.id().value() : null)")
    @Mapping(target = "userId", expression = "java(log.userId())")
    @Mapping(target = "loginName", expression = "java(log.loginName())")
    @Mapping(target = "channelType", expression = "java(log.channelType() != null ? log.channelType().name() : null)")
    @Mapping(target = "success", expression = "java(log.isSuccess())")
    @Mapping(target = "loginTime", expression = "java(log.loginTime())")
    @Mapping(target = "loginIp", expression = "java(log.loginIp())")
    @Mapping(target = "userAgent", expression = "java(log.userAgent())")
    @Mapping(target = "createdBy", expression = "java(log.createdBy() != null ? log.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(log.updatedBy() != null ? log.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(log.createdAt())")
    @Mapping(target = "updateTime", expression = "java(log.updatedAt())")
    @Mapping(target = "version", expression = "java(log.version() != null ? (int) log.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    LoginLogDO toDO(LoginLog log);

    @Mapping(target = "id", expression = "java(record.id() != null ? record.id().value() : null)")
    @Mapping(target = "loginLogId", ignore = true)
    @Mapping(target = "reason", expression = "java(record.reason())")
    @Mapping(target = "detail", expression = "java(record.detail())")
    @Mapping(target = "failureTime", expression = "java(record.failureTime())")
    @Mapping(target = "createdBy", expression = "java(record.createdBy() != null ? record.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(record.updatedBy() != null ? record.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(record.createdAt())")
    @Mapping(target = "updateTime", expression = "java(record.updatedAt())")
    @Mapping(target = "version", expression = "java(record.version() != null ? (int) record.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    LoginFailureRecordDO toRecordDO(LoginFailureRecord record);

    default LoginLog toDomain(LoginLogDO logDO, List<LoginFailureRecordDO> recordDOs) {
        if (logDO == null) {
            return null;
        }
        List<LoginFailureRecord> records = new ArrayList<>();
        if (recordDOs != null) {
            for (LoginFailureRecordDO recordDO : recordDOs) {
                records.add(toRecordDomain(recordDO));
            }
        }
        return LoginLog.reconstitute(
                LoginLogId.of(logDO.getId()),
                logDO.getUserId(),
                logDO.getLoginName(),
                toChannelType(logDO.getChannelType()),
                Boolean.TRUE.equals(logDO.getSuccess()),
                logDO.getLoginTime(),
                logDO.getLoginIp(),
                logDO.getUserAgent(),
                records,
                toUserNo(logDO.getCreatedBy()),
                toUserNo(logDO.getUpdatedBy()),
                logDO.getCreateTime(),
                logDO.getUpdateTime(),
                toVersion(logDO.getVersion())
        );
    }

    default LoginFailureRecord toRecordDomain(LoginFailureRecordDO recordDO) {
        if (recordDO == null) {
            return null;
        }
        return new LoginFailureRecord(
                LoginFailureRecordId.of(recordDO.getId()),
                recordDO.getReason(),
                recordDO.getDetail(),
                recordDO.getFailureTime(),
                toUserNo(recordDO.getCreatedBy()),
                toUserNo(recordDO.getUpdatedBy()),
                recordDO.getCreateTime(),
                recordDO.getUpdateTime(),
                toVersion(recordDO.getVersion())
        );
    }

    @Named("toChannelType")
    default ChannelType toChannelType(String channelType) {
        return channelType != null ? ChannelType.valueOf(channelType) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }
}
