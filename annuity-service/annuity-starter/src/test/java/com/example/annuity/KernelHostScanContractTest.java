package com.example.annuity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K-05 宿主契约测试：annuity 作为 kernel starter 宿主，不得再白盒扫描 kernel 包。
 *
 * <p>回归背景：旧启动类使用
 * {@code @SpringBootApplication(scanBasePackages = {"com.example.annuity", "com.example.core"})}
 * 与 {@code @MapperScan({"com.example.annuity...", "com.example.core.infrastructure.business.mapper"})}，
 * 导致 kernel 内部实现成为宿主的隐式契约。改为依赖 {@code business-core-starter} 后，
 * 这两处扫描必须消失，否则 starter 可以被绕过、kernel 边界重新泄漏。</p>
 */
@DisplayName("K-05 kernel starter 宿主扫描契约")
class KernelHostScanContractTest {

  @Test
  @DisplayName("启动类不得把 com.example.core 放入 scanBasePackages")
  void springBootApplication_doesNotScanKernelPackage() {
    SpringBootApplication annotation =
      AnnuityApplication.class.getAnnotation(SpringBootApplication.class);

    assertThat(annotation).isNotNull();
    assertThat(Arrays.asList(annotation.scanBasePackages()))
      .noneMatch(pkg -> pkg.startsWith("com.example.core"))
      .noneMatch(pkg -> pkg.startsWith("com.example.core.starter"));
  }

  @Test
  @DisplayName("MapperScan 只扫描 annuity 自己的 mapper 包")
  void mapperScan_doesNotScanKernelMapperPackage() {
    MapperScan annotation = AnnuityApplication.class.getAnnotation(MapperScan.class);

    assertThat(annotation).isNotNull();
    assertThat(Arrays.asList(annotation.value()))
      .isNotEmpty()
      .allMatch(pkg -> pkg.startsWith("com.example.annuity"))
      .noneMatch(pkg -> pkg.startsWith("com.example.core"));
  }
}
