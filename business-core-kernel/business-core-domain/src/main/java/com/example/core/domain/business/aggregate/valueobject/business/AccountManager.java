package com.example.core.domain.business.aggregate.valueobject.business;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 17:01
 */
public enum AccountManager {
  CJP("20000001"),
  CMB("20000008"),
  BOC("20000009"),
  ICBC("20000010"),
  CCB("20000011"),
  BOCOM("20000012"),
  SPDB("20000013"),
  PAB("20000015"),
  HBT("20000014"),
  ;

  private final String value;

  AccountManager(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
