package com.example.consumer.application.pdf.model;

import com.example.shared.pdf.model.BasePdfContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * BankLetterContext
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 14:32
 */
@Getter
@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class BankLetterContext extends BasePdfContext {
  private String customerName;
  private String accountNo;
  private String riskLevel;

  @Override
  public String getTemplateName() {
    return "bank-letter";
  }
}
