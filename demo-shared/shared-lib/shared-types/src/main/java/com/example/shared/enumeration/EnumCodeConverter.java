package com.example.shared.enumeration;

import java.io.Serializable;

/**
 * 枚举转换工具
 */
public final class EnumCodeConverter {


  private EnumCodeConverter() {
  }


  /**
   * code转换枚举
   */
  public static <E extends Enum<E> & CodeEnum<C>,
    C extends Serializable>
  E fromCode(
    Class<E> enumClass,
    C code
  ) {


    E result =
      EnumCodeCache.get(
        enumClass,
        code
      );


    if (result == null) {

      throw new IllegalArgumentException(
        "Unknown enum code:"
          + code
          + ", enum:"
          + enumClass.getName()
      );

    }


    return result;
  }


  /**
   * 安全转换
   */
  public static <E extends Enum<E> & CodeEnum<C>,
    C extends Serializable>
  E fromCodeOrNull(
    Class<E> enumClass,
    C code
  ) {

    return EnumCodeCache.get(
      enumClass,
      code
    );

  }


}
