package com.example.shared.domain.annotation;

import java.lang.annotation.*;

/**
 * 标记领域服务,基础设施层适配不同框架的 IoC 注册
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 12:25
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainService {
  String value() default "";
}
