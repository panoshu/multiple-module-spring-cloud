package com.example.shared.enumeration;

import java.io.Serializable;

/**
 * 业务枚举统一规范
 *
 * @param <C> code 类型
 */
public interface CodeEnum<C extends Serializable> {


  /**
   * 获取业务编码
   */
  C getCode();


  /**
   * 获取描述
   */
  default String getDescription() {
    return this.toString();
  }


}
