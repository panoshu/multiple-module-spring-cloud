package com.example.iam.infrastructure.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试专用 ID 生成拦截器。
 *
 * <p>用于在集成测试中为子表 DO(如 BusinessActionDO、PlanDelegationOperatorDO、
 * PlanDelegationPermissionDO)自动生成 Long 类型的 ID。
 *
 * <p>背景:项目源码中子表 DO 的 {@code @Id(keyType = KeyType.None)} 且 Converter
 * 对 id 字段标注 {@code ignore = true},正式环境通过应用层 IdService 生成 ID。
 * 但集成测试仅加载 infrastructure 层,IdService 不在测试上下文中,导致 insert 时
 * id 为 NULL,违反 NOT NULL 约束。
 *
 * <p>本拦截器仅在测试环境生效,通过反射检测 DO 的 {@code id} 字段,若为 NULL
 * 则分配一个递增的唯一 Long 值,避免修改任何生产源码。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class TestIdGenerationInterceptor implements Interceptor {

    /** ID 起始值,避开测试用例中显式指定的 ID 范围(如 10001、80001 等)。 */
    private static final AtomicLong ID_SEQUENCE = new AtomicLong(900_000L);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object parameter = invocation.getArgs()[1];
        if (parameter != null) {
            fillIdIfNull(parameter);
        }
        return invocation.proceed();
    }

    /**
     * 通过反射检查目标对象的 {@code id} 字段,若为 NULL 则填充递增 ID。
     *
     * <p>处理三种参数形态:
     * <ul>
     *   <li>直接 DO 对象:直接反射设置 id</li>
     *   <li>MyBatis {@code MapperMethod.ParamMap}:遍历 value 集合,对每个 DO 填充 id</li>
     *   <li>{@code List}:批量插入场景,遍历每个元素</li>
     * </ul>
     */
    private void fillIdIfNull(Object target) {
        if (target instanceof java.util.Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value != null && !value.getClass().getName().startsWith("java.")) {
                    fillIdIfNull(value);
                }
            }
            return;
        }
        if (target instanceof java.util.Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) {
                    fillIdIfNull(element);
                }
            }
            return;
        }
        fillIdForSimpleObject(target);
    }

    /**
     * 对简单 DO 对象填充 id(若 id 字段为 NULL)。
     */
    private void fillIdForSimpleObject(Object target) {
        Class<?> clazz = target.getClass();
        Field idField = findIdField(clazz);
        if (idField == null) {
            return;
        }
        if (idField.getType() != Long.class && idField.getType() != long.class) {
            return;
        }
        try {
            idField.setAccessible(true);
            Object currentValue = idField.get(target);
            if (currentValue == null) {
                Long newId = ID_SEQUENCE.incrementAndGet();
                idField.set(target, newId);
                log.debug("测试拦截器生成 ID: class={}, id={}", clazz.getSimpleName(), newId);
            }
        } catch (IllegalAccessException e) {
            log.debug("无法访问 id 字段: {}", clazz.getName());
        }
    }

    /**
     * 查找类(或父类)中声明的 {@code id} 字段。
     */
    private Field findIdField(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
